package com.test.testFactoryBean;

import com.test.User;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class FactoryBeanMain {
	public static void main(String[] args) {
		ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("beans.xml");
		User user = (User) ctx.getBean("myFB");
		System.out.println(user);
		User user2 = (User) ctx.getBean("myFB");
		System.out.println(user2);
	}
}
