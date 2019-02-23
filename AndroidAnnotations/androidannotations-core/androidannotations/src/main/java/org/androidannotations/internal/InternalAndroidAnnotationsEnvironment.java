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
package org.androidannotations.internal;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.Option;
import org.androidannotations.handler.AnnotationHandler;
import org.androidannotations.handler.GeneratingAnnotationHandler;
import org.androidannotations.helper.AndroidManifest;
import org.androidannotations.helper.ClassesHolder;
import org.androidannotations.holder.GeneratedClassHolder;
import org.androidannotations.internal.model.AnnotationElements;
import org.androidannotations.internal.process.ProcessHolder;
import org.androidannotations.plugin.AndroidAnnotationsPlugin;
import org.androidannotations.rclass.IRClass;

import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JDefinedClass;

public class InternalAndroidAnnotationsEnvironment implements AndroidAnnotationsEnvironment {

	private final ProcessingEnvironment processingEnvironment;
	private final Options options;
	private final AnnotationHandlers annotationHandlers;

	private List<AndroidAnnotationsPlugin> plugins;

	private IRClass rClass;
	private AndroidManifest androidManifest;

	private AnnotationElements extractedElements;

	private AnnotationElements validatedElements;

	private ProcessHolder processHolder;

	private Map<String, Set<Class<? extends Annotation>>> adiClassForElement = new HashMap<>();
	private Map<String, Set<Annotation>> adiInstanceForElement = new HashMap<>();

	private ClassesHolder classesHolder;

	InternalAndroidAnnotationsEnvironment(ProcessingEnvironment processingEnvironment) {
		this.processingEnvironment = processingEnvironment;
		options = new Options(processingEnvironment);
		annotationHandlers = new AnnotationHandlers();
	}

	public void setPlugins(List<AndroidAnnotationsPlugin> plugins) {
		this.plugins = plugins;

		Map<String, AnnotationHandler<?>> tempAnnotationHandlers = new HashMap<>();
		List<String> sortedAnnotationHandlers = new LinkedList<>();

		// Sort annotationHandlers
		for (AndroidAnnotationsPlugin plugin : plugins) {
			options.addAllSupportedOptions(plugin.getSupportedOptions());

			for (AnnotationHandler<?> annotationHandler : plugin.getHandlers(this)) {
				tempAnnotationHandlers.put(annotationHandler.getTarget(), annotationHandler);
				annotationHandler.setAndroidAnnotationPlugin(plugin);

				if (annotationHandler.getBeforeTarget() != null) {
					int indexForBeforeTarget = sortedAnnotationHandlers.indexOf(annotationHandler.getBeforeTarget());
					if (indexForBeforeTarget != -1) {
						sortedAnnotationHandlers.add(indexForBeforeTarget, annotationHandler.getTarget());
						continue;
					}
				}

				sortedAnnotationHandlers.add(annotationHandler.getTarget());
			}
		}

		for (String target : sortedAnnotationHandlers) {
			annotationHandlers.add(tempAnnotationHandlers.get(target));
		}
	}

	public void setAndroidEnvironment(IRClass rClass, AndroidManifest androidManifest) {
		this.rClass = rClass;
		this.androidManifest = androidManifest;
	}

	public void setExtractedElements(AnnotationElements extractedElements) {
		this.extractedElements = extractedElements;
	}

	public void setValidatedElements(AnnotationElements validatedElements) {
		this.validatedElements = validatedElements;
	}

	public void setProcessHolder(ProcessHolder processHolder) {
		this.processHolder = processHolder;
	}

	public void setClassesHolder(ClassesHolder classesHolder) {
		this.classesHolder = classesHolder;
	}

	@Override
	public ProcessingEnvironment getProcessingEnvironment() {
		return processingEnvironment;
	}

	@Override
	public Set<String> getSupportedOptions() {
		return options.getSupportedOptions();
	}

	@Override
	public String getOptionValue(Option option) {
		return options.get(option);
	}

	@Override
	public String getOptionValue(String optionKey) {
		return options.get(optionKey);
	}

	@Override
	public boolean getOptionBooleanValue(Option option) {
		return options.getBoolean(option);
	}

	@Override
	public boolean getOptionBooleanValue(String optionKey) {
		return options.getBoolean(optionKey);
	}

	@Override
	public Set<String> getSupportedAnnotationTypes() {
		return annotationHandlers.getSupportedAnnotationTypes();
	}

	@Override
	public List<AnnotationHandler<?>> getHandlers() {
		return annotationHandlers.get();
	}

	@Override
	public List<AnnotationHandler<?>> getDecoratingHandlers() {
		return annotationHandlers.getDecorating();
	}

	@Override
	public List<GeneratingAnnotationHandler<?>> getGeneratingHandlers() {
		return annotationHandlers.getGenerating();
	}

	@Override
	public IRClass getRClass() {
		return rClass;
	}

	@Override
	public AndroidManifest getAndroidManifest() {
		return androidManifest;
	}

	@Override
	public AnnotationElements getExtractedElements() {
		return extractedElements;
	}

