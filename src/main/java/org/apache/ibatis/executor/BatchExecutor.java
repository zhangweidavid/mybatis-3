/**
 *    Copyright 2009-2017 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor;

import java.sql.BatchUpdateException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.transaction.Transaction;

/**
 * @author Jeff Butler 
 */
public class BatchExecutor extends BaseExecutor {

  public static final int BATCH_UPDATE_RETURN_VALUE = Integer.MIN_VALUE + 1002;

  private final List<Statement> statementList = new ArrayList<Statement>();
  //批量执行结果
  private final List<BatchResult> batchResultList = new ArrayList<BatchResult>();
  //最近一次创建的sql
  private String currentSql;
  //最近一次添加批处理的MappedSatement
  private MappedStatement currentStatement;

  public BatchExecutor(Configuration configuration, Transaction transaction) {
    super(configuration, transaction);
  }

  @Override
  public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
    //获取配置信息
    final Configuration configuration = ms.getConfiguration();
    //创建StatementHandler
    final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
    //获取BoundSql
    final BoundSql boundSql = handler.getBoundSql();
    //从boundSql中获取 sql
    final String sql = boundSql.getSql();
    final Statement stmt;
    //如果sql等于currentSql同时MappedStatement与currentStatement相同， 就是同一条SQL，但是参数可能不同，这样就不需要重复创建PrepareStatement
    //可以减少网络交互次次数，通过源码可以发现批处理中最佳时间就是同样的sql要一起执行，不要存在不同sql间隔这样的场景出现
    if (sql.equals(currentSql) && ms.equals(currentStatement)) {
      int last = statementList.size() - 1;
      //获取最后一次创建statement
      stmt = statementList.get(last);
      //设置事务超时时间
      applyTransactionTimeout(stmt);
      //设置stmt参数
      handler.parameterize(stmt);
      //获取对应的批量结果
      BatchResult batchResult = batchResultList.get(last);
      //将参数对象添加到参数列表中
      batchResult.addParameterObject(parameterObject);
    } else {//和上一次创建的SQL不同，则需要重新创建PrepareStatement
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);
      currentSql = sql;
      currentStatement = ms;
      statementList.add(stmt);
      batchResultList.add(new BatchResult(ms, sql, parameterObject));
    }
    //添加到批处理
    handler.batch(stmt);
    //返回默认值
    return BATCH_UPDATE_RETURN_VALUE;
  }

  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException {
    Statement stmt = null;
    try {
      //刷新
      flushStatements();
      //获取配置
      Configuration configuration = ms.getConfiguration();
      //创建StatementHandler,该处可以通过插件增强
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
      //获取连接
      Connection connection = getConnection(ms.getStatementLog());
      //预处理
      stmt = handler.prepare(connection, transaction.getTimeout());
      //设置参数
      handler.parameterize(stmt);
      //执行了SQL 也就是说查询是没有批处理的
      return handler.<E>query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    flushStatements();
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Connection connection = getConnection(ms.getStatementLog());
    Statement stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    return handler.<E>queryCursor(stmt);
  }

  @Override
  public List<BatchResult> doFlushStatements(boolean isRollback) throws SQLException {
    try {
      List<BatchResult> results = new ArrayList<BatchResult>();
      if (isRollback) {
        return Collections.emptyList();
      }
      //遍历所有satement
      for (int i = 0, n = statementList.size(); i < n; i++) {
        Statement stmt = statementList.get(i);
        applyTransactionTimeout(stmt);
        //获取对应的结果对象
        BatchResult batchResult = batchResultList.get(i);
        try {
          //stmt.executeBatch执行批处理，并将更新条数保存到执行结果中;
          batchResult.setUpdateCounts(stmt.executeBatch());
          //获取结果对应到mappedStatement
          MappedStatement ms = batchResult.getMappedStatement();
          //获取参数列表
          List<Object> parameterObjects = batchResult.getParameterObjects();
          //获取key生成器
          KeyGenerator keyGenerator = ms.getKeyGenerator();
          if (Jdbc3KeyGenerator.class.equals(keyGenerator.getClass())) {
            Jdbc3KeyGenerator jdbc3KeyGenerator = (Jdbc3KeyGenerator) keyGenerator;
            jdbc3KeyGenerator.processBatch(ms, stmt, parameterObjects);
          } else if (!NoKeyGenerator.class.equals(keyGenerator.getClass())) { //不是NoKeyGenerator，执行keyGenerator后置处理
            for (Object parameter : parameterObjects) {//遍历所有参数对象
              keyGenerator.processAfter(this, ms, stmt, parameter);
            }
          }
          //关闭statement
          closeStatement(stmt);
        } catch (BatchUpdateException e) {
          StringBuilder message = new StringBuilder();
          message.append(batchResult.getMappedStatement().getId())
              .append(" (batch index #")
              .append(i + 1)
              .append(")")
              .append(" failed.");
          if (i > 0) {
            message.append(" ")
                .append(i)
                .append(" prior sub executor(s) completed successfully, but will be rolled back.");
          }
          throw new BatchExecutorException(message.toString(), e, results, batchResult);
        }
        results.add(batchResult);
      }
      return results;
    } finally {
      for (Statement stmt : statementList) {
        closeStatement(stmt);
      }
      currentSql = null;
      statementList.clear();
      batchResultList.clear();
    }
  }

}
