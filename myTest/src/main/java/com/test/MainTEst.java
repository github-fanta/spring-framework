package com.test;

import com.test.selfEditor.Customer;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MainTEst {
	public static void main(String[] args) {
//		ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("classpath:beans.xml");
//		User user = ctx.getBean(User.class);
//		System.out.println("user:" + user);

		ClassPathXmlApplicationContext ctx = new ClassPathXmlApplicationContext("selfEditor.xml");
		Customer bean = ctx.getBean(Customer.class);
		System.out.println(bean);
	}
}
