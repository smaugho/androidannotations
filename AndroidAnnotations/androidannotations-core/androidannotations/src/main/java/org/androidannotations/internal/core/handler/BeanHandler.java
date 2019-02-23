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

import static com.helger.jcodemodel.JExpr._null;

import java.util.List;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.ElementValidation;
import org.androidannotations.annotations.Bean;
import org.androidannotations.annotations.EBean;
import org.androidannotations.annotations.NonConfigurationInstance;
import org.androidannotations.handler.BaseAnnotationHandler;
import org.androidannotations.handler.MethodInjectionHandler;
import org.androidannotations.helper.InjectHelper;
import org.androidannotations.holder.EBeanHolder;
import org.androidannotations.holder.EComponentHolder;

import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.IJAssignmentTarget;
import com.helger.jcodemodel.IJStatement;
import com.helger.jcodemodel.JBlock;
import com.helger.jcodemodel.JConditional;
import com.helger.jcodemodel.JInvocation;

public class BeanHandler extends BaseAnnotationHandler<EComponentHolder> implements MethodInjectionHandler<EComponentHolder> {

	private final InjectHelper<EComponentHolder> injectHelper;

	public BeanHandler(AndroidAnnotationsEnvironment environment) {
		super(Bean.class, environment);
		injectHelper = new InjectHelper<>(validatorHelper, this);
	}

	@Override
	public void validate(Element element, ElementValidation validation) {
		injectHelper.validate(Bean.class, element, validation);
		if (!validation.isValid()) {
			return;
		}

		validatorHelper.isNotPrivate(element, validation);

		TypeElement beanTypeElement = getProcessingEnvironment().getElementUtils().getTypeElement(element.asType().toString());
		if (beanTypeElement == null) {
			validation.addError("@Bean cannot inject the class " + element.asType());
			return;
		}

		if (beanTypeElement.getKind().equals(ElementKind.INTERFACE)) {
			List<String> implementations = EBeanHandler.getImplementationsFor(beanTypeElement.getQualifiedName().toString());
			if (implementations != null) {

				if (implementations.size() > 1) {
					validation.addError("@Bean cannot decide between multiple implementations of an interface: " + implementations);
				}

				return;
			}
		}

		validatorHelper.typeHasAnnotation(EBean.class, beanTypeElement, validation);

	}

	@Override
	public void process(Element element, EComponentHolder holder) {
		injectHelper.process(element, holder);
	}

	@Override
	public JBlock getInvocationBlock(Element element, EComponentHolder holder) {
		return holder.getInitBodyInjectionBlock();
	}

	@Override
	public void assignValue(JBlock targetBlock, IJAssignmentTarget fieldRef, EComponentHolder holder, Element element, Element param) {

		String typeQualifiedName = null;
		TypeElement beanTypeElement = getProcessingEnvironment().getElementUtils().getTypeElement(element.asType().toString());

		if (beanTypeElement.getKind().equals(ElementKind.INTERFACE)) {
			List<String> implementations = EBeanHandler.getImplementationsFor(beanTypeElement.getQualifiedName().toString());
			if (implementations != null) {
				typeQualifiedName = implementations.get(0);
			}
		}

		if (typeQualifiedName == null) {

			TypeMirror typeMirror = annotationHelper.extractAnnotationClassParameter(element);
			if (typeMirror == null) {
				typeMirror = param.asType();
				typeMirror = getProcessingEnvironment().getTypeUtils().erasure(typeMirror);
			}
			typeQualifiedName = typeMirror.toString();

		}

		AbstractJClass injectedClass = getJClass(annotationHelper.generatedClassQualifiedNameFromQualifiedName(typeQualifiedName));
		JInvocation beanInstance = injectedClass.staticInvoke(EBeanHolder.GET_INSTANCE_METHOD_NAME).arg(holder.getContextRef());

		TypeElement declaredEBean = getProcessingEnvironment().getElementUtils().getTypeElement(typeQualifiedName);
		if (declaredEBean != null) {
			EBean annotation = adiHelper.getAnnotation(declaredEBean, EBean.class);
			if (annotation.scope() == EBean.Scope.Default) {
				beanInstance.arg(holder.getRootFragmentRef());
			}
		}

		IJStatement assignment = fieldRef.assign(beanInstance);
		if (param.getKind() == ElementKind.FIELD) {
			boolean hasNonConfigurationInstanceAnnotation = element.getAnnotation(NonConfigurationInstance.class) != null;
			if (hasNonConfigurationInstanceAnnotation) {
				JConditional conditional = targetBlock._if(fieldRef.eq(_null()));
				conditional._then().add(assignment);
				assignment = conditional;
			}
		}

		targetBlock.add(assignment);
	}

	@Override
	public void validateEnclosingElement(Element element, ElementValidation valid) {
		validatorHelper.enclosingElementHasEnhancedComponentAnnotation(element, valid);
	}

}
