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
package org.apache.ibatis.executor.loader.javassist;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;

import org.apache.ibatis.executor.ExecutorException;
import org.apache.ibatis.executor.loader.AbstractEnhancedDeserializationProxy;
import org.apache.ibatis.executor.loader.AbstractSerialStateHolder;
import org.apache.ibatis.executor.loader.ResultLoaderMap;
import org.apache.ibatis.executor.loader.WriteReplaceInterface;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.logging.Log;
import org.apache.ibatis.logging.LogFactory;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.reflection.property.PropertyCopier;
import org.apache.ibatis.reflection.property.PropertyNamer;
import org.apache.ibatis.session.Configuration;

/**
 * @author Eduardo Macarron
 */
public class JavassistProxyFactory implements org.apache.ibatis.executor.loader.ProxyFactory {

  private static final Log log = LogFactory.getLog(JavassistProxyFactory.class);
  private static final String FINALIZE_METHOD = "finalize";
  private static final String WRITE_REPLACE_METHOD = "writeReplace";

  public JavassistProxyFactory() {
    //javassit 检查
    try {
      Resources.classForName("javassist.util.proxy.ProxyFactory");
    } catch (Throwable e) {
      throw new IllegalStateException("Cannot enable lazy loading because Javassist is not available. Add Javassist to your classpath.", e);
    }
  }

  @Override
  public Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedResultObjectProxyImpl.createProxy(target, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
  }

  public Object createDeserializationProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
    return EnhancedDeserializationProxyImpl.createProxy(target, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
  }

  @Override
  public void setProperties(Properties properties) {
      // Not Implemented
  }

