package com.test.testFactoryBean;

import com.test.User;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class MyFactoryBean implements FactoryBean<User> {

	@Override
	public User getObject() throws Exception {
		return new User(){{
			setName("liq");
			setAge(20);
		}};
	}

	@Override
	public Class<?> getObjectType() {
		return User.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
