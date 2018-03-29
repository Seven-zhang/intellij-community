// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.python.codeInsight.completion;

import com.intellij.codeInsight.completion.*;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.stubs.StubIndex;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.psi.util.QualifiedName;
import com.intellij.util.containers.ContainerUtil;
import com.jetbrains.python.psi.*;
import com.jetbrains.python.psi.resolve.PyQualifiedNameResolveContext;
import com.jetbrains.python.psi.resolve.QualifiedNameFinder;
import com.jetbrains.python.psi.search.PyProjectScopeBuilder;
import com.jetbrains.python.psi.stubs.PyClassNameIndex;
import com.jetbrains.python.psi.stubs.PyFunctionNameIndex;
import com.jetbrains.python.psi.stubs.PyVariableNameIndex;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Function;

import static com.jetbrains.python.psi.resolve.PyResolveImportUtil.fromFoothold;
import static com.jetbrains.python.psi.resolve.PyResolveImportUtil.resolveQualifiedName;

/**
 * Adds completion variants for Python classes, functions and variables.
 *
 * @author yole
 */
public class PyClassNameCompletionContributor extends PyExtendedCompletionContributor {

  @Override
  protected void doFillCompletionVariants(@NotNull CompletionParameters parameters, @NotNull CompletionResultSet result) {
    final PsiFile originalFile = parameters.getOriginalFile();
    final PsiElement element = parameters.getPosition();
    final PsiElement parent = element.getParent();

    addVariantsFromIndex(result,
                         originalFile,
                         PyClassNameIndex.KEY,
                         parent instanceof PyStringLiteralExpression ? getStringLiteralInsertHandler() : getImportingInsertHandler(),
                         // TODO: implement autocompletion for inner classes, also see docstring for isTopLevelMemberAccessible
                         PyUtil::isTopLevel,
                         PyClass.class,
                         createClassElementHandler(originalFile));

    addVariantsFromIndex(result,
                         originalFile,
                         PyFunctionNameIndex.KEY,
                         getFunctionInsertHandler(parent),
                         PyUtil::isTopLevel,
                         PyFunction.class,
                         Function.identity());

    addVariantsFromIndex(result,
                         originalFile,
                         PyVariableNameIndex.KEY,
                         parent instanceof PyStringLiteralExpression ? getStringLiteralInsertHandler() : getImportingInsertHandler(),
                         PyUtil::isTopLevel,
                         PyTargetExpression.class,
                         Function.identity());
  }

  @NotNull
  private static Function<LookupElement, LookupElement> createClassElementHandler(@NotNull PsiFile file) {
    final PyFile pyFile = PyUtil.as(file, PyFile.class);
    if (pyFile == null) return Function.identity();

    final Set<QualifiedName> sourceQNames =
      ContainerUtil.map2SetNotNull(pyFile.getFromImports(), PyFromImportStatement::getImportSourceQName);

    return le -> {
      final PyClass cls = PyUtil.as(le.getPsiElement(), PyClass.class);
      if (cls == null) return le;

      final String clsQName = cls.getQualifiedName();
      if (clsQName == null) return le;

      if (!sourceQNames.contains(QualifiedName.fromDottedString(clsQName).removeLastComponent())) return le;

      return PrioritizedLookupElement.withPriority(le, PythonCompletionWeigher.WEIGHT_DELTA);
    };
  }

  private InsertHandler<LookupElement> getFunctionInsertHandler(PsiElement parent) {
    if (parent instanceof PyStringLiteralExpression) {
      return getStringLiteralInsertHandler();
    }
    if (parent.getParent() instanceof PyDecorator) {
      return getImportingInsertHandler();
    }
    return getFunctionInsertHandler();
  }

  private static <T extends PsiNamedElement> void addVariantsFromIndex(@NotNull CompletionResultSet resultSet,
                                                                       @NotNull PsiFile targetFile,
                                                                       @NotNull StubIndexKey<String, T> key,
                                                                       @NotNull InsertHandler<LookupElement> insertHandler,
                                                                       @NotNull Condition<? super T> condition,
                                                                       @NotNull Class<T> elementClass,
                                                                       @NotNull Function<LookupElement, LookupElement> elementHandler) {
    final Project project = targetFile.getProject();
    final GlobalSearchScope scope = PyProjectScopeBuilder.excludeSdkTestsScope(targetFile);
    final Map<String, LookupElement> uniqueResults = new HashMap<>();

    final Collection<String> keys = StubIndex.getInstance().getAllKeys(key, project);
    for (final String elementName : CompletionUtil.sortMatching(resultSet.getPrefixMatcher(), keys)) {
      for (T element : StubIndex.getElements(key, elementName, project, scope, elementClass)) {
        if (condition.value(element)) {
          final String name = element.getName();
          final ItemPresentation itemPresentation = ((NavigationItem)element).getPresentation();
          if (name != null && itemPresentation != null && itemPresentation.getLocationString() != null) {
            final LookupElementBuilder builder = LookupElementBuilder
              .createWithSmartPointer(name, element)
              .withIcon(element.getIcon(0))
              .withTailText(" " + itemPresentation.getLocationString(), true)
              .withInsertHandler(insertHandler);

            uniqueResults.put(itemPresentation.getPresentableText() + itemPresentation.getLocationString(), builder);
          }
        }
      }
    }
    uniqueResults.values().stream()
                 .map(elementHandler)
                 .filter(PyClassNameCompletionContributor::isTopLevelMemberAccessible)
                 .forEach(resultSet::addElement);
  }

  /**
   * Check whether the element from the {@link LookupElement} is accessible with current context
   * and source root configuration.
   *
   * The method returns false for nested classes so it should ne changed to implement nested classes completion.
   */
  private static boolean isTopLevelMemberAccessible(LookupElement lookupElement) {
    PsiElement element = lookupElement.getPsiElement();
    if (!(element instanceof PyElement) || !(element.getContainingFile() instanceof PyFile)) {
      return false;
    }
    PyElement pyElement = (PyElement)element;
    String name = QualifiedNameFinder.getQualifiedName(pyElement);
    if (name == null) {
      return false;
    }
    QualifiedName qualifiedName = QualifiedName.fromDottedString(name);
    if (qualifiedName.getLastComponent() == null) {
      return false;
    }

    PyQualifiedNameResolveContext resolveContext = fromFoothold(element.getContainingFile());
    List<PsiElement> moduleResolveResults = resolveQualifiedName(qualifiedName.removeLastComponent(), resolveContext);

    return StreamEx.of(moduleResolveResults)
                   .map(psiElement -> {
                     if (psiElement instanceof PyFile) {
                       return (PyFile)psiElement;
                     }
                     if (psiElement instanceof PsiDirectory) {
                       return PyUtil.as(PyUtil.turnDirIntoInit(psiElement), PyFile.class);
                     }
                     return null;
                   })
                   .nonNull()
                   .flatMap(resolveResult -> resolveResult.multiResolveName(qualifiedName.getLastComponent()).stream())
                   .findAny().isPresent();
  }
}
