package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{MethodExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.{RBodyStatement, RCompoundStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier

class ExtractMethodObject extends RefactoringIntention(ExtractMethodObject) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val methodToRefactor = focusedMethodFrom(focusedElement)

    methodObjectClassFor(methodToRefactor).putBefore(methodToRefactor)
    methodToRefactor.replaceBodyWith(methodObjectInvocationFor(methodToRefactor))
  }

  private def focusedMethodFrom(focusedElement: PsiElement)(implicit project: Project) = {
    val focusedMethod = elementToRefactor(focusedElement).get
    focusedMethod.normalizeSpacesAfterParameterList
    focusedMethod
  }

  private def methodObjectClassFor(focusedMethod: RMethod)(implicit project: Project) = {
    val methodObjectClass = Parser.parseHeredoc(
      s"""
         |class ${methodObjectClassNameFor(focusedMethod)}
         |  def invoke
         |    BODY
         |  end
         |end
      """)
    val invokeMethod = methodObjectClass.childOfType[RMethod]()

    if (focusedMethod.hasParameters) {
      val parameterIdentifiers = focusedMethod.parameterIdentifiers
      replaceReferencesWithInstanceVariables(parameterIdentifiers, focusedMethod.body)
      methodObjectConstructorFrom(parameterIdentifiers).putBefore(invokeMethod)
    }

    val sourceBody = focusedMethod.body
    val targetBody = invokeMethod.body
    targetBody.replace(sourceBody)

    methodObjectClass
  }

  private def methodObjectConstructorFrom(parameterIdentifiers: List[RIdentifier])(implicit project: Project) = {
    val parameterNames = parameterIdentifiers.map(_.getText)
    val constructorParameterList = parameterNames.mkString(", ")
    val instanceVariableInitialization = parameterNames
      .map { parameterName => s"@$parameterName = $parameterName" }
      .mkString("\n  ")

    Parser.parseHeredoc(
      s"""
        |def initialize(${constructorParameterList})
        |  $instanceVariableInitialization
        |end
      """
    )
  }

  private def replaceReferencesWithInstanceVariables(parameterIdentifiers: List[RIdentifier], methodBody: RBodyStatement)(implicit project: Project) = {
    parameterIdentifiers.foreach { parameterIdentifier =>
      val parameterName = parameterIdentifier.getText
      val instanceVariableRead = Parser.parse(s"@$parameterName")

      ReferencesSearch
        .search(parameterIdentifier, new LocalSearchScope(methodBody))
        .findAll()
        .forEach(parameterReference => {
          parameterReference.getElement.replace(instanceVariableRead)
        })
    }
  }

  private def methodObjectInvocationFor(focusedMethod: RMethod)(implicit project: Project) = {
    val methodObjectConstructorArguments = if (focusedMethod.hasParameters) {
      val parameterNames = focusedMethod.parameterIdentifiers.map(_.getText)

      s"(${parameterNames.mkString(", ")})"
    } else {
      ""
    }

    val newMethodBody = Parser.parseHeredoc(
      s"""
         |${methodObjectClassNameFor(focusedMethod)}.new${methodObjectConstructorArguments}.invoke
      """).asInstanceOf[RCompoundStatement]
    newMethodBody
  }

  private def methodObjectClassNameFor(sourceMethod: RMethod) = {
    val methodName = sourceMethod.getMethodName.getText
    snakeToCamelCase(methodName).capitalize + "MethodObject"
  }

  // Ojo: does it work when the name starts with _?
  // See: https://gist.github.com/sidharthkuruvila/3154845
  private def snakeToCamelCase(name: String) = "_([a-z\\d])".r.replaceAllIn(name, {m =>
    m.group(1).toUpperCase()
  })

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementToRefactor(focusedElement).isDefined
  }

  private def elementToRefactor(focusedElement: PsiElement) = {
    focusedElement.findParentOfType[RMethod](treeHeightLimit = 3)
  }
}

object ExtractMethodObject extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Extracts a method object based on the contents of an existing method"

  override def optionDescription: String = "Extract method object"
}

