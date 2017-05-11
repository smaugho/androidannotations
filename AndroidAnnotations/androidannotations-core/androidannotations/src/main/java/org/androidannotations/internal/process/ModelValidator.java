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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.ElementValidation;
import org.androidannotations.annotations.Bean;
import org.androidannotations.handler.AnnotationHandler;
import org.androidannotations.helper.ADIHelper;
import org.androidannotations.internal.model.AnnotationElements;
import org.androidannotations.internal.model.AnnotationElementsHolder;
import org.androidannotations.logger.Logger;
import org.androidannotations.logger.LoggerFactory;

import com.dspot.declex.api.external.External;
import com.dspot.declex.api.external.ExternalPopulate;
import com.dspot.declex.api.model.Model;
import com.dspot.declex.api.viewsinjection.Populate;
import com.dspot.declex.helper.FilesCacheHelper;
import com.dspot.declex.util.TypeUtils;
import com.dspot.declex.util.TypeUtils.ClassInformation;
import com.dspot.declex.util.element.VirtualElement;

public class ModelValidator {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelValidator.class);
	private AndroidAnnotationsEnvironment environment;

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
		
		//Beans and Models pair <parent, reference>
		final Map<String, Map<Element, Element>> beanAndModelParents = new HashMap<>();
		
		Set<Element> beanAndModelAnnotatedElements = null;
		Set<Element> externalPopulateElements = new HashSet<>();
		
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
					
					Set<Element> allAnnotatedElements = new HashSet<>();
					allAnnotatedElements.add(realAnnotatedElement);
					
					final boolean hasExternal = adiHelper.hasAnnotation(realAnnotatedElement, External.class);
					final boolean hasExternalPopulate = adiHelper.hasAnnotation(realAnnotatedElement, ExternalPopulate.class);
					
					//This kind of annotation will be processed only in the parents (containers of Beans and Models)
					if (hasExternal || hasExternalPopulate) {
						
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
															
									if (beanAndModelParents.containsKey(subClass)) {
										for (Entry<Element, Element> parentReference : beanAndModelParents.get(subClass).entrySet()) {
											VirtualElement virtualElement = VirtualElement.from(realAnnotatedElement);
											virtualElement.setEnclosingElement(parentReference.getKey());
											virtualElement.setReference(parentReference.getValue());
											virtualElements.add(virtualElement);					
											
											if (hasExternalPopulate) {
												externalPopulateElements.add(virtualElement);
											}
											
										}
									}
								}					
							} else {
								
								if (beanAndModelParents.containsKey(rootElementClass)) {
									for (Entry<Element, Element> parentReference : beanAndModelParents.get(rootElementClass).entrySet()) {
										VirtualElement virtualElement = VirtualElement.from(realAnnotatedElement);
										virtualElement.setEnclosingElement(parentReference.getKey());
										virtualElement.setReference(parentReference.getValue());
										virtualElements.add(virtualElement);
										
										if (hasExternalPopulate) {
											externalPopulateElements.add(virtualElement);
										}
									}
								}								
							}							
						}
						
						//Use the virtual element
						if (hasExternal) {
							allAnnotatedElements = virtualElementsMap.get(realAnnotatedElement);
						};
						
						if (hasExternalPopulate) {
							
							//@Populate will be applied in this case only to the virtual elements 
							if (annotationHandler.getTarget().equals(Populate.class.getCanonicalName())) {
								//allAnnotatedElements = virtualElementsMap.get(realAnnotatedElement);
								//TODO: ignore
								continue;
							}
							
							//@ExternalPopulate is applied to the real and virtual elements as well
							if (annotationHandler.getTarget().equals(ExternalPopulate.class.getCanonicalName())) {
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
}
