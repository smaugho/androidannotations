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
package org.androidannotations.logger.appender;

import java.util.LinkedList;
import java.util.List;

import javax.annotation.processing.Messager;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;

import org.androidannotations.logger.Level;
import org.androidannotations.logger.formatter.FormatterSimple;

public class MessagerAppender extends Appender {

	private static List<Message> errors = new LinkedList<>();

	private Messager messager;

	public MessagerAppender() {
		super(new FormatterSimple());
	}

	@Override
	public void open() {
		messager = processingEnv.getMessager();
	}

	@Override
	public void append(Level level, Element element,
			AnnotationMirror annotationMirror, String message) {
		
		if (messager == null) {
			return;
		}

		Kind kind = resolveKind(level);
		if (!kind.equals(Kind.ERROR)) {
			messager.printMessage(kind, message, element, annotationMirror);
		} else {
			errors.add(new Message(kind, message, element, annotationMirror));
		}
	}

	@Override
	public synchronized void close(boolean lastRound) {
		if (lastRound) {
			for (Message error : errors) {
				messager.printMessage(error.kind, error.message, error.getElement(), error.annotationMirror);
			}
			errors.clear();
		}
	}

	private Kind resolveKind(Level level) {
		switch (level) {
		case TRACE:
			return Kind.NOTE;
		case DEBUG:
			return Kind.NOTE;
		case INFO:
			return Kind.NOTE;
		case WARN:
			return Kind.WARNING;
		case ERROR:
			return Kind.ERROR;
		}
		return Kind.OTHER;
	}

	private class Message {
		Kind kind;
		String message;
		AnnotationMirror annotationMirror;
		List<String> elements = new LinkedList<>();

		Message(Kind kind, String message, Element element, AnnotationMirror annotationMirror) {
			super();
			this.kind = kind;
			this.message = message;
			this.annotationMirror = annotationMirror;

			if (element != null) {
				Element enclosingElement = element;
				do {
					elements.add(0, enclosingElement.toString());
					enclosingElement = enclosingElement.getEnclosingElement();
				} while (!enclosingElement.getKind().equals(ElementKind.PACKAGE));
			}
		}

		public Element getElement() {
			if (elements.isEmpty()) {
				return null;
			}

			Element element = processingEnv.getElementUtils().getTypeElement(elements.remove(0));
			while (elements.size() > 0) {
				if (element instanceof ExecutableElement) {
					ExecutableElement method = (ExecutableElement) element;
					for (VariableElement param : method.getParameters()) {
						if (param.toString().equals(elements.get(0))) {
							elements.remove(0);
							element = param;
							break;
						}
					}
				} else {
					for (Element elem : element.getEnclosedElements()) {
						if (elem.toString().equals(elements.get(0))) {
							elements.remove(0);
							element = elem;
							break;
						}
					}
				}
			}

			return element;
		}

	}

}
