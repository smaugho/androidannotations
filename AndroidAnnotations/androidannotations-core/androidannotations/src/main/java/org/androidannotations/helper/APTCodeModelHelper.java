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
package org.androidannotations.helper;

import static com.helger.jcodemodel.JExpr._new;
import static com.helger.jcodemodel.JExpr.lit;
import static org.androidannotations.helper.ModelConstants.classSuffix;

import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.annotation.Inherited;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.ErrorType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Types;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.annotations.EBean;
import org.androidannotations.holder.EBeanHolder;
import org.androidannotations.holder.GeneratedClassHolder;
import org.androidannotations.internal.helper.AnnotationParamExtractor;

import com.helger.jcodemodel.AbstractJAnnotationValue;
import com.helger.jcodemodel.AbstractJClass;
import com.helger.jcodemodel.AbstractJType;
import com.helger.jcodemodel.IJAnnotatable;
import com.helger.jcodemodel.IJExpression;
import com.helger.jcodemodel.IJFormatter;
import com.helger.jcodemodel.IJGenerifiable;
import com.helger.jcodemodel.IJStatement;
import com.helger.jcodemodel.JAnnotationArrayMember;
import com.helger.jcodemodel.JAnnotationUse;
import com.helger.jcodemodel.JBlock;
import com.helger.jcodemodel.JCodeModel;
import com.helger.jcodemodel.JDefinedClass;
import com.helger.jcodemodel.JExpr;
import com.helger.jcodemodel.JInvocation;
import com.helger.jcodemodel.JMethod;
import com.helger.jcodemodel.JMod;
import com.helger.jcodemodel.JTypeVar;
import com.helger.jcodemodel.JVar;
import com.helger.jcodemodel.SourcePrintWriter;
import com.helger.jcodemodel.writer.JCMWriter;
import com.helger.jcodemodel.writer.JFormatter;

public class APTCodeModelHelper {

	private static final List<String> IGNORED_ANNOTATIONS = Collections.singletonList("kotlin.Metadata");

	private AndroidAnnotationsEnvironment environment;
	private CompilationTreeHelper compilationTreeHelper;

	public APTCodeModelHelper(AndroidAnnotationsEnvironment environment) {
		this.environment = environment;
		this.compilationTreeHelper = new CompilationTreeHelper(environment);
	}

	public AbstractJClass elementTypeToJClass(Element element) {
		return elementTypeToJClass(element, false);
	}

	public AbstractJClass elementTypeToJClass(Element element, boolean useFirstTypeArgument) {

		TypeMirror type = element instanceof ExecutableElement ? ((ExecutableElement) element).getReturnType() : element.asType();

		if (useFirstTypeArgument && type instanceof DeclaredType) {
			if (((DeclaredType) type).getTypeArguments().size() > 0) {
				type = ((DeclaredType) type).getTypeArguments().get(0);
			}
		}

		return typeMirrorToJClass(type, element, Collections.<String, TypeMirror> emptyMap());
	}

	public String typeStringToClassName(String typeName, Element referenceElement) {
		return compilationTreeHelper.getClassNameFromCompilationUnitImports(typeName, referenceElement);
	}

	public AbstractJClass typeMirrorToJClass(TypeMirror type) {
		return typeMirrorToJClass(type, null, Collections.<String, TypeMirror> emptyMap());
	}

	public AbstractJClass typeMirrorToJClass(TypeMirror type, Element referenceElement) {
		return typeMirrorToJClass(type, referenceElement, Collections.<String, TypeMirror> emptyMap());
	}

	private AbstractJClass typeMirrorToJClass(TypeMirror type, Map<String, TypeMirror> substitute) {
		return typeMirrorToJClass(type, null, substitute);
	}

