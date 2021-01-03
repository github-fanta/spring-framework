package com.test.selfEditor;

import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.beans.PropertyEditorRegistry;

// 2.属性编辑器弄好了，还得自己实现一个注册器把属性编辑器注册进去
public class AddressPropertyEditorRegistrar implements PropertyEditorRegistrar {

	@Override
	public void registerCustomEditors(PropertyEditorRegistry registry) {
		registry.registerCustomEditor(Address.class, new AddressPropertyEditor());
	}
}
