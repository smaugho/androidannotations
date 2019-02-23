/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
 * Copyright (C) 2016-2019 the AndroidAnnotations project
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
package org.androidannotations.internal.core.handler;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.ElementValidation;
import org.androidannotations.annotations.EBean;
import org.androidannotations.handler.BaseGeneratingAnnotationHandler;
import org.androidannotations.holder.EBeanHolder;

public class EBeanHandler extends BaseGeneratingAnnotationHandler<EBeanHolder> {

	private static Map<String, List<String>> implementations = new HashMap<>();

	public EBeanHandler(AndroidAnnotationsEnvironment environment) {
		super(EBean.class, environment);
	}

	@Override
	public EBeanHolder createGeneratedClassHolder(AndroidAnnotationsEnvironment environment, TypeElement annotatedComponent) throws Exception {
		return new EBeanHolder(environment, annotatedComponent);
	}

	@Override
	public void validate(Element element, ElementValidation valid) {
		super.validate(element, valid);

		validatorHelper.isNotInterface((TypeElement) element, valid);

		validatorHelper.isNotPrivate(element, valid);

		validatorHelper.isAbstractOrHasEmptyOrContextConstructor(element, valid);

		registerImplementationsFor(element, ((TypeElement) element).getQualifiedName().toString());

	}

	private void registerImplementationsFor(Element element, String implementationClassName) {

		List<? extends TypeMirror> superTypes = getProcessingEnvironment().getTypeUtils().directSupertypes(element.asType());
		for (TypeMirror type : superTypes) {

			TypeElement superElement = getProcessingEnvironment().getElementUtils().getTypeElement(type.toString());
			if (superElement == null || superElement.asType().toString().equals(Object.class.getCanonicalName())) {
				continue;
			}

			if (superElement.getKind().equals(ElementKind.INTERFACE)) {
				putImplementationFor(superElement.getQualifiedName().toString(), implementationClassName);
			}

			registerImplementationsFor(superElement, implementationClassName);
		}

	}

	@Override
	public void process(Element element, EBeanHolder holder) {

		EBean eBeanAnnotation = adiHelper.getAnnotation(element, EBean.class);
		EBean.Scope eBeanScope = eBeanAnnotation.scope();
		boolean hasSingletonScope = eBeanScope == EBean.Scope.Singleton;

		holder.createFactoryMethod(hasSingletonScope);

		if (!hasSingletonScope) {
			holder.invokeInitInConstructors();
			holder.createRebindMethod();
		}
	}

	public static List<String> getImplementationsFor(String className) {
		return implementations.get(className);
	}

	public static void putImplementationFor(String className, String implementationClassName) {
		List<String> implementations = getImplementationsFor(className);
		if (implementations == null) {
			implementations = new LinkedList<>();
			EBeanHandler.implementations.put(className, implementations);
		}
		implementations.add(implementationClassName);
	}

}
