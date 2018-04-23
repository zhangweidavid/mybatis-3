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
package org.apache.ibatis.executor.keygen;

import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.RowBounds;

/**
 * @author Clinton Begin
 * @author Jeff Butler
 */
public class SelectKeyGenerator implements KeyGenerator {
  
  public static final String SELECT_KEY_SUFFIX = "!selectKey";
  private final boolean executeBefore;
  private final MappedStatement keyStatement;

  public SelectKeyGenerator(MappedStatement keyStatement, boolean executeBefore) {
    this.executeBefore = executeBefore;
    this.keyStatement = keyStatement;
  }

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    //是否执行前执行，如果不是就不执行
    if (executeBefore) {
      processGeneratedKeys(executor, ms, parameter);
    }
  }

  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    //如果executeBefore配置为false则执行
    if (!executeBefore) {
      processGeneratedKeys(executor, ms, parameter);
    }
  }

  private void processGeneratedKeys(Executor executor, MappedStatement ms, Object parameter) {
    try {
      //如果parameter不为null同时keyStatement不为null且keyStatement 指定了keyProperties
      if (parameter != null && keyStatement != null && keyStatement.getKeyProperties() != null) {
        //获取keyProperties
        String[] keyProperties = keyStatement.getKeyProperties();
        //获取配置信息
        final Configuration configuration = ms.getConfiguration();
        //获取参数对象元数据
        final MetaObject metaParam = configuration.newMetaObject(parameter);
        //其实已经判断过了
        if (keyProperties != null) {
          //新建keyExecutor
          Executor keyExecutor = configuration.newExecutor(executor.getTransaction(), ExecutorType.SIMPLE);
          //执行查询
          List<Object> values = keyExecutor.query(keyStatement, parameter, RowBounds.DEFAULT, Executor.NO_RESULT_HANDLER);
          //如果查询结果为0个则抛出异常
          if (values.size() == 0) {
            throw new ExecutorException("SelectKey returned no data.");            
          } else if (values.size() > 1) {//查询的结果个数多余1个则抛出异常
            throw new ExecutorException("SelectKey returned more than one value.");
          } else {//只返了一个结果值
            MetaObject metaResult = configuration.newMetaObject(values.get(0));
            //如果keyProperty个数只有1个
            if (keyProperties.length == 1) {
              //如果查询结果对象存在这个属性的getter方法
              if (metaResult.hasGetter(keyProperties[0])) {
                //将属性值设置到param中
                setValue(metaParam, keyProperties[0], metaResult.getValue(keyProperties[0]));
              } else {
                //如果没有getter方法就将当前值设置到属性中
                setValue(metaParam, keyProperties[0], values.get(0));
              }
            } else {//处理指定多个key属性场景
              handleMultipleProperties(keyProperties, metaParam, metaResult);
            }
          }
        }
      }
    } catch (ExecutorException e) {
      throw e;
    } catch (Exception e) {
      throw new ExecutorException("Error selecting key or setting result to parameter object. Cause: " + e, e);
    }
  }

  private void handleMultipleProperties(String[] keyProperties,
      MetaObject metaParam, MetaObject metaResult) {
    //获取所有key  column
    String[] keyColumns = keyStatement.getKeyColumns();
      //如果key column不存在
    if (keyColumns == null || keyColumns.length == 0) {
      //没有指定key column则直接使用配置到 key property
      for (String keyProperty : keyProperties) {
        setValue(metaParam, keyProperty, metaResult.getValue(keyProperty));
      }
    } else {
      //存在key column 但是数量不一致
      if (keyColumns.length != keyProperties.length) {
        throw new ExecutorException("If SelectKey has key columns, the number must match the number of key properties.");
      }
      //数量一致，要求keyColumn 和keyProperty一一对应
      for (int i = 0; i < keyProperties.length; i++) {
        setValue(metaParam, keyProperties[i], metaResult.getValue(keyColumns[i]));
      }
    }
  }

  private void setValue(MetaObject metaParam, String property, Object value) {
    if (metaParam.hasSetter(property)) {
      metaParam.setValue(property, value);
    } else {
      throw new ExecutorException("No setter found for the keyProperty '" + property + "' in " + metaParam.getOriginalObject().getClass().getName() + ".");
    }
  }
}
