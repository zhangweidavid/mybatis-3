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
package org.apache.ibatis.reflection;

/**
 * 反射工厂接口
 */
public interface ReflectorFactory {

  //类反射对象是否可以缓存
  boolean isClassCacheEnabled();

  //设置类与反射的映射关系是否可以缓存
  void setClassCacheEnabled(boolean classCacheEnabled);

  //获取当前类型的反射对象
  Reflector findForClass(Class<?> type);
}