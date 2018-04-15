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

import java.beans.BeanInfo;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.ReflectPermission;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.ibatis.reflection.invoker.GetFieldInvoker;
import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.invoker.MethodInvoker;
import org.apache.ibatis.reflection.invoker.SetFieldInvoker;
import org.apache.ibatis.reflection.property.PropertyNamer;

/**
 * 这个类表示一组可缓存的类定义信息允许在属性名称和getter / setter方法之间轻松映射。
 * @author Clinton Begin
 */
public class Reflector {
  //对应Class类型
  private final Class<?> type;
  //可读的属性，所谓可读属性就是存在相应getter方法的属性
  private final String[] readablePropertyNames;
  //可写的属性，所谓可写属性就是存在相应setter方法的属性
  private final String[] writeablePropertyNames;
  //setter映射表，key是属性，值是Invoker 就是对setter方法的一个封装
  private final Map<String, Invoker> setMethods = new HashMap<String, Invoker>();
  //getter映射表，key是属性，值是Invoker
  private final Map<String, Invoker> getMethods = new HashMap<String, Invoker>();
  //写方法参数类型,key是属性名称值是参数类型
  private final Map<String, Class<?>> setTypes = new HashMap<String, Class<?>>();
  //读方法返回类型映射表，key是属性名称，值是getter返回值的类型
  private final Map<String, Class<?>> getTypes = new HashMap<String, Class<?>>();
  //默认构造方法
  private Constructor<?> defaultConstructor;
  //对读写无感的属性映射表，也就是记录所有读写方法的集合
  private Map<String, String> caseInsensitivePropertyMap = new HashMap<String, String>();

