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
  private String name;
  private final String indexedName;
  private String index;
  private final String children;


  public PropertyTokenizer(String fullname) {
    //从属性名称中找到.位置
    int delim = fullname.indexOf('.');
    //如果存在.
    if (delim > -1) {
      //找到父属性
      name = fullname.substring(0, delim);
      //找到子属性
      children = fullname.substring(delim + 1);
    } else {
      //如果不存在.,则表示当前属性没有子属性
      name = fullname;
      children = null;
    }
    //带有索引的名称
    indexedName = name;
    //从name中查找 '['位置
    delim = name.indexOf('[');
    //如果存在属性则
    if (delim > -1) {
      //找到所以
      index = name.substring(delim + 1, name.length() - 1);
      //去除索引后的名称
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
