package org.androidannotations.helper;

import javax.annotation.Nullable;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;

import org.androidannotations.AndroidAnnotationsEnvironment;
import org.androidannotations.internal.virtual.VirtualElement;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

public class CompilationTreeHelper {

	private AndroidAnnotationsEnvironment environment;
	ProcessingEnvironment processingEnvironment;
	private final Trees trees;

	public CompilationTreeHelper(AndroidAnnotationsEnvironment environment) {
		super();
		this.environment = environment;
		this.processingEnvironment = environment.getProcessingEnvironment();
		this.trees = Trees.instance(environment.getProcessingEnvironment());
	}

	@Nullable
	public CompilationUnitTree getCompilationUnitImportFromElement(Element referenceElement) {

		final TreePath treePath = trees.getPath(referenceElement instanceof VirtualElement ? ((VirtualElement) referenceElement).getElement() : referenceElement);

		if (treePath == null) {
			return null;
		}

		return treePath.getCompilationUnit();

	}

	public String getClassNameFromCompilationUnitImports(String className, Element referenceElement) {

		if (referenceElement == null || processingEnvironment.getElementUtils().getTypeElement(className) != null) {

			return className;

		}

		CompilationUnitTree compilationUnit = getCompilationUnitImportFromElement(referenceElement);
		if (compilationUnit == null) {
			return className;
		}

		// TODO Handle imports with "*"

		for (ImportTree importTree : compilationUnit.getImports()) {

			String lastElementImport = importTree.getQualifiedIdentifier().toString();
			String firstElementName = className;
			String currentVariableClass = "";

			int pointIndex = lastElementImport.lastIndexOf('.');
			if (pointIndex != -1) {
				lastElementImport = lastElementImport.substring(pointIndex + 1);
			}

			pointIndex = firstElementName.indexOf('.');
			if (pointIndex != -1) {
				firstElementName = firstElementName.substring(0, pointIndex);
				currentVariableClass = className.substring(pointIndex);
			}

			while (firstElementName.endsWith("[]")) {
				firstElementName = firstElementName.substring(0, firstElementName.length() - 2);
				if (currentVariableClass.isEmpty()) {
					currentVariableClass = currentVariableClass + "[]";
				}
			}

			if (lastElementImport.equals(firstElementName)) {
				return importTree.getQualifiedIdentifier() + currentVariableClass;
			}

		}

		// If the class is not referenced in the imports, then the class probably is in
		// the same package
		return compilationUnit.getPackageName() + "." + className;

	}

	public void visitElementTree(Element element, TreePathScanner<Boolean, Trees> scanner) {
		final TreePath treePath = trees.getPath(element instanceof VirtualElement ? ((VirtualElement) element).getElement() : element);
		scanner.scan(treePath, trees);
	}

}
