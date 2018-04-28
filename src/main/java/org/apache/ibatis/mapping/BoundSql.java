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
package org.apache.ibatis.mapping;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.property.PropertyTokenizer;
import org.apache.ibatis.session.Configuration;


public class BoundSql {
  //真实的sql语句，可能存在占位符
  private final String sql;
  //参数映射关系
  private final List<ParameterMapping> parameterMappings;
  //参数对象
  private final Object parameterObject;
  //附加参数
  private final Map<String, Object> additionalParameters;
  //附加参数元数据
  private final MetaObject metaParameters;

  public BoundSql(Configuration configuration, String sql, List<ParameterMapping> parameterMappings, Object parameterObject) {
    this.sql = sql;
    this.parameterMappings = parameterMappings;
    this.parameterObject = parameterObject;
    //初始化附加参数对象
    this.additionalParameters = new HashMap<String, Object>();
    //创建HashMap的元数据
    this.metaParameters = configuration.newMetaObject(additionalParameters);
  }

  //获取SQL
  public String getSql() {
    return sql;
  }

  //获取参数映射关系
  public List<ParameterMapping> getParameterMappings() {
    return parameterMappings;
  }

  //获取参数对象
  public Object getParameterObject() {
    return parameterObject;
  }

  //判断附加参数中是否存在了该属性名对应的参数
  public boolean hasAdditionalParameter(String name) {
    String paramName = new PropertyTokenizer(name).getName();
    return additionalParameters.containsKey(paramName);
  }

  //添加指定名称和值的附加参数
  public void setAdditionalParameter(String name, Object value) {
    metaParameters.setValue(name, value);
  }
  //根据名称获取附加参数值
  public Object getAdditionalParameter(String name) {
    return metaParameters.getValue(name);
  }
}
