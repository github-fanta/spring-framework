package com.test.autowired;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class Outer {

	@Autowired
	private Inner inner;
}
