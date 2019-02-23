package org.androidannotations.helper;

import java.lang.annotation.Annotation;
import java.util.Set;

import javax.lang.model.element.Element;

import org.androidannotations.AndroidAnnotationsEnvironment;

public class ADIHelper {

	private AndroidAnnotationsEnvironment environment;

	public ADIHelper(AndroidAnnotationsEnvironment environment) {
		this.environment = environment;
	}

	public boolean hasAnnotation(Element element, Class<? extends Annotation> annotation) {
		Annotation annotated = element.getAnnotation(annotation);
		if (annotated != null) {
			return true;
		}

		Set<Class<? extends Annotation>> adi = environment.getADIOnElement(element);
		return adi.contains(annotation);
	}

	@SuppressWarnings("unchecked")
	public <T extends Annotation> T getAnnotation(Element element, Class<T> annotation) {
		if (!hasAnnotation(element, annotation)) {
			return null;
		}

		T annotated = element.getAnnotation(annotation);
		if (annotated != null) {
			return annotated;
		}

		Set<Annotation> adiAnnotations = environment.getADIAnnotationsOnElement(element, annotation);
		for (Annotation adiAnnotation : adiAnnotations) {
			if (annotation.isInstance(adiAnnotation)) {
				return (T) adiAnnotation;
			}
		}

		return null;
	}

}
