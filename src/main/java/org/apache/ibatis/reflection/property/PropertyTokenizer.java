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
package org.apache.ibatis.reflection.property;

import java.util.Iterator;

/**
 * 属性标记生成器
 * @author Clinton Begin
 */
public class PropertyTokenizer implements Iterator<PropertyTokenizer> {
  //属性
  private String name;
  //索引化的属性名称，如 list[10],如果当前属性没有编入索引则和name相同
  private final String indexedName;
  //索引
  private String index;
  //子属性
  private final String children;


  public PropertyTokenizer(String fullname) {
    //从属性fullname中查找分隔符"."
    int delim = fullname.indexOf('.');
    //如果存在分隔符，则将属性通过分割符分割为两部分
    if (delim > -1) {
      //一级属性
      name = fullname.substring(0, delim);
      //子属性
      children = fullname.substring(delim + 1);
    } else {
      //如果没有分割符则表示没有子属性，则将children设置为null
      name = fullname;
      children = null;
    }
    //将属性名称赋值给indexedName
    indexedName = name;
    //从当前属性中查找分隔符；如果list[10]
    delim = name.indexOf('[');
    //如果存在属性则
    if (delim > -1) {
      //找到索引，如list[10] 这样的属性可以得到index=10
      index = name.substring(delim + 1, name.length() - 1);
      //得到正确的属性名称
      name = name.substring(0, delim);
    }
  }

  public String getName() {
    return name;
  }

  public String getIndex() {
    return index;
  }

  public String getIndexedName() {
    return indexedName;
  }

  public String getChildren() {
    return children;
  }

  @Override
  public boolean hasNext() {
    return children != null;
  }

  @Override
  public PropertyTokenizer next() {
    return new PropertyTokenizer(children);
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("Remove is not supported, as it has no meaning in the context of properties.");
  }
}
