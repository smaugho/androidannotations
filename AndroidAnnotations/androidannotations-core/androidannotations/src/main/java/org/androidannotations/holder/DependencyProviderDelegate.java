/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed To in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.androidannotations.holder;

import static com.helger.jcodemodel.JExpr._new;
import static com.helger.jcodemodel.JExpr._null;
import static com.helger.jcodemodel.JMod.PRIVATE;
import static com.helger.jcodemodel.JMod.PUBLIC;
import static com.helger.jcodemodel.JMod.STATIC;
import static org.androidannotations.helper.ModelConstants.generationSuffix;

import java.util.HashMap;
import java.util.Map;

import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.JClassAlreadyExistsException;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JFieldVar;
import com.helger.jcodemodel.JMethod;
import com.helger.jcodemodel.JVar;

public class DependencyProviderDelegate extends GeneratedClassHolderDelegate<EComponentHolder> {

	private static final String GET_INSTANCE_METHOD_NAME = "getInstance" + generationSuffix();
	private static final String SET_INSTANCE_METHOD_NAME = "setInstance" + generationSuffix();

	private JDefinedClass dependenciesProviderClass;
	private JMethod getInstanceMethod;
	private JMethod setInstanceMethod;

	private Map<String, JMethod> providerMethodByClass = new HashMap<>();

	public DependencyProviderDelegate(EComponentHolder holder) {
		super(holder);
	}

	public JMethod createProviderMethod(AbstractJClass clazz) {
		if (!providerMethodByClass.containsKey(clazz.fullName())) {
			String className = clazz.name();
			JMethod method = getDependenciesProviderClass().method(PUBLIC, clazz, "get" + className.substring(0, 1).toUpperCase() + className.substring(1));
			providerMethodByClass.put(clazz.fullName(), method);
			return method;
		}
		return null;
	}

	public JMethod getProviderMethod(AbstractJClass clazz) {
		return providerMethodByClass.get(clazz.fullName());
	}

	public JDefinedClass getDependenciesProviderClass() {
		if (dependenciesProviderClass == null) {
			setDependenciesProviderClass();
		}
		return dependenciesProviderClass;
	}

	public JMethod getInstanceMethod() {
		if (getInstanceMethod == null) {
			setDependenciesProviderClass();
		}
		return getInstanceMethod;
	}

	public JMethod getInstanceSetterMethod() {
		if (setInstanceMethod == null) {
			setDependenciesProviderClass();
		}
		return setInstanceMethod;
	}

	private void setDependenciesProviderClass() {
		try {
			dependenciesProviderClass = getGeneratedClass()._class(PUBLIC | STATIC, "DependenciesProvider" + generationSuffix());
			JFieldVar instanceField = dependenciesProviderClass.field(PRIVATE | STATIC, dependenciesProviderClass, "instance");

			getInstanceMethod = dependenciesProviderClass.method(PUBLIC | STATIC, dependenciesProviderClass, GET_INSTANCE_METHOD_NAME);
			getInstanceMethod.body()._if(instanceField.eq(_null()))._then().assign(instanceField, _new(dependenciesProviderClass));
			getInstanceMethod.body()._return(instanceField);

			setInstanceMethod = dependenciesProviderClass.method(PUBLIC | STATIC, codeModel().VOID, SET_INSTANCE_METHOD_NAME);
			JVar instParam = setInstanceMethod.param(dependenciesProviderClass, "inst");
			setInstanceMethod.body().assign(instanceField, instParam);
		} catch (JClassAlreadyExistsException e) {
			throw new IllegalStateException("Class Already Created: " + e.getMessage());
		}
	}

}
