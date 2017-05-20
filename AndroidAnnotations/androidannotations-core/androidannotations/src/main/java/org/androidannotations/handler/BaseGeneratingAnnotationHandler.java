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
package org.androidannotations.handler;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.ElementValidation;
import org.androidannotations.holder.GeneratedClassHolder;

import com.dspot.declex.helper.ActionHelper;
import com.dspot.declex.util.TypeUtils;

public abstract class BaseGeneratingAnnotationHandler<T extends GeneratedClassHolder> extends BaseAnnotationHandler<T> implements GeneratingAnnotationHandler<T> {

	private ActionHelper actionHelper;
	
	public BaseGeneratingAnnotationHandler(Class<?> targetClass, AndroidAnnotationsEnvironment environment) {
		super(targetClass, environment);
		actionHelper = ActionHelper.getInstance(environment);
	}

	public BaseGeneratingAnnotationHandler(String target, AndroidAnnotationsEnvironment environment) {
		super(target, environment);
		actionHelper = ActionHelper.getInstance(environment);
	}

	@Override
	protected void validate(Element element, ElementValidation valid) {
		
		validatorHelper.isNotFinal(element, valid);
		
		if (element.getKind().equals(ElementKind.CLASS)) {
			actionHelper.validate(element, this);
		}
		
		if (!filesCacheHelper.isAncestor(element.asType().toString())) {
			filesCacheHelper.addGeneratedClass(
				TypeUtils.getGeneratedClassName(element, getEnvironment()), 
				element
			);
		}

		if (isInnerClass(element)) {

			validatorHelper.isNotPrivate(element, valid);

			validatorHelper.isStatic(element, valid);

			validatorHelper.enclosingElementHasAndroidAnnotation(element, valid);

			validatorHelper.enclosingElementIsNotAbstractIfNotAbstract(element, valid);
		}
	}

	private boolean isInnerClass(Element element) {
		TypeElement typeElement = (TypeElement) element;
		return typeElement.getNestingKind().isNested();
	}
}
