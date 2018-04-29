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

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.binding.BindingException;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * jdbc3KeyGenerator没有实现前置处理，因为该该主要是用于取回数据库自增的主键
 */
public class Jdbc3KeyGenerator implements KeyGenerator {

  /**
   * A shared instance.
   * @since 3.4.3
   */
  public static final Jdbc3KeyGenerator INSTANCE = new Jdbc3KeyGenerator();

  @Override
  public void processBefore(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    // do nothing
  }

  @Override
  public void processAfter(Executor executor, MappedStatement ms, Statement stmt, Object parameter) {
    processBatch(ms, stmt, getParameters(parameter));
  }

  public void processBatch(MappedStatement ms, Statement stmt, Collection<Object> parameters) {
    ResultSet rs = null;
    try {
      //获取数据库自增的主键，如果没有生成主键则返回的结果集为空
      rs = stmt.getGeneratedKeys();
      //获取配置数据
      final Configuration configuration = ms.getConfiguration();
      //获取类型处理器的注册机
      final TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();
      //获取主键属性
      final String[] keyProperties = ms.getKeyProperties();
      //获取结果集中的元数据
      final ResultSetMetaData rsmd = rs.getMetaData();
      TypeHandler<?>[] typeHandlers = null;
      //如果指定了主键属性且返回的结果集中的列数大于或等于主键属性列的个数
      if (keyProperties != null && rsmd.getColumnCount() >= keyProperties.length) {
        //遍历参数
        for (Object parameter : parameters) {
          // there should be one row for each statement (also one for each parameter)
          if (!rs.next()) {
            break;
          }
          //获取参数的元数据
          final MetaObject metaParam = configuration.newMetaObject(parameter);
          //获取属性的类型处理器
          if (typeHandlers == null) {
            typeHandlers = getTypeHandlers(typeHandlerRegistry, metaParam, keyProperties, rsmd);
          }
          //生成主键属性值
          populateKeys(rs, metaParam, keyProperties, typeHandlers);
        }
      }
    } catch (Exception e) {
      throw new ExecutorException("Error getting generated key or setting result to parameter object. Cause: " + e, e);
    } finally {
      if (rs != null) {
        try {
          rs.close();
        } catch (Exception e) {
          // ignore
        }
      }
    }
  }

  private Collection<Object> getParameters(Object parameter) {
    Collection<Object> parameters = null;
    //如果属性是集合则返回集合
    if (parameter instanceof Collection) {
      parameters = (Collection) parameter;
    } else if (parameter instanceof Map) {//参数对象是Map
      Map parameterMap = (Map) parameter;
      if (parameterMap.containsKey("collection")) {//如果map中有collection key
        parameters = (Collection) parameterMap.get("collection");
      } else if (parameterMap.containsKey("list")) {//如果map中有list key
        parameters = (List) parameterMap.get("list");
      } else if (parameterMap.containsKey("array")) {//如果map中有array key
        parameters = Arrays.asList((Object[]) parameterMap.get("array"));
      }
    }
    //既不是集合也不是map,则创建一个List将参数对象添加到集合中
    if (parameters == null) {
      parameters = new ArrayList<Object>();
      parameters.add(parameter);
    }
    return parameters;
  }

  private TypeHandler<?>[] getTypeHandlers(TypeHandlerRegistry typeHandlerRegistry, MetaObject metaParam, String[] keyProperties, ResultSetMetaData rsmd) throws SQLException {
    TypeHandler<?>[] typeHandlers = new TypeHandler<?>[keyProperties.length];
    //遍历所有主键列属性
    for (int i = 0; i < keyProperties.length; i++) {
      //如果参数对象中有主键列的setter方法
      if (metaParam.hasSetter(keyProperties[i])) {
        TypeHandler<?> th;
        try {
          //获取setter参数类型
          Class<?> keyPropertyType = metaParam.getSetterType(keyProperties[i]);
          //获取参数类型的处理器
          th = typeHandlerRegistry.getTypeHandler(keyPropertyType, JdbcType.forCode(rsmd.getColumnType(i + 1)));
        } catch (BindingException e) {
          th = null;
        }
        typeHandlers[i] = th;
      }
    }
    return typeHandlers;
  }

  private void populateKeys(ResultSet rs, MetaObject metaParam, String[] keyProperties, TypeHandler<?>[] typeHandlers) throws SQLException {
    //遍历所有key属性
    for (int i = 0; i < keyProperties.length; i++) {
      //key属性
      String property = keyProperties[i];
      //获取类型处理器
      TypeHandler<?> th = typeHandlers[i];
      if (th != null) {
        //获取主键值
        Object value = th.getResult(rs, i + 1);
        //会写到参数对象中
        metaParam.setValue(property, value);
      }
    }
  }

}
