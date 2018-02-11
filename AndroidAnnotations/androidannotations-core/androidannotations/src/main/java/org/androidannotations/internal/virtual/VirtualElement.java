package org.androidannotations.internal.virtual;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ElementVisitor;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import com.dspot.declex.util.TypeUtils;
import com.helger.jcodemodel.IJExpression;

public class VirtualElement implements Element {

	private Element reference;
	private IJExpression referenceExpression;
	private Element containerElement;
	
	protected Element element;
	private Element enclosingElement;
	
	/**
	 * Used in the process of linking Virtual Elements
	 */
	private boolean temporal;
	
	//<Enclosing Element, List<Virtual Enclosed Elements>>
	private static Map<Element, List<Element>> mapVirtualEnclosedElements = new HashMap<>();
	
	public static List<Element> getVirtualEnclosedElements(Element enclosingElement) {
		if (mapVirtualEnclosedElements.containsKey(enclosingElement)) {
			return mapVirtualEnclosedElements.get(enclosingElement);
		}
		
		return Collections.emptyList();
	}
	
	public static VirtualElement from(Element element) {
		
		if (element instanceof ExecutableElement) {
			return new VirtualExecutableElement((ExecutableElement) element);
		}
		
		if (element instanceof VariableElement) {
			return new VirtualVariableElement((VariableElement)element);
		}
		
		return new VirtualElement(element);
	}
	
	VirtualElement(Element element) {
		this.element = element;
		this.containerElement = TypeUtils.getRootElement(element);
	}
	
	public void setTemporal() {
		this.temporal = true;
	}
	
	public boolean isTemporal() {
		return this.temporal;
	}
	
	public Element getReference() {
		return reference;
	}
	
	public void setReference(Element reference) {
		this.reference = reference;
	}
		
	public IJExpression getReferenceExpression() {
		return referenceExpression;
	}
	
	public void setReferenceExpression(IJExpression referenceExpression) {
		this.referenceExpression = referenceExpression;
	}
	
	public Element getContainerElement() {
		return containerElement;
	}
	
	public Element getElement() {
		return element;
	}
	
	@Override
	public <A extends Annotation> A[] getAnnotationsByType(
			Class<A> annotationType) {
		return element.getAnnotationsByType(annotationType);
	}

	@Override
	public TypeMirror asType() {
		return element.asType();
	}

	@Override
	public ElementKind getKind() {
		return element.getKind();
	}

	@Override
	public Set<Modifier> getModifiers() {
		return element.getModifiers();
	}

	@Override
	public Name getSimpleName() {
		return element.getSimpleName();
	}

	@Override
	public Element getEnclosingElement() {
		return enclosingElement;
	}
	
	public void setEnclosingElement(Element enclosingElement) {
		
		if (!isTemporal()) {
			List<Element> enclosedElements = mapVirtualEnclosedElements.get(enclosingElement);
			if (enclosedElements == null) {
				enclosedElements = new LinkedList<>();
				mapVirtualEnclosedElements.put(enclosingElement, enclosedElements);
			}
			
			enclosedElements.add(this);
		}
		
		this.enclosingElement = enclosingElement;
	}

	@Override
	public List<? extends Element> getEnclosedElements() {
		return element.getEnclosedElements();
	}

	@Override
	public List<? extends AnnotationMirror> getAnnotationMirrors() {
		return element.getAnnotationMirrors();
	}

	@Override
	public <A extends Annotation> A getAnnotation(Class<A> annotationType) {
		return element.getAnnotation(annotationType);
	}

	@Override
	public <R, P> R accept(ElementVisitor<R, P> v, P p) {
		return element.accept(v, p);
	}
		
	@Override
	public String toString() {
		return element.toString();
	}


}