  public Reflector(Class<?> clazz) {
    type = clazz;
    //初始化defaultConstructor
    addDefaultConstructor(clazz);
    //初始化getMethods,setMethods
    addGetterAndSetterMethods(clazz);
    //
    addFields(clazz);
    //可读属性名称数组
    readablePropertyNames = getMethods.keySet().toArray(new String[getMethods.keySet().size()]);
    //可写属性名称数组
    writeablePropertyNames = setMethods.keySet().toArray(new String[setMethods.keySet().size()]);
    //将所有可读可写的属性都添加到caseInsensitivePropertyMap
    for (String propName : readablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
    for (String propName : writeablePropertyNames) {
      caseInsensitivePropertyMap.put(propName.toUpperCase(Locale.ENGLISH), propName);
    }
  }

  //初始化默认构造器
  private void addDefaultConstructor(Class<?> clazz) {
    //获取当前类声明的所有构造方法
    Constructor<?>[] consts = clazz.getDeclaredConstructors();
    //遍历构造方法
    for (Constructor<?> constructor : consts) {
      //如果构造方法是无参构造方法
      if (constructor.getParameterTypes().length == 0) {
        //如归可以访问私有方法，则将构造方法的accessible设置为true
        if (canAccessPrivateMethods()) {
          try {
            constructor.setAccessible(true);
          } catch (Exception e) {
            // Ignored. This is only a final precaution, nothing we can do.
          }
        }
        //如果构造方法是可访问的则将其设置为默认构造方法
        if (constructor.isAccessible()) {
          this.defaultConstructor = constructor;
        }
      }
    }
  }

  private void addGetterAndSetterMethods(Class<?> cls)  {
    try {
      //冲突的getter方法，该映射表中保存了属性的所有getter方法
      Map<String, List<Method>> conflictingGetters = new HashMap<String, List<Method>>();
      //冲突的setter方法
      Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();

      //通过javaBean的内省获取PropertyDescriptor
      PropertyDescriptor[] propertyDescriptors = Introspector.getBeanInfo(cls).getPropertyDescriptors();

      //遍历该类下的属性
      for (PropertyDescriptor propertyDescriptor : propertyDescriptors) {
        //获取属性的名称
        String name = propertyDescriptor.getName();
        //将属性的getter方法添加到映射表中
        addMethodConflict(conflictingGetters, name, propertyDescriptor.getReadMethod());
        //将属性的setter方法添加到映射表中
        addMethodConflict(conflictingSetters,name,propertyDescriptor.getWriteMethod());

      }
      //解决getter方法冲突，所谓解决冲突就是如果存在多个getter方法选择一个最优方法
      resolveGetterConflicts(conflictingGetters);
      //解决setter方法冲突
      resolveSetterConflicts(conflictingSetters);
    }catch (Exception e){
      //忽略异常
    }
  }

  //解决getter方法冲突
  private void resolveGetterConflicts(Map<String, List<Method>> conflictingGetters) {
    //遍历所有属性
    for (Entry<String, List<Method>> entry : conflictingGetters.entrySet()) {
      Method winner = null;
      //获取属性名称
      String propName = entry.getKey();
      //遍历该属性对应的getter方法
      for (Method candidate : entry.getValue()) {
        //如果胜出者为null则将当前申请者赋值给生出者，该方法在第一次循环的时候执行
        if (winner == null) {
          winner = candidate;
          continue;
        }
        //获取胜出者的返回类型
        Class<?> winnerType = winner.getReturnType();
        //获取申请者的返回类型
        Class<?> candidateType = candidate.getReturnType();
        //如果胜出者的返回类型同申请者的返回类型相同
        if (candidateType.equals(winnerType)) {
          //如果返回类型不是boolean类型则抛出反射异常，boolean 类型优先使用isXXX
          if (!boolean.class.equals(candidateType)) {
            throw new ReflectionException(
                "Illegal overloaded getter method with ambiguous type for property "
                    + propName + " in class " + winner.getDeclaringClass()
                    + ". This breaks the JavaBeans specification and can cause unpredictable results.");
          } else if (candidate.getName().startsWith("is")) {//如果返回类型是boolean类型则isXXX胜出
            winner = candidate;
          }
        } else if (candidateType.isAssignableFrom(winnerType)) {//如果申请者的类型是当前胜出者的父类或父接口则指定范围更小的胜出
          // OK getter type is descendant
        } else if (winnerType.isAssignableFrom(candidateType)) {
          winner = candidate;
        } else {
          throw new ReflectionException(
              "Illegal overloaded getter method with ambiguous type for property "
                  + propName + " in class " + winner.getDeclaringClass()
                  + ". This breaks the JavaBeans specification and can cause unpredictable results.");
        }
      }
      //将最优的getter方法添加到getter方法映射表中
      addGetMethod(propName, winner);
    }
  }

  private void addGetMethod(String name, Method method) {
    //如果是有效的属性名称，则将属性名称对象的getter方法包装成一个Invoker添加到getMethods中，同时将返回值类型放到getTypes中
    if (isValidPropertyName(name)) {
      getMethods.put(name, new MethodInvoker(method));
      Type returnType = TypeParameterResolver.resolveReturnType(method, type);
      getTypes.put(name, typeToClass(returnType));
    }
  }

  private void addSetMethods(Class<?> cls) {
    Map<String, List<Method>> conflictingSetters = new HashMap<String, List<Method>>();
    Method[] methods = getClassMethods(cls);
    for (Method method : methods) {
      String name = method.getName();
      if (name.startsWith("set") && name.length() > 3) {
        if (method.getParameterTypes().length == 1) {
          name = PropertyNamer.methodToProperty(name);
          addMethodConflict(conflictingSetters, name, method);
        }
      }
    }
    resolveSetterConflicts(conflictingSetters);
  }

  private void addMethodConflict(Map<String, List<Method>> conflictingMethods, String name, Method method) {
    //获取属性对应的方法集合
    List<Method> list = conflictingMethods.get(name);
    //如果集合为空，则创建一个新的ArrayList,并将集合放到映射表中
    if (list == null) {
      list = new ArrayList<Method>();
      conflictingMethods.put(name, list);
    }
    //将当前方法添加到集合中
    list.add(method);
  }

  private void resolveSetterConflicts(Map<String, List<Method>> conflictingSetters) {
    //遍历所有属性
    for (String propName : conflictingSetters.keySet()) {
      //获取当前属性的所有setter方法
      List<Method> setters = conflictingSetters.get(propName);
      //获取属性的getter方法返回类型
      Class<?> getterType = getTypes.get(propName);
      Method match = null;
      ReflectionException exception = null;
      //遍历setter方法
      for (Method setter : setters) {
        //获取setter 方法的参数类型
        Class<?> paramType = setter.getParameterTypes()[0];
        //如果setter方法的参数类型和getter方法返回类型相同则匹配出最佳setter
        if (paramType.equals(getterType)) {
          // should be the best match
          match = setter;
          break;
        }
        //如果没有匹配getter返回类型且没有异常，则选择一个最佳方法
        if (exception == null) {
          try {
            match = pickBetterSetter(match, setter, propName);
          } catch (ReflectionException e) {
            // there could still be the 'best match'
            match = null;
            exception = e;
          }
        }
      }
      //如果遍历结束后还是没有找到setter方法则抛出异常，通过上文代码可以知道此时exception一定不为null
      if (match == null) {
        throw exception;
      } else {// 如果有匹配的setter方法则添加到setter映射表中
        addSetMethod(propName, match);
      }
    }
  }

  private Method pickBetterSetter(Method setter1, Method setter2, String property) {
    if (setter1 == null) {
      return setter2;
    }
    Class<?> paramType1 = setter1.getParameterTypes()[0];
    Class<?> paramType2 = setter2.getParameterTypes()[0];
    if (paramType1.isAssignableFrom(paramType2)) {
      return setter2;
    } else if (paramType2.isAssignableFrom(paramType1)) {
      return setter1;
    }
    throw new ReflectionException("Ambiguous setters defined for property '" + property + "' in class '"
        + setter2.getDeclaringClass() + "' with types '" + paramType1.getName() + "' and '"
        + paramType2.getName() + "'.");
  }

  private void addSetMethod(String name, Method method) {
    if (isValidPropertyName(name)) {
      setMethods.put(name, new MethodInvoker(method));
      Type[] paramTypes = TypeParameterResolver.resolveParamTypes(method, type);
      setTypes.put(name, typeToClass(paramTypes[0]));
    }
  }

  //将Type转换为Class
  private Class<?> typeToClass(Type src) {
    Class<?> result = null;
    //如果src是Class的实例则返回class
    if (src instanceof Class) {
      result = (Class<?>) src;
    } else if (src instanceof ParameterizedType) {
      result = (Class<?>) ((ParameterizedType) src).getRawType();
    } else if (src instanceof GenericArrayType) {
      Type componentType = ((GenericArrayType) src).getGenericComponentType();
      if (componentType instanceof Class) {
        result = Array.newInstance((Class<?>) componentType, 0).getClass();
      } else {
        Class<?> componentClass = typeToClass(componentType);
        result = Array.newInstance((Class<?>) componentClass, 0).getClass();
      }
    }
    if (result == null) {
      result = Object.class;
    }
    return result;
  }

  private void addFields(Class<?> clazz) {
    //获取该类中所声明的所有属性
    Field[] fields = clazz.getDeclaredFields();
    //遍历属性
    for (Field field : fields) {
      //如果可以访问私有方法则将accessible设置为true
      if (canAccessPrivateMethods()) {
        try {
          field.setAccessible(true);
        } catch (Exception e) {
          // Ignored. This is only a final precaution, nothing we can do.
        }
      }
      //如果属性是可以访问的
      if (field.isAccessible()) {
        //如果setter方法表中不存在这个属性
        if (!setMethods.containsKey(field.getName())) {

          int modifiers = field.getModifiers();
          //如果这个方法不是final的静态方法就将该方法添加到setField中
          if (!(Modifier.isFinal(modifiers) && Modifier.isStatic(modifiers))) {
            addSetField(field);
          }
        }
        //如果这个属性在getter方法映射表中不存在则将添加到getField中
        if (!getMethods.containsKey(field.getName())) {
          addGetField(field);
        }
      }
    }
    //如果还有父类方法则递归执行
    if (clazz.getSuperclass() != null) {
      addFields(clazz.getSuperclass());
    }
  }

  private void addSetField(Field field) {
    //如果是有效属性，则对该属性创建一个SetFieldInvoker对象保存到Setter方法映射表中
    if (isValidPropertyName(field.getName())) {
      setMethods.put(field.getName(), new SetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      setTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private void addGetField(Field field) {
    //如果是有效属性，则对该属性创建一个GetFieldInvoker对象保存到Setter方法映射表中
    if (isValidPropertyName(field.getName())) {
      getMethods.put(field.getName(), new GetFieldInvoker(field));
      Type fieldType = TypeParameterResolver.resolveFieldType(field, type);
      getTypes.put(field.getName(), typeToClass(fieldType));
    }
  }

  private boolean isValidPropertyName(String name) {
    return !(name.startsWith("$") || "serialVersionUID".equals(name) || "class".equals(name));
  }

  /*
   * This method returns an array containing all methods
   * declared in this class and any superclass.
   * We use this method, instead of the simpler Class.getMethods(),
   * because we want to look for private methods as well.
   *
   * @param cls The class
   * @return An array containing all methods in this class
   */
  private Method[] getClassMethods(Class<?> cls) {
    Map<String, Method> uniqueMethods = new HashMap<String, Method>();
    Class<?> currentClass = cls;
    while (currentClass != null) {
      addUniqueMethods(uniqueMethods, currentClass.getDeclaredMethods());

      // we also need to look for interface methods -
      // because the class may be abstract
      Class<?>[] interfaces = currentClass.getInterfaces();
      for (Class<?> anInterface : interfaces) {
        addUniqueMethods(uniqueMethods, anInterface.getMethods());
      }

      currentClass = currentClass.getSuperclass();
    }

    Collection<Method> methods = uniqueMethods.values();

    return methods.toArray(new Method[methods.size()]);
  }

  private void addUniqueMethods(Map<String, Method> uniqueMethods, Method[] methods) {
    for (Method currentMethod : methods) {
      if (!currentMethod.isBridge()) {
        String signature = getSignature(currentMethod);
        // check to see if the method is already known
        // if it is known, then an extended class must have
        // overridden a method
        if (!uniqueMethods.containsKey(signature)) {
          if (canAccessPrivateMethods()) {
            try {
              currentMethod.setAccessible(true);
            } catch (Exception e) {
              // Ignored. This is only a final precaution, nothing we can do.
            }
          }

          uniqueMethods.put(signature, currentMethod);
        }
      }
    }
  }

  private String getSignature(Method method) {
    StringBuilder sb = new StringBuilder();
    Class<?> returnType = method.getReturnType();
    if (returnType != null) {
      sb.append(returnType.getName()).append('#');
    }
    sb.append(method.getName());
    Class<?>[] parameters = method.getParameterTypes();
    for (int i = 0; i < parameters.length; i++) {
      if (i == 0) {
        sb.append(':');
      } else {
        sb.append(',');
      }
      sb.append(parameters[i].getName());
    }
    return sb.toString();
  }

  //检查是否有反射权限，如果配置了SecurityManager同时没有反射权限则返回false
  private static boolean canAccessPrivateMethods() {
    try {
      //获取系统安全管理器
      SecurityManager securityManager = System.getSecurityManager();
      //如果系统安全管理器不为null
      if (null != securityManager) {
        //检查是否有反射权限，如果有则返回true,否则返回false
        securityManager.checkPermission(new ReflectPermission("suppressAccessChecks"));
      }
    } catch (SecurityException e) {
      return false;
    }
    return true;
  }

  /*
   * Gets the name of the class the instance provides information for
   *
   * @return The class name
   */
  public Class<?> getType() {
    return type;
  }

  public Constructor<?> getDefaultConstructor() {
    if (defaultConstructor != null) {
      return defaultConstructor;
    } else {
      throw new ReflectionException("There is no default constructor for " + type);
    }
  }

  public boolean hasDefaultConstructor() {
    return defaultConstructor != null;
  }

  public Invoker getSetInvoker(String propertyName) {
    Invoker method = setMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }
  //获取指定属性的getter方法的invoker
  public Invoker getGetInvoker(String propertyName) {
    Invoker method = getMethods.get(propertyName);
    if (method == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return method;
  }

  /*
   * Gets the type for a property setter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery setter
   */
  public Class<?> getSetterType(String propertyName) {
    Class<?> clazz = setTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no setter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets the type for a property getter
   *
   * @param propertyName - the name of the property
   * @return The Class of the propery getter
   */
  public Class<?> getGetterType(String propertyName) {
    Class<?> clazz = getTypes.get(propertyName);
    if (clazz == null) {
      throw new ReflectionException("There is no getter for property named '" + propertyName + "' in '" + type + "'");
    }
    return clazz;
  }

  /*
   * Gets an array of the readable properties for an object
   *
   * @return The array
   */
  public String[] getGetablePropertyNames() {
    return readablePropertyNames;
  }

  /*
   * Gets an array of the writeable properties for an object
   *
   * @return The array
   */
  public String[] getSetablePropertyNames() {
    return writeablePropertyNames;
  }

  /*
   * Check to see if a class has a writeable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a writeable property by the name
   */
  public boolean hasSetter(String propertyName) {
    return setMethods.keySet().contains(propertyName);
  }

  /*
   * Check to see if a class has a readable property by name
   *
   * @param propertyName - the name of the property to check
   * @return True if the object has a readable property by the name
   */
  public boolean hasGetter(String propertyName) {
    return getMethods.keySet().contains(propertyName);
  }

  public String findPropertyName(String name) {
    return caseInsensitivePropertyMap.get(name.toUpperCase(Locale.ENGLISH));
  }
}
