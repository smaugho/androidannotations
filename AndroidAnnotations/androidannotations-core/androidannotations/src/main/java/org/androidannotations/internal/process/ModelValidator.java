/**
 * Copyright (C) 2010-2016 eBusiness Information, Excilys Group
 * Copyright (C) 2016-2017 the AndroidAnnotations project
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
package org.androidannotations.internal.process;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.ElementValidation;
import org.androidannotations.annotations.Bean;
import org.androidannotations.handler.AnnotationHandler;
import org.androidannotations.helper.ADIHelper;
import org.androidannotations.internal.model.AnnotationElements;
import org.androidannotations.internal.model.AnnotationElementsHolder;
import org.androidannotations.logger.Logger;
import org.androidannotations.logger.LoggerFactory;

import com.dspot.declex.annotation.Export;
import com.dspot.declex.annotation.External;
import com.dspot.declex.annotation.ExternalPopulate;
import com.dspot.declex.annotation.ExternalRecollect;
import com.dspot.declex.annotation.Model;
import com.dspot.declex.annotation.Populate;
import com.dspot.declex.annotation.Recollect;
import com.dspot.declex.helper.FilesCacheHelper;
import com.dspot.declex.util.TypeUtils;
import com.dspot.declex.util.TypeUtils.ClassInformation;
import com.dspot.declex.wrapper.element.VirtualElement;

public class ModelValidator {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelValidator.class);
	private AndroidAnnotationsEnvironment environment;

	private Map<String, Map<Element, Element>> beanAndModelParents;
	private List<VirtualElement> paramsWaitingForItsVirtualMethod;
	
	public ModelValidator(AndroidAnnotationsEnvironment environment) {
		this.environment = environment;
	}

	public AnnotationElements validate(AnnotationElements extractedModel, AnnotationElementsHolder validatingHolder) {

		LOGGER.info("Validating elements");

		/*
		 * We currently do not validate the elements on the ancestors, assuming
		 * they've already been validated. This also means some checks such as
		 * unique ids might not be check all situations.
		 */
		
		final ADIHelper adiHelper = new ADIHelper(environment);
		final Map<Element, Set<Element>> virtualElementsMap = new HashMap<>();		
		
		//Beans and Models pair <parent, reference where the bean is used>
		beanAndModelParents = new HashMap<>();
		paramsWaitingForItsVirtualMethod = new LinkedList<>();
		
		Set<Element> beanAndModelAnnotatedElements = null;
		
		for (AnnotationHandler annotationHandler : environment.getHandlers()) {
			
			if (!annotationHandler.isEnabled()) {
				continue;
			}
			
			String validatorSimpleName = annotationHandler.getClass().getSimpleName();
			String annotationName = annotationHandler.getTarget();

			Set<? extends Element> annotatedElements = extractedModel.getRootAnnotatedElements(annotationName);

			Set<Element> validatedAnnotatedElements = new LinkedHashSet<>();

			validatingHolder.putRootAnnotatedElements(annotationName, validatedAnnotatedElements);

			if (!annotatedElements.isEmpty()) {
				LOGGER.debug("Validating with {}: {}", validatorSimpleName, annotatedElements);
			}
			
			final FilesCacheHelper filesCacheHelper = FilesCacheHelper.getInstance();
			
			for (Element realAnnotatedElement : annotatedElements) {
				try {
					
					final boolean isExported = adiHelper.hasAnnotation(realAnnotatedElement, Export.class);
					if (isExported && !(realAnnotatedElement instanceof VirtualElement)) {
						if (!annotationHandler.getTarget().equals(Export.class.getCanonicalName())
							&& !realAnnotatedElement.getKind().isField()) {
							continue;
						}						
					}
					
					Set<Element> allAnnotatedElements = new HashSet<>();					
					allAnnotatedElements.add(realAnnotatedElement);
					
					final boolean hasExternal = adiHelper.hasAnnotation(realAnnotatedElement, External.class);
					final boolean hasExternalPopulate = adiHelper.hasAnnotation(realAnnotatedElement, ExternalPopulate.class);
					final boolean hasExternalRecollect = adiHelper.hasAnnotation(realAnnotatedElement, ExternalRecollect.class);
					
					if (annotationHandler.getTarget().equals(External.class.getCanonicalName()) 
						&& realAnnotatedElement.getKind().equals(ElementKind.CLASS)) {
						//@External is not processed when it is over a CLASS
						//this is intended to be used only for ADI purposes
						continue;
					}
					
					//This kind of annotation will be processed only in the parents (containers of Beans and Models)
					if ((hasExternal || hasExternalPopulate || hasExternalRecollect)
						&& !realAnnotatedElement.getKind().equals(ElementKind.CLASS)) {
						
						if (beanAndModelAnnotatedElements == null) {
							beanAndModelAnnotatedElements = new HashSet<>();
							for (Element element : extractedModel.getRootAnnotatedElements(Bean.class.getCanonicalName())) {
								beanAndModelAnnotatedElements.add(element);
							}
							for (Element element : extractedModel.getRootAnnotatedElements(Model.class.getCanonicalName())) {
								beanAndModelAnnotatedElements.add(element);
							}
							
							for (Element element : beanAndModelAnnotatedElements) {
								final Element rootElement = TypeUtils.getRootElement(element);
								ClassInformation info = TypeUtils.getClassInformation(element, environment);
								
								Map<Element, Element> parents = beanAndModelParents.get(info.generatorClassName);
								if (parents == null) {
									parents = new HashMap<>();
									beanAndModelParents.put(info.generatorClassName, parents);
								}
								
								parents.put(rootElement, element);
							}
						}
						
						if (!virtualElementsMap.containsKey(realAnnotatedElement)) {							
							final Element rootElement = TypeUtils.getRootElement(realAnnotatedElement);
							final String rootElementClass = rootElement.asType().toString();
							
							final Set<Element> virtualElements = new HashSet<>();
							virtualElementsMap.put(realAnnotatedElement, virtualElements);
							
							if (filesCacheHelper.isAncestor(rootElementClass)) {
								
								Set<String> subClasses = filesCacheHelper.getAncestorSubClasses(rootElementClass);
								for (String subClass : subClasses) {
									if (filesCacheHelper.isAncestor(subClass)) continue;
									createVirtualElements(subClass, realAnnotatedElement, virtualElements);
								}					
								
							} else {
								createVirtualElements(rootElementClass, realAnnotatedElement, virtualElements);
							}
								
						}
						
						//Use the virtual element
						if (hasExternal) {
							allAnnotatedElements = virtualElementsMap.get(realAnnotatedElement);
						};
						
						if (hasExternalPopulate) {
							
							//@Populate will be applied in this case only to the virtual elements 
							if (annotationHandler.getTarget().equals(Populate.class.getCanonicalName())) {
								allAnnotatedElements = virtualElementsMap.get(realAnnotatedElement);
							}
							
							//@ExternalPopulate is applied to the real and virtual elements as well
							if (annotationHandler.getTarget().equals(ExternalPopulate.class.getCanonicalName())) {
								allAnnotatedElements.addAll(virtualElementsMap.get(realAnnotatedElement));
							}
						}
						
						if (hasExternalRecollect) {
							//@Recollect will be applied in this case only to the virtual elements 
							if (annotationHandler.getTarget().equals(Recollect.class.getCanonicalName())) {
								allAnnotatedElements = virtualElementsMap.get(realAnnotatedElement);
							}
							
							//@ExternalRecollect is applied to the real and virtual elements as well
							if (annotationHandler.getTarget().equals(ExternalRecollect.class.getCanonicalName())) {
								allAnnotatedElements.addAll(virtualElementsMap.get(realAnnotatedElement));
							}
						}
					}
					
					for (Element annotatedElement : allAnnotatedElements) {
						ElementValidation elementValidation = annotationHandler.validate(annotatedElement);

						AnnotationMirror annotationMirror = elementValidation.getAnnotationMirror();
						for (ElementValidation.Error error : elementValidation.getErrors()) {
							LOGGER.error(error.getMessage(), error.getElement(), annotationMirror);
						}

						for (String warning : elementValidation.getWarnings()) {
							LOGGER.warn(warning, elementValidation.getElement(), annotationMirror);
						}

						if (elementValidation.isValid()) {
							validatedAnnotatedElements.add(annotatedElement);
						} else {
							LOGGER.warn("Element {} invalidated by {}", annotatedElement, annotationName);
						}
					}			
					
				} catch (Throwable e) {
					LOGGER.error(
							"Internal crash while validating element {} with annotation {}. \n"
							 + "Please report this in " 
							 + annotationHandler.getAndroidAnnotationPlugin().getIssuesUrl(), 
							 realAnnotatedElement, annotationName
						 );
					LOGGER.error("Crash Report: {}", e);
				}
			}
		}

		return validatingHolder;
	}
	
	private void createVirtualElements(String beanElementClass, Element realAnnotatedElement, Set<Element> virtualElements) {
		
		Set<Class<? extends Annotation>> adiForElement = environment.getADIOnElement(realAnnotatedElement);
		
		if (beanAndModelParents.containsKey(beanElementClass)) {
			for (Entry<Element, Element> parentReference : beanAndModelParents.get(beanElementClass).entrySet()) {
				VirtualElement virtualElement = VirtualElement.from(realAnnotatedElement);
				
				if (realAnnotatedElement.getKind() == ElementKind.PARAMETER) {
					VirtualElement virtualParent = null;
					
					List<Element> rootElementVirtualEnclosed = VirtualElement.getVirtualEnclosedElements(parentReference.getKey());
					for (Element elem : rootElementVirtualEnclosed) {
						if (((VirtualElement)elem).getElement().equals(realAnnotatedElement.getEnclosingElement())) {
							virtualParent = (VirtualElement) elem;
							break;
						}
					}
					
					if (virtualParent == null) {
						paramsWaitingForItsVirtualMethod.add(virtualElement);
						
						VirtualElement temporalParent = VirtualElement.from(realAnnotatedElement.getEnclosingElement());
						temporalParent.setTemporal();
						temporalParent.setEnclosingElement(parentReference.getKey());	
						temporalParent.setReference(parentReference.getValue());
						
						virtualElement.setEnclosingElement(temporalParent);
					} else {
						virtualElement.setEnclosingElement(virtualParent);
					}	
				} else {
					
					if (realAnnotatedElement.getKind() == ElementKind.METHOD) {
						int i = 0;
						while (i < paramsWaitingForItsVirtualMethod.size()) {
							VirtualElement param = paramsWaitingForItsVirtualMethod.get(i);
							if (param.getElement().getEnclosingElement().equals(realAnnotatedElement)) {
								paramsWaitingForItsVirtualMethod.remove(i);
								param.setEnclosingElement(virtualElement);
								continue;
							}
							i++;
						}
					}
															
					virtualElement.setEnclosingElement(parentReference.getKey());												
				}
				
				virtualElement.setReference(parentReference.getValue());
							
				//Add ADI to virtual elements
				for (Class<? extends Annotation> annotation : adiForElement) {
					environment.addAnnotationToADI(virtualElement, annotation);
				}			
				
				virtualElements.add(virtualElement);
				
			}
		}
	}
	
}