	@Override
	public AnnotationElements getValidatedElements() {
		return validatedElements;
	}

	@Override
	public JCodeModel getCodeModel() {
		return classesHolder.codeModel();
	}

	@Override
	public AbstractJClass getJClass(String fullyQualifiedName) {
		return classesHolder.refClass(fullyQualifiedName);
	}

	@Override
	public AbstractJClass getJClass(Class<?> clazz) {
		return classesHolder.refClass(clazz);
	}

	@Override
	public JDefinedClass getDefinedClass(String fullyQualifiedName) {
		return classesHolder.definedClass(fullyQualifiedName);
	}

	@Override
	public GeneratedClassHolder getGeneratedClassHolder(Element element) {
		return processHolder.getGeneratedClassHolder(element);
	}

	@Override
	public ClassesHolder.Classes getClasses() {
		return classesHolder.classes();
	}

	@Override
	public List<Class<? extends Annotation>> getGeneratingAnnotations() {
		return annotationHandlers.getGeneratingAnnotations();
	}

	@Override
	public boolean isAndroidAnnotation(String annotationQualifiedName) {
		return getSupportedAnnotationTypes().contains(annotationQualifiedName);
	}

	@Override
	public List<AndroidAnnotationsPlugin> getPlugins() {
		return plugins;
	}

	@Override
	public ProcessHolder getProcessHolder() {
		return processHolder;
	}

	@Override
	public ClassesHolder getClassesHolder() {
		return classesHolder;
	}

	@Override
	public Set<Class<? extends Annotation>> getADIOnElement(Element element) {
		String elementName = getElementName(element);

		if (!adiClassForElement.containsKey(elementName)) {
			return Collections.emptySet();
		}

		return adiClassForElement.get(elementName);
	}

	@Override
	public Set<Class<? extends Annotation>> getADIForClass(String clazz) {
		if (!adiClassForElement.containsKey(clazz)) {
			return Collections.emptySet();
		}
		return adiClassForElement.get(clazz);
	}

	@Override
	public Set<Annotation> getADIAnnotationsOnElement(Element element, Class<? extends Annotation> annotationClass) {
		String elementName = getElementName(element);

		if (!adiClassForElement.containsKey(elementName)) {
			return null;
		}

		return adiInstanceForElement.get(elementName);
	}

	@Override
	public void addAnnotationToADI(Element element, Object annotation) {
		this.addAnnotationToADI(getElementName(element), annotation);
	}

	@SuppressWarnings("unchecked")
	@Override
	public void addAnnotationToADI(String name, Object annotation) {

		Set<Class<? extends Annotation>> adiClassAnnotations = adiClassForElement.get(name);
		if (adiClassAnnotations == null) {
			adiClassAnnotations = new HashSet<>();
			adiClassForElement.put(name, adiClassAnnotations);
		}

		Set<Annotation> adiInstanceAnnotations = adiInstanceForElement.get(name);
		if (adiInstanceAnnotations == null) {
			adiInstanceAnnotations = new HashSet<>();
			adiInstanceForElement.put(name, adiInstanceAnnotations);
		}

		if (annotation instanceof Annotation) {
			adiClassAnnotations.add(((Annotation) annotation).getClass());
			adiInstanceAnnotations.add((Annotation) annotation);
		}

		if (annotation instanceof Class) {
			adiClassAnnotations.add((Class<? extends Annotation>) annotation);
			adiInstanceAnnotations.add(annotationFrom((Class<? extends Annotation>) annotation));
		}

	}

	private String getElementName(Element element) {
		String name = null;

		Element enclosingElement = element;
		do {
			if (name == null) {
				name = enclosingElement.toString();
			} else {
				name = enclosingElement.toString() + ":" + name;
			}

			enclosingElement = enclosingElement.getEnclosingElement();
		} while (!enclosingElement.getKind().equals(ElementKind.PACKAGE));

		return name;
	}

	@SuppressWarnings("unchecked")
	private static <A extends Annotation> A annotationFrom(Class<A> annotation) {
		return (A) Proxy.newProxyInstance(annotation.getClassLoader(), new Class[] { annotation }, new ProxyAnnotation());
	}

	static class ProxyAnnotation implements InvocationHandler {

		private static final Method OBJECT_EQUALS = getObjectMethod("equals", Object.class);
		private static final Method OBJECT_HASHCODE = getObjectMethod("hashCode");

		public Object invoke(Object proxy, Method method, Object[] args) {

			if (OBJECT_EQUALS == method) {
				return this.equals(args[0]);
			}

			if (OBJECT_HASHCODE == method) {
				return this.hashCode();
			}

			if (method.getName().equals("hashCode") && (args == null || args.length == 0)) {
				return this.hashCode();
			}

			return method.getDefaultValue();
		}

		private static Method getObjectMethod(String name, Class<?>... types) {
			try {
				// null 'types' is OK.
				return Object.class.getMethod(name, types);
			} catch (NoSuchMethodException e) {
				throw new IllegalArgumentException(e);
			}
		}
	}
}