	private AbstractJClass typeMirrorToJClass(TypeMirror type, Element referenceElement, Map<String, TypeMirror> substitute) {
		if (type instanceof ErrorType) {
			return environment.getJClass(typeStringToClassName(type.toString(), referenceElement));
		} else if (type instanceof DeclaredType) {
			return typeMirrorToJClass((DeclaredType) type, referenceElement, substitute);
		} else if (type instanceof WildcardType) {
			return typeMirrorToJClass((WildcardType) type, referenceElement, substitute);
		} else if (type instanceof ArrayType) {
			return typeMirrorToJClass((ArrayType) type, referenceElement, substitute);
		} else {

			TypeMirror substituted = substitute.get(type.toString());
			if (substituted != null && type != substituted) {
				return typeMirrorToJClass(substituted, referenceElement, substitute);
			}

			if (type.getKind().isPrimitive() || type.getKind() == TypeKind.VOID || type.getKind() == TypeKind.TYPEVAR) {
				return environment.getJClass(type.toString());
			}

			return environment.getJClass(typeStringToClassName(type.toString(), referenceElement));

		}
	}

	private AbstractJClass typeMirrorToJClass(DeclaredType declaredType, Element referenceElement, Map<String, TypeMirror> substitute) {
		String declaredTypeName = declaredType.asElement().toString();

		AbstractJClass declaredClass = environment.getJClass(declaredTypeName);

		List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();

		List<AbstractJClass> typeArgumentJClasses = new ArrayList<>();
		for (TypeMirror typeArgument : typeArguments) {
			typeArgumentJClasses.add(typeMirrorToJClass(typeArgument, referenceElement, substitute));
		}
		if (typeArgumentJClasses.size() > 0) {
			declaredClass = declaredClass.narrow(typeArgumentJClasses);
		}

		return declaredClass;
	}

	private AbstractJClass typeMirrorToJClass(WildcardType wildcardType, Element referenceElement, Map<String, TypeMirror> substitute) {
		TypeMirror bound = wildcardType.getExtendsBound();
		if (bound == null) {
			bound = wildcardType.getSuperBound();
			if (bound == null) {
				return environment.getClasses().OBJECT.wildcardExtends();
			}
			return typeMirrorToJClass(bound, referenceElement, substitute).wildcardSuper();
		}

		TypeMirror extendsBound = wildcardType.getExtendsBound();

		if (extendsBound == null) {
			return environment.getClasses().OBJECT.wildcardExtends();
		} else {
			return typeMirrorToJClass(extendsBound, referenceElement, substitute).wildcardExtends();
		}
	}

	private AbstractJClass typeMirrorToJClass(ArrayType arrayType, Element referenceElement, Map<String, TypeMirror> substitute) {
		AbstractJClass refClass = typeMirrorToJClass(arrayType.getComponentType(), referenceElement, substitute);
		return refClass.array();
	}

	private Map<String, TypeMirror> getActualTypes(Types typeUtils, DeclaredType baseClass, TypeMirror annotatedClass) {
		List<TypeMirror> superTypes = new ArrayList<>();
		superTypes.add(annotatedClass);
		while (!superTypes.isEmpty()) {
			TypeMirror x = superTypes.remove(0);
			if (typeUtils.isSameType(typeUtils.erasure(x), typeUtils.erasure(baseClass))) {
				DeclaredType type = (DeclaredType) x;
				Map<String, TypeMirror> actualTypes = new HashMap<>();
				for (int i = 0; i < type.getTypeArguments().size(); i++) {
					TypeMirror actualArg = type.getTypeArguments().get(i);
					TypeMirror formalArg = baseClass.getTypeArguments().get(i);
					if (!typeUtils.isSameType(actualArg, formalArg)) {
						actualTypes.put(formalArg.toString(), actualArg);
					}
				}
				return actualTypes;
			}
			superTypes.addAll(typeUtils.directSupertypes(x));
		}
		return Collections.emptyMap();
	}

	public List<AbstractJClass> typeBoundsToJClass(List<? extends TypeMirror> bounds) {
		return typeBoundsToJClass(bounds, Collections.<String, TypeMirror> emptyMap());
	}

	private List<AbstractJClass> typeBoundsToJClass(List<? extends TypeMirror> bounds, Map<String, TypeMirror> actualTypes) {
		if (bounds.isEmpty()) {
			return Collections.singletonList(environment.getClasses().OBJECT);
		} else {
			List<AbstractJClass> jClassBounds = new ArrayList<>();

			for (TypeMirror bound : bounds) {
				jClassBounds.add(typeMirrorToJClass(bound, actualTypes));
			}
			return jClassBounds;
		}
	}

