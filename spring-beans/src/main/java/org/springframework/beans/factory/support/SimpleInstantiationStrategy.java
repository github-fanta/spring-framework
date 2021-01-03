/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;

import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.lang.Nullable;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

/**
 * Simple object instantiation strategy for use in a BeanFactory.
 *
 * <p>Does not support Method Injection, although it provides hooks for subclasses
 * to override to add Method Injection support, for example by overriding methods.
 *
 * @author Rod Johnson
 * @author Juergen Hoeller
 * @since 1.1
 */
// CglibSubclassingInstantiationStrategy
public class SimpleInstantiationStrategy implements InstantiationStrategy {

	// FactoryMethod 的ThreadLocal对象
	private static final ThreadLocal<Method> currentlyInvokedFactoryMethod = new ThreadLocal<>();


	/**
	 * 返回当前现成所有的FactoryMethod变量值
	 * Return the factory method currently being invoked or {@code null} if none.
	 * <p>Allows factory method implementations to determine whether the current
	 * caller is the container itself as opposed to user code.
	 */
	@Nullable
	public static Method getCurrentlyInvokedFactoryMethod() {
		return currentlyInvokedFactoryMethod.get();
	}


	/**
	 *
	 *使用初始化策略实例化Bean
	 */
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		// Don't override the class with CGLIB if no overrides.
		// 如果有需要覆盖或者动态替换的方法，则当然需要使用cglib进行动态代理，因为可以在创建代理的同时将动态方法织入类中
		// 但是如果没有需要动态改变的方法，为了方便直接反射就可以了。
		// 如果没有方法覆盖，也就是用户没有使用replace或者lookup的配置方法，那么直接使用反射的方式
		if (!bd.hasMethodOverrides()) {
			Constructor<?> constructorToUse;
			synchronized (bd.constructorArgumentLock) {
				// 看bd里是否有构造方法（之前解析过构造器，缓存在这里）
				constructorToUse = (Constructor<?>) bd.resolvedConstructorOrFactoryMethod;
				// 如果没有构造方法且没有工厂方法
				if (constructorToUse == null) {

					// 使用JDK的反射机制
					final Class<?> clazz = bd.getBeanClass();
					// 要实例化的Bean是否是接口
					if (clazz.isInterface()) {
						throw new BeanInstantiationException(clazz, "Specified class is an interface");
					}
					try {
						if (System.getSecurityManager() != null) {
							// 反射获取构造方法
							constructorToUse = AccessController.doPrivileged(
									(PrivilegedExceptionAction<Constructor<?>>) clazz::getDeclaredConstructor);
						}
						else {
							// 获取默认的无参构造器
							constructorToUse = clazz.getDeclaredConstructor();
						}
						// 获取到构造器之后将构造器赋值给bd中的属性
						bd.resolvedConstructorOrFactoryMethod = constructorToUse;
					}
					catch (Throwable ex) {
						throw new BeanInstantiationException(clazz, "No default constructor found", ex);
					}
				}
			}
			// BeanUtils实例化， 通过反射机制调用构造方法.newInstance(arg)
			return BeanUtils.instantiateClass(constructorToUse);
		} // endof BeanDefination告诉了此bean有重写方法
		else {
			// 如果有方法覆盖，也就是用户有使用replace或者lookup的配置方法。
			// 需要将这两个配置提供的功能切入进去，所以就必须要使用动态代理的方式将包含两个特性所对应的逻辑的拦截器设置进去
			// Must generate CGLIB subclass.
			// 使用CGLIB来实例化
			return instantiateWithMethodInjection(bd, beanName, owner);
		}
	}

	/**
	 * 此方法没有做任何的实现，直接抛出异常，可以由子类重写覆盖
	 *
	 * Subclasses can override this method, which is implemented to throw
	 * UnsupportedOperationException, if they can instantiate an object with
	 * the Method Injection specified in the given RootBeanDefinition.
	 * Instantiation should use a no-arg constructor.
	 */
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}

	// 使用有参构造方法进行实例化
	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			final Constructor<?> ctor, Object... args) {

		// 检查bd对象是否有MethodOverrides对象，没有的话则直接实例化对象
		if (!bd.hasMethodOverrides()) {
			if (System.getSecurityManager() != null) {
				// use own privileged to change accessibility (when security is on)
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(ctor);
					return null;
				});
			}
			// 通过反射实例化对象
			return BeanUtils.instantiateClass(ctor, args);
		}
		else {
			// 如果有methodOverride对象，则调用方法来进行实现
			return instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
		}
	}

	/**
	 * Subclasses can override this method, which is implemented to throw
	 * UnsupportedOperationException, if they can instantiate an object with
	 * the Method Injection specified in the given RootBeanDefinition.
	 * Instantiation should use the given constructor and parameters.
	 */
	protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName,
			BeanFactory owner, @Nullable Constructor<?> ctor, Object... args) {

		throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
	}

	@Override
	public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner,
			@Nullable Object factoryBean, final Method factoryMethod, Object... args) {

		try {
			// 是否包含系统安全管理器
			if (System.getSecurityManager() != null) {
				AccessController.doPrivileged((PrivilegedAction<Object>) () -> {
					ReflectionUtils.makeAccessible(factoryMethod);
					return null;
				});
			}
			else {
				// 反射工具类设置访问权限
				ReflectionUtils.makeAccessible(factoryMethod);
			}
			// 获取原有的Method对象
			Method priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get();
			try {
				// 设置当前的Method
				currentlyInvokedFactoryMethod.set(factoryMethod);
				// 使用factoryMethod实例化对象
				Object result = factoryMethod.invoke(factoryBean, args);
				if (result == null) {
					result = new NullBean();
				}
				return result;
			}
			finally {
				// 实例化完成后恢复现场
				if (priorInvokedFactoryMethod != null) {
					currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod);
				}
				else {
					currentlyInvokedFactoryMethod.remove();
				}
			}
		}
		catch (IllegalArgumentException ex) {
			throw new BeanInstantiationException(factoryMethod,
					"Illegal arguments to factory method '" + factoryMethod.getName() + "'; " +
					"args: " + StringUtils.arrayToCommaDelimitedString(args), ex);
		}
		catch (IllegalAccessException ex) {
			throw new BeanInstantiationException(factoryMethod,
					"Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
		}
		catch (InvocationTargetException ex) {
			String msg = "Factory method '" + factoryMethod.getName() + "' threw exception";
			if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory &&
					((ConfigurableBeanFactory) owner).isCurrentlyInCreation(bd.getFactoryBeanName())) {
				msg = "Circular reference involving containing bean '" + bd.getFactoryBeanName() + "' - consider " +
						"declaring the factory method as static for independence from its containing instance. " + msg;
			}
			throw new BeanInstantiationException(factoryMethod, msg, ex.getTargetException());
		}
	}

}