  static Object crateProxy(Class<?> type, MethodHandler callback, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {

    ProxyFactory enhancer = new ProxyFactory();
    enhancer.setSuperclass(type);

    try {
      //获取writeReplace方法
      type.getDeclaredMethod(WRITE_REPLACE_METHOD);
      //如果一个对象有writeReplace则实际序列化对象是writeReplace的返回值
      if (log.isDebugEnabled()) {
        log.debug(WRITE_REPLACE_METHOD + " method was found on bean " + type + ", make sure it returns this");
      }
    } catch (NoSuchMethodException e) {//如果不存在则设置enhancer设置WriteReplaceInterface接口，为什么要实现该接口呢？可以通过后续代码寻找答案
      enhancer.setInterfaces(new Class[]{WriteReplaceInterface.class});
    } catch (SecurityException e) {
      // nothing to do here
    }

    Object enhanced;
    Class<?>[] typesArray = constructorArgTypes.toArray(new Class[constructorArgTypes.size()]);
    Object[] valuesArray = constructorArgs.toArray(new Object[constructorArgs.size()]);
    //创建了代理对象
    try {
      enhanced = enhancer.create(typesArray, valuesArray);
    } catch (Exception e) {
      throw new ExecutorException("Error creating lazy proxy.  Cause: " + e, e);
    }
    //设置代理对象的增强
    ((Proxy) enhanced).setHandler(callback);
    //返回代理对象
    return enhanced;
  }

  /**
   * 增强结果对象代理实现
   */
  private static class EnhancedResultObjectProxyImpl implements MethodHandler {

    private final Class<?> type;
    //懒加载映射表
    private final ResultLoaderMap lazyLoader;
    private final boolean aggressive;
    //懒加载触发方法
    private final Set<String> lazyLoadTriggerMethods;
    private final ObjectFactory objectFactory;
    private final List<Class<?>> constructorArgTypes;
    private final List<Object> constructorArgs;

    private EnhancedResultObjectProxyImpl(Class<?> type, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      //class
      this.type = type;
      //懒加载
      this.lazyLoader = lazyLoader;
      //是否积极模式
      this.aggressive = configuration.isAggressiveLazyLoading();
      //懒加载触发方法
      this.lazyLoadTriggerMethods = configuration.getLazyLoadTriggerMethods();
      //对象工厂
      this.objectFactory = objectFactory;
      this.constructorArgTypes = constructorArgTypes;
      this.constructorArgs = constructorArgs;
    }

    /**
     *
     * @param target 目标对象
     * @param lazyLoader 懒加载
     * @param configuration
     * @param objectFactory
     * @param constructorArgTypes
     * @param constructorArgs
     * @return
     */
    public static Object createProxy(Object target, ResultLoaderMap lazyLoader, Configuration configuration, ObjectFactory objectFactory, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      //获取目标对象类
      final Class<?> type = target.getClass();
      EnhancedResultObjectProxyImpl callback = new EnhancedResultObjectProxyImpl(type, lazyLoader, configuration, objectFactory, constructorArgTypes, constructorArgs);
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      //复制属性
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    @Override
    public Object invoke(Object enhanced, Method method, Method methodProxy, Object[] args) throws Throwable {
      final String methodName = method.getName();
      try {
        synchronized (lazyLoader) {
          //如果当前调用是writeReplace 则表示需要对象当前对象进行序列化，而当前对象是一个代理对象，直接对当前对象序列化显然是不合理的
          //在上面的代码可以发现代理对对象都存在writeReplace方法，在序列化的时候会调用该方法，在对该方法进行增强处理返回原始对对象，如果需要延迟加载
          //则返回了一个序列化状态保持对象
          if (WRITE_REPLACE_METHOD.equals(methodName)) {
            Object original;
            //创建一个新的原始对象
            if (constructorArgTypes.isEmpty()) {
              original = objectFactory.create(type);
            } else {
              original = objectFactory.create(type, constructorArgTypes, constructorArgs);
            }
            //复制属性
            PropertyCopier.copyBeanProperties(type, enhanced, original);
            //如果存在延迟加载对象则创建一个代理对象
            if (lazyLoader.size() > 0) {
              return new JavassistSerialStateHolder(original, lazyLoader.getProperties(), objectFactory, constructorArgTypes, constructorArgs);
            } else {//如果没有延迟加载对象
              return original;
            }
          } else {// 如果不是writeReplace方法
            //如果懒加载对象大于0且当前不是finalize方法
            if (lazyLoader.size() > 0 && !FINALIZE_METHOD.equals(methodName)) {
              //如果是积极的懒加载模式或者调用列触发加载全部方法则加载全部懒加载属性
              if (aggressive || lazyLoadTriggerMethods.contains(methodName)) {
                lazyLoader.loadAll();
              } else if (PropertyNamer.isSetter(methodName)) {
                //如果setter的属性是懒加载的则从懒加载映射表中删除
                final String property = PropertyNamer.methodToProperty(methodName);
                lazyLoader.remove(property);
              } else if (PropertyNamer.isGetter(methodName)) {
                //如果getter的属性是懒加载的此时需要加载
                final String property = PropertyNamer.methodToProperty(methodName);
                if (lazyLoader.hasLoader(property)) {
                  lazyLoader.load(property);
                }
              }
            }
          }
        }
        return methodProxy.invoke(enhanced, args);
      } catch (Throwable t) {
        throw ExceptionUtil.unwrapThrowable(t);
      }
    }
  }

  /**
   * 反序列化增强
   */
  private static class EnhancedDeserializationProxyImpl extends AbstractEnhancedDeserializationProxy implements MethodHandler {

    private EnhancedDeserializationProxyImpl(Class<?> type, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      super(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }

    public static Object createProxy(Object target, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      final Class<?> type = target.getClass();
      EnhancedDeserializationProxyImpl callback = new EnhancedDeserializationProxyImpl(type, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
      Object enhanced = crateProxy(type, callback, constructorArgTypes, constructorArgs);
      PropertyCopier.copyBeanProperties(type, target, enhanced);
      return enhanced;
    }

    @Override
    public Object invoke(Object enhanced, Method method, Method methodProxy, Object[] args) throws Throwable {
      final Object o = super.invoke(enhanced, method, args);
      return o instanceof AbstractSerialStateHolder ? o : methodProxy.invoke(o, args);
    }

    @Override
    protected AbstractSerialStateHolder newSerialStateHolder(Object userBean, Map<String, ResultLoaderMap.LoadPair> unloadedProperties, ObjectFactory objectFactory,
            List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
      return new JavassistSerialStateHolder(userBean, unloadedProperties, objectFactory, constructorArgTypes, constructorArgs);
    }
  }
}