	private void addTypeBounds(IJGenerifiable generifiable, List<AbstractJClass> bounds, String name) {
		JTypeVar typeVar = null;

		for (AbstractJClass bound : bounds) {
			if (typeVar == null) {
				typeVar = generifiable.generify(name, bound);
			} else {
				typeVar.bound(bound);
			}
		}
	}

	public JMethod overrideMethod(ExecutableElement executableElement, JDefinedClass holderClass, TypeElement holderClassElement) {

		String methodName = executableElement.getSimpleName().toString();

		DeclaredType baseClass = (DeclaredType) executableElement.getEnclosingElement().asType();

		Types typeUtils = environment.getProcessingEnvironment().getTypeUtils();

		Map<String, TypeMirror> actualTypes = getActualTypes(typeUtils, baseClass, holderClassElement.asType());
		Map<String, List<AbstractJClass>> methodTypes = new LinkedHashMap<>();

		for (TypeParameterElement typeParameter : executableElement.getTypeParameters()) {
			List<? extends TypeMirror> bounds = typeParameter.getBounds();

			List<AbstractJClass> addedBounds = typeBoundsToJClass(bounds, actualTypes);
			methodTypes.put(typeParameter.toString(), addedBounds);
		}

		AbstractJClass returnType = typeMirrorToJClass(executableElement.getReturnType(), executableElement);
		int modifier = elementVisibilityModifierToJMod(executableElement);
		JMethod method = holderClass.method(modifier, returnType, methodName);

		method.annotate(Override.class);

		for (Map.Entry<String, List<AbstractJClass>> typeDeclaration : methodTypes.entrySet()) {
			List<AbstractJClass> bounds = typeDeclaration.getValue();
			addTypeBounds(method, bounds, typeDeclaration.getKey());
		}

		int i = 0;
		for (VariableElement parameter : executableElement.getParameters()) {
			boolean varParam = i == executableElement.getParameters().size() - 1 && executableElement.isVarArgs();
			addParamToMethod(method, parameter, JMod.FINAL, actualTypes, varParam, false);
			i++;
		}

		for (TypeMirror superThrownType : executableElement.getThrownTypes()) {
			AbstractJClass thrownType = typeMirrorToJClass(superThrownType, executableElement, actualTypes);
			method._throws(thrownType);
		}

		return method;
	}

	public JMethod overrideAnnotatedMethod(ExecutableElement executableElement, GeneratedClassHolder holder) {
		return overrideAnnotatedMethod(executableElement, holder, false);
	}

	public JMethod overrideAnnotatedMethod(ExecutableElement executableElement, GeneratedClassHolder holder, boolean checkForAction) {
		return overrideAnnotatedMethod(executableElement, holder, checkForAction, true);
	}

