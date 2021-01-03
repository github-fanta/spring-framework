package com.test.selfEditor;


import java.beans.PropertyEditorSupport;

// 1. 实现自己的一个属性编辑器
public class AddressPropertyEditor extends PropertyEditorSupport {

	@Override
	public void setAsText(String text) throws IllegalArgumentException {
		// 根据"省_市_区" 的格式解析属性
		String[] s = text.split("_");
		Address address = new Address();
		address.setProvince(s[0]);
		address.setCity(s[1]);
		address.setTown(s[2]);
		// 这个时候还没完 这个方法没有返回值  看看父类是怎么做的
		// public void setAsText(String text) throws java.lang.IllegalArgumentException {
		//        if (value instanceof String) {
		// 这里调用了这个！！
		//            setValue(text);
		//            return;
		//        }
		//        throw new java.lang.IllegalArgumentException(text);
		//    }

		this.setValue(address);
	}
}
