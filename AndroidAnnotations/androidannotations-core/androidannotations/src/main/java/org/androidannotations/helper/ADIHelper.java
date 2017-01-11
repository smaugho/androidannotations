package org.androidannotations.helper;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
		if (annotated != null) return true;
		
		Set<Class<? extends Annotation>> adi = environment.getADIOnElement(element);
		return adi.contains(annotation);
	}
	
	public <T extends Annotation> T getAnnotation(Element element, Class<T> annotation) {
		if (!hasAnnotation(element, annotation)) return null;
		
		T annotated = element.getAnnotation(annotation);
		if (annotated != null) return annotated;
		
		return annotationFrom(annotation);
	}
	
	
	@SuppressWarnings("unchecked")
	private static <A extends Annotation> A annotationFrom(Class<A> annotation) {
		return (A) Proxy.newProxyInstance(
				annotation.getClassLoader(),
				new Class[] { annotation }, 
				new ProxyAnnotation()
			);
	}
	
	static class ProxyAnnotation implements InvocationHandler {

		public Object invoke(Object proxy, Method method, Object[] args)
				throws Throwable {
			return method.getDefaultValue();
		}
	}
	
}
