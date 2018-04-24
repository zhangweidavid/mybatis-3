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
package org.apache.ibatis.plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.reflection.ExceptionUtil;

/**
 * 插件辅助工具类，通过该类对需要拦截的方法进行动态增强
 */
public class Plugin implements InvocationHandler {
  //目标对象
  private final Object target;
  //拦截器
  private final Interceptor interceptor;
  private final Map<Class<?>, Set<Method>> signatureMap;

  private Plugin(Object target, Interceptor interceptor, Map<Class<?>, Set<Method>> signatureMap) {
    this.target = target;
    this.interceptor = interceptor;
    this.signatureMap = signatureMap;
  }

  //对目标对象使用过interceptor进行增强
  public static Object wrap(Object target, Interceptor interceptor) {
    //获取拦截类，方法映射表
    Map<Class<?>, Set<Method>> signatureMap = getSignatureMap(interceptor);
    //获取目标类类型
    Class<?> type = target.getClass();
    //获取目标类所有需要拦截到接口
    Class<?>[] interfaces = getAllInterfaces(type, signatureMap);
    //如果有拦截接口，则创建代理对象对其进行增强
    if (interfaces.length > 0) {
      //创建动态代理对象
      return Proxy.newProxyInstance(
          type.getClassLoader(),
          interfaces,
          new Plugin(target, interceptor, signatureMap));
    }
    return target;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      Set<Method> methods = signatureMap.get(method.getDeclaringClass());
      //如果当前方法需要被增强
      if (methods != null && methods.contains(method)) {
        //调用目标对象对拦截器
        return interceptor.intercept(new Invocation(target, method, args));
      }
      //否则直接调用方法
      return method.invoke(target, args);
    } catch (Exception e) {
      throw ExceptionUtil.unwrapThrowable(e);
    }
  }

  private static Map<Class<?>, Set<Method>> getSignatureMap(Interceptor interceptor) {
    //获取类上@Intercepts的注解
    Intercepts interceptsAnnotation = interceptor.getClass().getAnnotation(Intercepts.class);
    // 如果插件上没有@Intercepts注解，抛出异常
    if (interceptsAnnotation == null) {
      throw new PluginException("No @Intercepts annotation was found in interceptor " + interceptor.getClass().getName());      
    }
    //@Intercepts注解中所有签名
    Signature[] sigs = interceptsAnnotation.value();
    Map<Class<?>, Set<Method>> signatureMap = new HashMap<Class<?>, Set<Method>>();
    //遍历所有签名
    for (Signature sig : sigs) {
      //根据类型从签名映射表中获取方法集合
      Set<Method> methods = signatureMap.get(sig.type());
      if (methods == null) {
        //如果方法集合为null,则创建一个空集合并放入到映射表中
        methods = new HashSet<Method>();
        signatureMap.put(sig.type(), methods);
      }
      try {
        //根据方法名称，参数类型列表从指定的class中获取方法
        Method method = sig.type().getMethod(sig.method(), sig.args());
        //如果找到指定的方法则添加到集合中
        methods.add(method);
      } catch (NoSuchMethodException e) {
        throw new PluginException("Could not find method on " + sig.type() + " named " + sig.method() + ". Cause: " + e, e);
      }
    }
    return signatureMap;
  }

  private static Class<?>[] getAllInterfaces(Class<?> type, Map<Class<?>, Set<Method>> signatureMap) {
    Set<Class<?>> interfaces = new HashSet<Class<?>>();
    while (type != null) {
      //获取当前类的所有接口
      for (Class<?> c : type.getInterfaces()) {
        //如果当前接口需要拦截，则将该接口放入到接口集合中
        if (signatureMap.containsKey(c)) {
          interfaces.add(c);
        }
      }
      //向父类找符合条件到集合
      type = type.getSuperclass();
    }
    //返回数组
    return interfaces.toArray(new Class<?>[interfaces.size()]);
  }

}