	public JMethod overrideAnnotatedMethod(ExecutableElement executableElement, GeneratedClassHolder holder, boolean checkForAction, boolean placeOverrideAndCallSuper) {
		TypeMirror annotatedClass = holder.getAnnotatedElement().asType();
		DeclaredType baseClass = (DeclaredType) executableElement.getEnclosingElement().asType();

		Types typeUtils = environment.getProcessingEnvironment().getTypeUtils();

		Map<String, TypeMirror> actualTypes = getActualTypes(typeUtils, baseClass, annotatedClass);
		Map<String, List<AbstractJClass>> methodTypes = new LinkedHashMap<>();

		for (TypeParameterElement typeParameter : executableElement.getTypeParameters()) {
			List<? extends TypeMirror> bounds = typeParameter.getBounds();

			List<AbstractJClass> addedBounds = typeBoundsToJClass(bounds, actualTypes);
			methodTypes.put(typeParameter.toString(), addedBounds);
		}

		actualTypes.keySet().removeAll(methodTypes.keySet());

		JMethod existingMethod = findAlreadyGeneratedMethod(executableElement, holder, checkForAction);
		if (existingMethod != null) {
			return existingMethod;
		}

		String methodName = executableElement.getSimpleName().toString();
		if (checkForAction && !methodName.startsWith("$")) {
			methodName = "$" + methodName;
		}

		if (!checkForAction && methodName.startsWith("$")) {
			methodName = methodName.substring(1);
		}

		AbstractJClass returnType = typeMirrorToJClass(executableElement.getReturnType(), executableElement, actualTypes);
		int modifier = elementVisibilityModifierToJMod(executableElement);
		JMethod method = holder.getGeneratedClass().method(modifier, returnType, methodName);

		List<? extends AnnotationMirror> annotations = executableElement.getAnnotationMirrors();

		if (checkForAction) {

			AnnotationMirror overrideAnnotation = null;
			for (AnnotationMirror annotation : annotations) {
				if (annotation.toString().equals("@" + Override.class.getCanonicalName())) {
					overrideAnnotation = annotation;
					break;
				}
			}

			if (overrideAnnotation != null) {
				annotations = new ArrayList<>(annotations);
				annotations.remove(overrideAnnotation);
			}

		}
		copyNonAAAnnotations(method, annotations);

		if (placeOverrideAndCallSuper && !checkForAction && !hasAnnotation(method, Override.class)) {
			method.annotate(Override.class);
		}

		for (Map.Entry<String, List<AbstractJClass>> typeDeclaration : methodTypes.entrySet()) {
			List<AbstractJClass> bounds = typeDeclaration.getValue();
			addTypeBounds(method, bounds, typeDeclaration.getKey());
		}

		int i = 0;
		for (VariableElement parameter : executableElement.getParameters()) {
			boolean varParam = i == executableElement.getParameters().size() - 1 && executableElement.isVarArgs();
			addParamToMethod(method, parameter, JMod.FINAL, actualTypes, varParam, checkForAction);
			i++;
		}

		for (TypeMirror superThrownType : executableElement.getThrownTypes()) {
			AbstractJClass thrownType = typeMirrorToJClass(superThrownType, executableElement, actualTypes);
			method._throws(thrownType);
		}

		if (placeOverrideAndCallSuper) {
			callSuperMethod(method, holder, method.body());
		}

		return method;
	}

	public int elementVisibilityModifierToJMod(Element element) {
		Set<Modifier> modifiers = element.getModifiers();

		if (modifiers.contains(Modifier.PUBLIC)) {
			return JMod.PUBLIC;
		} else if (modifiers.contains(Modifier.PROTECTED)) {
			return JMod.PROTECTED;
		} else if (modifiers.contains(Modifier.PRIVATE)) {
			return JMod.PRIVATE;
		} else {
			return JMod.NONE;
		}
	}

	public void generify(IJGenerifiable generifiable, TypeElement fromTypeParameters) {
		for (TypeParameterElement param : fromTypeParameters.getTypeParameters()) {
			List<AbstractJClass> bounds = typeBoundsToJClass(param.getBounds());

			addTypeBounds(generifiable, bounds, param.getSimpleName().toString());
		}
	}

	public AbstractJClass narrowGeneratedClass(AbstractJClass generatedClass, TypeMirror fromTypeArguments) {
		DeclaredType type = (DeclaredType) fromTypeArguments;

		for (TypeMirror param : type.getTypeArguments()) {
			AbstractJClass paramClass = typeMirrorToJClass(param);
			generatedClass = generatedClass.narrow(paramClass);
		}
		return generatedClass;
	}

	protected JMethod findAlreadyGeneratedMethod(ExecutableElement executableElement, GeneratedClassHolder holder, boolean checkForAction) {

		JDefinedClass definedClass = holder.getGeneratedClass();
		String methodName = executableElement.getSimpleName().toString();

		if (checkForAction && !methodName.startsWith("$")) {
			methodName = "$" + methodName;
		}

		if (!checkForAction && methodName.startsWith("$")) {
			methodName = methodName.substring(1);
		}

		List<? extends VariableElement> parameters = executableElement.getParameters();
		// CHECKSTYLE:OFF
		// TODO: refactor the nasty label jump
		method: for (JMethod method : definedClass.methods()) {
			if (method.name().equals(methodName) && method.params().size() == parameters.size()) {
				int i = 0;
				for (JVar param : method.params()) {
					String searchedParamType = elementTypeToJClass(parameters.get(i)).fullName();
					if (searchedParamType.contains("<")) {
						searchedParamType = searchedParamType.substring(0, searchedParamType.indexOf('<'));
					}

					String currentParamType = param.type().fullName();
					if (currentParamType.contains("<")) {
						currentParamType = currentParamType.substring(0, currentParamType.indexOf('<'));
					}

					if (!currentParamType.equals(searchedParamType)) {
						continue method;
					}
					i++;
				}
				return method;
			}
		}
		// CHECKSTYLE:ON
		return null;
	}

