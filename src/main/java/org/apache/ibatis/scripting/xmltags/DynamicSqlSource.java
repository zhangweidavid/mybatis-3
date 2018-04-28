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
package org.apache.ibatis.scripting.xmltags;

import java.util.Map;

import org.apache.ibatis.builder.SqlSourceBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.session.Configuration;

/**
 * 动态SqlSource
 */
public class DynamicSqlSource implements SqlSource {

  //配置数据
  private final Configuration configuration;
  //动态SQL根节点
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    //对当前请求参数构建动态上下文
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    //动态SQL处理
    rootSqlNode.apply(context);
    //创建SqlSource构造器
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    //获取参数类型
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
     //创建SqlSource
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    //获取BoundSql
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    //将绑定数据添加到boundSql扩展参数中
    for (Map.Entry<String, Object> entry : context.getBindings().entrySet()) {
      boundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
    }
    return boundSql;
  }

}
