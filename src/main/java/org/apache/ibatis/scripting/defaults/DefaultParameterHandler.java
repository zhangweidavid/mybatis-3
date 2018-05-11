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
package org.apache.ibatis.scripting.defaults;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeException;
import org.apache.ibatis.type.TypeHandler;
import org.apache.ibatis.type.TypeHandlerRegistry;

/**
 * @author Clinton Begin
 * @author Eduardo Macarron
 */
public class DefaultParameterHandler implements ParameterHandler {

  private static final Log log= LogFactory.getLog(DefaultParameterHandler.class);
  //类型处理器注册表
  private final TypeHandlerRegistry typeHandlerRegistry;

  private final MappedStatement mappedStatement;
  //参数对象
  private final Object parameterObject;
  //boundSql
  private final BoundSql boundSql;
  //全局配置信息
  private final Configuration configuration;

  public DefaultParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    this.mappedStatement = mappedStatement;
    this.configuration = mappedStatement.getConfiguration();
    this.typeHandlerRegistry = mappedStatement.getConfiguration().getTypeHandlerRegistry();
    this.parameterObject = parameterObject;
    this.boundSql = boundSql;
  }

  @Override
  public Object getParameterObject() {
    return parameterObject;
  }

  @Override
  public void setParameters(PreparedStatement ps) {
    log.trace(" setParameters  ps="+ps+", parameterObject="+parameterObject);
    ErrorContext.instance().activity("setting parameters").object(mappedStatement.getParameterMap().getId());
    //获取参数映射关系
    List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();
    //如果参数映射关系不为null
    if (parameterMappings != null) {
      //遍历所有参数映射
      for (int i = 0; i < parameterMappings.size(); i++) {
        //获取参数映射对象
        ParameterMapping parameterMapping = parameterMappings.get(i);
        //如果参数映射参数类型不是OUT
        if (parameterMapping.getMode() != ParameterMode.OUT) {
          Object value;
          //获取参数名称
          String propertyName = parameterMapping.getProperty();
          //如果扩展参数存在存在该存在，则从扩展参数中获取属性值
          if (boundSql.hasAdditionalParameter(propertyName)) {
            value = boundSql.getAdditionalParameter(propertyName);
          } else if (parameterObject == null) {//如果扩展参数中没有，且参数对象为null则值为null
            value = null;
          } else if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {//如果当前参数对象类型有类型处理器值值就是当前对象
            value = parameterObject;
          } else {//不存在扩展参数也不为null而且没有类型处理器则通过MetaObject方式获取属性值
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            value = metaObject.getValue(propertyName);
          }
          //获取参数类型处理器
          TypeHandler typeHandler = parameterMapping.getTypeHandler();
          //获取参数类型
          JdbcType jdbcType = parameterMapping.getJdbcType();
          //如果参数值和类型都为null,则使用配置的jdbcTypeForNull
          if (value == null && jdbcType == null) {
            jdbcType = configuration.getJdbcTypeForNull();
          }
          //设置参数值
          try {
            typeHandler.setParameter(ps, i + 1, value, jdbcType);
          } catch (TypeException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          } catch (SQLException e) {
            throw new TypeException("Could not set parameters for mapping: " + parameterMapping + ". Cause: " + e, e);
          }
        }
      }
    }
  }

}
