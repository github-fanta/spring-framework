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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 无论是什么情况，优先执行BeanDefinitionRegistryPostProcessors
		// 将已经执行过的BFPP存储在processedBeans中，防止重复执行
		Set<String> processedBeans = new HashSet<>();

		// BeanDefinitionRegistry 是对beanDefinition进行增删改查的一个工具类
		// 此处是DefaultListableBeanFactory,实现了BeanDefinitionRegistry接口，所以为true.
		if (beanFactory instanceof BeanDefinitionRegistry) {
			// 类型转换过来。
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			// 两个接口区分： BeanDefinitionRegistryPostProcessor是BeanFactoryPostProcessor的子集
			// BeanFactoryPostProcessor的操作对象是BeanFactory,
			// 而BeanDefinitionRegistryPostProcessor操作对象是BeanDefinition
			// 存放BeanFactoryPostProcessor
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			// 存放BeanDefinitionRegistryPostProcessor的集合
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 处理入参中的beanFactoryPostProcessors， 遍历所有的beanFactoryPostProcessors,将beanDefinitionRegistryPostProcessor
			// 和BeanFactoryPostProcessor区分开
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				// 如果是BeanDefinitionRegistryPostProcessor
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					// 这里与普通的BeanFactoryPostProcessor不同(只是拎出来，并不执行)，这里还会直接执行postProcessBeanDefinitionRegistry方法
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					// 添加到registryProcessors,用于后续执行postProcessBeanFactory方法
					registryProcessors.add(registryProcessor);
				}
				else {
					// 不是如果是BeanDefinitionRegistryPostProcessor 普通的BeanFactoryPostProcessor，
					// 添加到regularPostProcessors， 用于后续执行postProcessBeanFactory方法
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 用于保存本次要执行的BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();
// >>>>>>>这段是一个完整的代码块，以后会有很多流程都以这里为基础>>>>>>>
//////////1.拿出PriorityOrdered的BennDefinitionRegistry的后置处理器//////////////////
//////////2.执行这些后置处理器/////////////////
//////////3.清空放这些后置处理器的集合/////////////////
			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 调用所有实现PriorityOrdered接口的BeanDefinitionRegistryPostProcessor实现类
			// 找到所有实现BeanDefinitionRegistryPostProcessor接口bean的名字
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			// 遍历每个bean名字
			for (String ppName : postProcessorNames) {
				// 检测是否实现了PriorityOrdered接口
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 获取名字对应的bean实例，添加到currentRegistryProcessors中  会触发getBean（）实例化的逻辑
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将要被执行的BFPP名称添加到processedBean，避免后续重复执行
					processedBeans.add(ppName);
				}
			}
			// 按照优先级进行排序操作 确定执行顺序
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 添加到registryProcessors中， 用于最后执行postProcessBeanFactory方法
			registryProcessors.addAll(currentRegistryProcessors);
			// 遍历currentRegistryProcessors 执行postProcessBeanDefinitionRegistry方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 执行完毕之后，清空currentRegistryProcessors
			currentRegistryProcessors.clear();
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 调用所有实现Ordered接口的BeanDefinitionRegistryPostProcessor实现类
			// 找到所有实现BeanDefinitionRegistryPostProcessor接口bean的beanName,
			// 此处需要重复查找后置处理器名字的原因在于上面的执行过程中(invokeBeanDefinitionRegistryPostProcessors,调用的后置处理器的逻辑中)
			// 可能会新增其他的BeanDefinitionRegistry(一般不会，但是我就要新增，框架你能怎么办，只能再读一遍了呗)
			// isTypeMatch(ppName, Ordered.class) 如果ppName的Class实现了PriorityOrdered也是true哦！ 所以就算你新加了一个PriorityOrdered  在这里还是逃不掉会被执行的呢
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 拿到Ordered接口的后置处理器 并且 还未执行过
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					// 获取名字对应的bean实例，添加到currentRegistryProcessors中  会触发getBean（）实例化的逻辑
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将要被执行的BFPP名称添加到processedBeans,避免后续重复执行
					processedBeans.add(ppName);
				}
			}
			// 按照优先级进行排序操作
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 添加到registryProcessors中，用于最后执行postProcessBeanFactory方法
			registryProcessors.addAll(currentRegistryProcessors);
			// 遍历currentRegistryProcessors，执行postProcessBeanDefinitionRegistry方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			// 执行完毕之后，清空currentRegistryProcessors
			currentRegistryProcessors.clear();