	private void addParamToMethod(JMethod method, VariableElement parameter, int mod, Map<String, TypeMirror> actualTypes, boolean varParam, boolean checkForAction) {
		String parameterName = parameter.getSimpleName().toString();
		if (checkForAction) {
			parameterName = "$" + parameterName;
		}

		AbstractJClass parameterClass = typeMirrorToJClass(parameter.asType(), parameter, actualTypes);
		JVar param = varParam ? method.varParam(mod, parameterClass.elementType(), parameterName) : method.param(mod, parameterClass, parameterName);
		copyNonAAAnnotations(param, parameter.getAnnotationMirrors());
	}

	public void copyNonAAAnnotations(IJAnnotatable annotatable, List<? extends AnnotationMirror> annotationMirrors) {
		for (AnnotationMirror annotationMirror : annotationMirrors) {
			if (annotationMirror.getAnnotationType().asElement().getAnnotation(Inherited.class) == null) {
				AbstractJClass annotationClass = typeMirrorToJClass(annotationMirror.getAnnotationType());
				if (!environment.isAndroidAnnotation(annotationClass.fullName()) && !IGNORED_ANNOTATIONS.contains(annotationClass.fullName())) {
					copyAnnotation(annotatable, annotationMirror);
				}
			}
		}
	}

	public void copyAnnotation(IJAnnotatable annotatable, AnnotationMirror annotationMirror) {
		Map<? extends ExecutableElement, ? extends AnnotationValue> parameters = annotationMirror.getElementValues();

		if (!hasAnnotation(annotatable, annotationMirror) || annotatable instanceof JAnnotationArrayMember) {
			AbstractJClass annotation = typeMirrorToJClass(annotationMirror.getAnnotationType());
			JAnnotationUse annotate = annotatable.annotate(annotation);

			for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> param : parameters.entrySet()) {
				param.getValue().accept(new AnnotationParamExtractor(annotate, this), param.getKey().getSimpleName().toString());
			}
		}
	}

	private boolean hasAnnotation(IJAnnotatable annotatable, AnnotationMirror annotationMirror) {
		return hasAnnotation(annotatable, annotationMirror.getAnnotationType().toString());
	}

	public boolean hasAnnotation(IJAnnotatable annotatable, Class<? extends Annotation> annotationClass) {
		return hasAnnotation(annotatable, annotationClass.getCanonicalName());
	}

	private boolean hasAnnotation(IJAnnotatable annotatable, String annotationFQN) {
		for (JAnnotationUse annotation : annotatable.annotations()) {
			if (annotation.getAnnotationClass().fullName().equals(annotationFQN)) {
				return true;
			}
		}
		return false;
	}

	public JInvocation getSuperCall(GeneratedClassHolder holder, JMethod superMethod) {
		IJExpression activitySuper = holder.getGeneratedClass().staticRef("super");
		JInvocation superCall = JExpr.invoke(activitySuper, superMethod);

		for (JVar param : superMethod.params()) {
			superCall.arg(param);
		}

		if (superMethod.hasVarArgs()) {
			superCall.arg(superMethod.varParam());
		}

		return superCall;
	}

	public void callSuperMethod(JMethod superMethod, GeneratedClassHolder holder, JBlock callBlock) {
		JInvocation superCall = getSuperCall(holder, superMethod);

		AbstractJType returnType = superMethod.type();
		if (returnType.fullName().equals("void")) {
			callBlock.add(superCall);
		} else {
			callBlock._return(superCall);
		}
	}

	public JBlock removeBody(JMethod method) {
		JBlock body = method.body();
		try {
			Field bodyField = JMethod.class.getDeclaredField("m_aBody");
			bodyField.setAccessible(true);
			bodyField.set(method, null);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		JBlock clonedBody = new JBlock().bracesRequired(false).indentRequired(false);
		copy(body, clonedBody);
		return clonedBody;
	}

	public void copy(JBlock body, JBlock newBody) {
		for (Object statement : body.getContents()) {
			if (statement instanceof JVar) {
				JVar var = (JVar) statement;
				try {
					Field varInitField = JVar.class.getDeclaredField("m_aInitExpr");
					varInitField.setAccessible(true);
					IJExpression varInit = (IJExpression) varInitField.get(var);

					newBody.decl(var.type(), var.name(), varInit);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			} else {
				newBody.add((IJStatement) statement);
			}
		}
	}

	public void replaceSuperCall(JMethod method, JBlock replacement) {
		String superCallStart = "super." + method.name() + "(";

		JBlock oldBody = removeBody(method);
		JBlock newBody = method.body();

		for (Object content : oldBody.getContents()) {
			StringWriter writer = new StringWriter();
			IJFormatter formatter = createFormatter(writer);
			IJStatement statement = (IJStatement) content;
			statement.state(formatter);
			String statementString = writer.getBuffer().toString();
			if (statementString.startsWith(superCallStart)) {
				newBody.add(replacement);
			} else {
				newBody.add(statement);
			}
		}
	}

	private static IJFormatter createFormatter(StringWriter writer) {
		SourcePrintWriter sourcePrintWriter = new SourcePrintWriter(writer, JCMWriter.getDefaultNewLine());
		return new JFormatter(sourcePrintWriter, JCMWriter.DEFAULT_INDENT_STRING);
	}

	public JDefinedClass createDelegatingAnonymousRunnableClass(JBlock previousBody) {
		JCodeModel codeModel = environment.getCodeModel();

		JDefinedClass anonymousRunnableClass = codeModel.anonymousClass(Runnable.class);

		JMethod runMethod = anonymousRunnableClass.method(JMod.PUBLIC, codeModel.VOID, "run");
		runMethod.annotate(Override.class);
		runMethod.body().add(previousBody);

		return anonymousRunnableClass;
	}

	/**
	 * Gets all of the methods of the class and includes the methods of any
	 * implemented interfaces.
	 *
	 * @param typeElement
	 * @return full list of methods.
	 */
	public List<ExecutableElement> getMethods(TypeElement typeElement) {
		List<? extends Element> enclosedElements = typeElement.getEnclosedElements();
		List<ExecutableElement> methods = new ArrayList<>(ElementFilter.methodsIn(enclosedElements));

		// Add methods of the interfaces. These will be valid as they have gone
		// through the validator.
		for (TypeMirror iface : typeElement.getInterfaces()) {
			DeclaredType dt = (DeclaredType) iface;
			methods.addAll(ElementFilter.methodsIn(dt.asElement().getEnclosedElements()));
		}

		return methods;
	}

	public JMethod implementMethod(GeneratedClassHolder holder, List<ExecutableElement> methods, String methodName, String returnType, String... parameterTypes) {
		return implementMethod(holder, methods, methodName, returnType, false, parameterTypes);
	}

	public JMethod implementMethod(GeneratedClassHolder holder, List<ExecutableElement> methods, String methodName, String returnType, boolean finalParams, String... parameterTypes) {
		// First get the ExecutableElement method object from the util function.
		ExecutableElement method = getMethod(methods, methodName, returnType, parameterTypes);
		JMethod jmethod = null;

		if (method != null) {
			// Get the return type or VOID if none.
			AbstractJType jcReturnType = returnType.equals(TypeKind.VOID.toString()) ? environment.getCodeModel().VOID : environment.getJClass(returnType);

			// Create the implementation and annotate it with the Override
			// annotation.
			jmethod = holder.getGeneratedClass().method(JMod.PUBLIC, jcReturnType, method.getSimpleName().toString());
			jmethod.annotate(Override.class);

			// Create the parameters.
			int paramMods = finalParams ? JMod.FINAL : JMod.NONE;
			for (int i = 0; i < method.getParameters().size(); i++) {
				VariableElement param = method.getParameters().get(i);
				jmethod.param(paramMods, environment.getJClass(parameterTypes[i]), param.getSimpleName().toString());
			}
		}

		return jmethod;
	}

	private ExecutableElement getMethod(List<ExecutableElement> methods, String methodName, String returnType, String... parameterTypes) {
		for (ExecutableElement method : methods) {
			List<? extends VariableElement> parameters = method.getParameters();

			// Get the method return type or "VOID" if none.
			String methodReturnType = method.getReturnType().getKind() == TypeKind.VOID ? TypeKind.VOID.toString() : method.getReturnType().toString();

			if (parameters.size() == parameterTypes.length && methodReturnType.equals(returnType)) {
				if (methodName == null || method.getSimpleName().toString().equals(methodName)) {
					// At this point, method name, return type and number of
					// parameters are correct. Now we need to validate the
					// parameter types.
					boolean validMethod = true;

					for (int i = 0; i < parameters.size(); i++) {
						VariableElement param = parameters.get(i);

						if (!param.asType().toString().equals(parameterTypes[i])) {
							// Parameter type does not match, this is not the
							// correct method.
							validMethod = false;
							break;
						}
					}

					if (validMethod) {
						return method;
					}
				}
			}
		}

		return null;
	}

	public JInvocation newBeanOrEBean(DeclaredType beanType, JVar contextVar) {
		if (beanType.asElement().getAnnotation(EBean.class) != null) {
			String typeQualifiedName = beanType.toString();
			AbstractJClass injectedClass = environment.getJClass(typeQualifiedName + classSuffix());
			return injectedClass.staticInvoke(EBeanHolder.GET_INSTANCE_METHOD_NAME).arg(contextVar);
		} else {
			return _new(environment.getJClass(beanType.toString()));
		}
	}

	public IJExpression litObject(Object o) {
		if (o instanceof Integer) {
			return lit((Integer) o);
		} else if (o instanceof Float) {
			return lit((Float) o);
		} else if (o instanceof Long) {
			return lit((Long) o);
		} else if (o instanceof Boolean) {
			return lit((Boolean) o);
		} else {
			return lit((String) o);
		}
	}

	public TypeMirror getActualType(Element element, GeneratedClassHolder holder) {
		DeclaredType enclosingClassType = (DeclaredType) element.getEnclosingElement().asType();
		return getActualType(element, enclosingClassType, holder);
	}

	// TODO it would be nice to cache the result map for better performance
	public TypeMirror getActualType(Element element, DeclaredType enclosingClassType, GeneratedClassHolder holder) {
		Types types = environment.getProcessingEnvironment().getTypeUtils();
		TypeMirror annotatedClass = holder.getAnnotatedElement().asType();

		Map<String, TypeMirror> actualTypes = getActualTypes(types, enclosingClassType, annotatedClass);

		TypeMirror type = actualTypes.get(element.asType().toString());
		return type == null ? element.asType() : type;
	}

	public TypeMirror getActualTypeOfEnclosingElementOfInjectedElement(GeneratedClassHolder holder, Element param) {
		DeclaredType enclosingClassType;
		if (param.getKind() == ElementKind.PARAMETER) {
			enclosingClassType = (DeclaredType) param.getEnclosingElement().getEnclosingElement().asType();
		} else {
			enclosingClassType = (DeclaredType) param.getEnclosingElement().asType();
		}
		return getActualType(param, enclosingClassType, holder);
	}

	public void addSuppressWarnings(IJAnnotatable generatedElement, String annotationValue) {
		Collection<JAnnotationUse> annotations = generatedElement.annotations();
		for (JAnnotationUse annotationUse : annotations) {
			if (SuppressWarnings.class.getCanonicalName().equals(annotationUse.getAnnotationClass().fullName())) {
				AbstractJAnnotationValue value = annotationUse.getParam("value");
				StringWriter code = new StringWriter();
				IJFormatter formatter = createFormatter(code);
				formatter.generable(value);
				if (!code.toString().contains(annotationValue)) {
					if (value instanceof JAnnotationArrayMember) {
						((JAnnotationArrayMember) value).param(annotationValue);
					} else {
						String foundValue = code.toString().substring(1, code.toString().length() - 1);
						JAnnotationArrayMember newParamArray = annotationUse.paramArray("value");
						newParamArray.param(foundValue).param(annotationValue);
					}
				}
				return;
			}
		}

		generatedElement.annotate(SuppressWarnings.class).param("value", annotationValue);
	}

	public void addTrimmedDocComment(JMethod method, String docComment) {
		if (docComment != null) {
			method.javadoc().append(docComment.replaceAll("\r", "").trim());
		}
	}
}