//>>>>>>>>>>>>>>>>>>>>除了PriorityOrder 和 Ordered之外的普通优先级>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				// 找出所有的BeanDefinitionRegistryPostProcessor
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				// 执行每个
				for (String ppName : postProcessorNames) {
					// 跳过已经执行过的BeanDefinitionRegistryPostProcessor
					// isTypeMatch(ppName, Ordered.class) 如果上面的处理器中加入了PriorityOrdered 或者 Ordered的处理器 在这里还是逃不掉会被执行的呢
					if (!processedBeans.contains(ppName)) {
						// 获取名字对应的bean实例，添加到currentRegistryProcessors中
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						// 将要被执行的BFPP名称添加到processedBeans，避免后续重复执行
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				// 其实没有优先级的普通级别，哪个在前哪个在后已经无所谓了
				// 我猜这里只是为了和前面保持一样的处理逻辑
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				// 添加到registryProcessors中
				registryProcessors.addAll(currentRegistryProcessors);
				// 执行所有普通优先级的BeanDefinitionRegistryPostProcessor
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				// 执行完毕，清空currentRegistryProcessors
				currentRegistryProcessors.clear();
			}

			/*现在开始来处理后置处理器的postProcessBeanFactory方法*/
//>>>>>>统一来执行>>>>>>>>>>>>>>>>>>
// 先调用BeanDefinitionRegistryPostProcessor(包含PiriorityOrdered，Ordered，普通的)的postProcessBeanFactory方法>>>>>>>>>>>>>>>>
// 而非postProcessBeanDefinitionRegistry() 因为上面四种处理代码中已经执行过了,
// 和参数传入的后置处理器 中的非BeanDefinitionRegistryPostProcessor的其他后置处理器
			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 调用所有  postProcessBeanFactory()方法
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			// 最后，调用入参beanFactoryPostProcessors中的 普通BeanFactroyPostProcessor
			// 而容器中的beanFactoryPostProcessors并没有在这里执行，在后面代码才执行的
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
//<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
		}

		// 收起if会看到，这里（beanFactory ！instanceof BeanDefinitionRegistry）
		else {
			// Invoke factory processors registered with the context instance.
			// 如果beanFactory不归属于BeanDefinitionRegistry类型，那么直接执行postProcessBeanFactory方法
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// 到此位置，入参beanFactoryPostProcessors和容器中的所有BeanDefinitionRegistryPostProcessor已经全部处理完毕，
		// 下面开始处理容器中所有的BeanFactoryPostProcessor
		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 找到所有实现BeanFactoryPostProcessor接口的类
		// 同样，这里也会遵循PriorityOrdered -> Ordered -> 普通
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 用于存放实现了PriorityOrdered接口的BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 用于存放Ordered接口的BeanFactoryPostProcessor的名字
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 用于存放普通BeanFactoryPostProcessor的名字
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 遍历postProcessorNames,将BeanFactoryPostProcessor 按实现PriorityOrdered,实现Ordered接口、普通三种分开
		for (String ppName : postProcessorNames) {
			// 跳过已经执行过的BeanFactoryPostProcessor
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// priorityOrdered直接拿到bean  其他都只是拿到名字
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 对实现了PriorityOrdered接口的BeanFactoryPostProcessor进行排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 率先 执行 PriorityOrdered接口的BeanFactoryPostProcessor， 执行postProcessBeanFactory方法
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		// 创建存放实现了Ordered接口的BeanFactoryPostProcessor集合
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();

		// 这里为什么不像上面一样重复获取了？ 不害怕用户自定义的 PriorityOrdered 的后置处理器自己加入新的处理器吗？
		// 因为这里的接口不一样，在postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory)中ConfigurableListableBeanFactory没有注册新的beanDefinition的接口
		// 而BeanDefinitionRegistryPostProcessor 的postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) 传入的BeanDefinitionRegistry是有注册BeanDefinition的功能的
		// 遍历存放实现了Ordered接口的BeanFactoryPostProcesssor名字的集合
		for (String postProcessorName : orderedPostProcessorNames) {
			// 将实现了Ordered接口的BeanFactoryPostProcesssor添加到集合中
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 对Ordered 的处理器排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 执行所有Ordered的BeanFactoryPostProcesssor，执行他们的postProcessBeanFactory
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 最后，创建存放普通的BeanFactoryPostProcessor 的集合
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		// 遍历存放实现了普通BeanFactoryPostProcessor名字的集合
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			// 将普通的BeanFactoryPostProcessor添加到集合中
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 执行普通BeanFactoryPostProcessor
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		// 清除元数据缓存(mergeBeanDefinitions、allBeanNamesByType、singletonBeanNameByType)
		// 因为后置处理器可能已经修改了原始元数据，例如，替换一些原始值中的占位符
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {
		// 从容器取出所有后置处理器的BeanDefination的名字
		// 找到所有实现了BeanPostProcessor接口的类
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		//记录下BeanPostProcessor的目标计数
		// 此处为啥要+1呢， 原因非常简单，因为下面多加了一个BeanPostProcessorChecker后置处理器
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// 添加BeanPostProcessorChecker(主要用于记录信息) 到beanFactory中
		// 这个checker就检查一下，输出个日志记录，没做啥
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 定义存放实现了PriorityOrdered接口的BeanPostProcessor集合
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 定义存放Spring内部的BeanPostProcessor
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 遍历存放在BeanFactory中的后置处理器
		for (String ppName : postProcessorNames) {
			// 1：分组一： PriorityOrdered 优先级的后置处理器
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				// 三个优先级中只有PriorityOrdered 直接实例化了，其他两个只是拿到postProcessorNames
				// 这里返回的pp，是已经执行 aware接口方法，postBeanProcessor，init等之后成品的Bean了。
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				// 若此bpp还实现了MergedBeanDefinitionPostProcessor
				// 那么将ppName对应的bean实例加到internalPostProcessors里
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			// 2：分组二： Ordered 优先级的后置处理器  想象@Order(1)那个注解标明执行顺序
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			// 3：分组三： 其他（nonOrdered） 优先级的后置处理器
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		// 首先，对实现了PriorityOrdered接口的beanPostProcessor实例进行排序操作
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 一个个放入抽象bean工厂的list中
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		// 然后是Ordered
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		// 最后 是普通的BPP
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		sortPostProcessors(internalPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// 由于要检测内部的bean比如ApplicationListeners，
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {
		Comparator<Object> comparatorToUse = null;
		if (beanFactory instanceof DefaultListableBeanFactory) {
			// 获取设置的比较器
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			// 如果没有设置比较器，则使用默认的OrderComparator
			// 可以看这个单例比较器内部的比较逻辑
			comparatorToUse = OrderComparator.INSTANCE;
		}
		// 使用比较器对postProcessors进行排序
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		/**
		 * 后置处理器的before方法，啥都不干，直接返回对象
		 * @param bean the new bean instance
		 * @param beanName the name of the bean
		 * @return
		 */
		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		/**
		 * 后置处理器的after方法，用来判断哪些是不需要检测的bean 实际工作就输出了个日志
		 * @param bean the new bean instance
		 * @param beanName the name of the bean
		 * @return
		 */
		@Override
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			// 1、BeanPostProcessor类型不检测
			// 2、ROLE_INFRASTRUCTURE这种角色的bean不检测(Spring自己的bean)
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					// 不是所有的bean都有资格被vBeanPostProcessors处理，比如动态代理，你没配的bean 是不会代理的
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
