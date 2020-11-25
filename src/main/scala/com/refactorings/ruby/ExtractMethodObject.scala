package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiReference}
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{MethodExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.{RBodyStatement, RCompoundStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.RInstanceVariable
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RIdentifier, RPseudoConstant}
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor

import scala.collection.mutable.ListBuffer

class ExtractMethodObject extends RefactoringIntention(ExtractMethodObject) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val methodToRefactor = focusedMethodFrom(focusedElement)

    new ExtractMethodObjectApplier(methodToRefactor, currentProject).apply()
  }

  private def focusedMethodFrom(focusedElement: PsiElement)(implicit project: Project) = {
    val focusedMethod = elementToRefactor(focusedElement).get
    focusedMethod.normalizeSpacesAfterParameterList
    focusedMethod
  }

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

private class ExtractMethodObjectApplier(methodToRefactor: RMethod, implicit val project: Project) {
  private val selfReferences: List[PsiReference] = selfReferencesFrom(methodToRefactor)
  private val parameterIdentifiers: List[RIdentifier] = methodToRefactor.parameterIdentifiers

  def apply(): Unit = {
    methodObjectClassDefinition.putAfter(methodToRefactor)
    methodToRefactor.replaceBodyWith(methodObjectInvocation)
  }

  private def methodObjectClassDefinition  = {
    val methodObjectClass = Parser.parseHeredoc(
      s"""
         |class ${methodObjectClassName}
         |  def invoke
         |    BODY
         |  end
         |end
        """
    )
    val invokeMethodTemplate = methodObjectClass.childOfType[RMethod]()

    if (methodToRefactorUsesSelf || methodToRefactorHasParameters) {
      methodObjectConstructor.putBefore(invokeMethodTemplate)
    }

    invokeMethodTemplate.body.replace(invocationMethodBody)

    methodObjectClass
  }

  private def methodToRefactorHasParameters = parameterIdentifiers.nonEmpty

  private def methodToRefactorUsesSelf = selfReferences.nonEmpty

  private def methodObjectConstructor = {
    var parameterNames = parameterIdentifiers.map(_.getText)
    if (methodToRefactorUsesSelf) {
      parameterNames = parameterNames.appended("original_receiver")
    }

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

  private def invocationMethodBody = {
    // Here we mutate the method body from the original method to refactor.
    // This shouldn't be a problem, since we're going to override the original method's body afterwards.
    replaceAllWithInstanceVariableNamed(selfReferences, "original_receiver")
    parameterIdentifiers.foreach { parameterIdentifier =>
      replaceAllWithInstanceVariableNamed(
        parameterIdentifier.referencesInside(methodToRefactor.body),
        parameterIdentifier.getText
      )
    }

    methodToRefactor.body
  }

  private def replaceAllWithInstanceVariableNamed(references: Iterable[PsiReference], parameterName: String): Unit = {
    val instanceVariableRead = Parser.parse(s"@$parameterName").childOfType[RInstanceVariable]()

    references.foreach(
      _.getElement.replace(instanceVariableRead)
    )
  }

  private def methodObjectInvocation = {
    Parser.parse(
      s"${methodObjectClassName}.new${methodObjectConstructorArguments}.invoke"
    ).asInstanceOf[RCompoundStatement]
  }

  private def methodObjectConstructorArguments = {
    var parameterNames = parameterIdentifiers.map(_.getText)
    if (methodToRefactorUsesSelf) {
      parameterNames = parameterNames.appended("self")
    }

    if (parameterNames.nonEmpty) {
      parameterNames.mkString("(", ", ", ")")
    } else {
      ""
    }
  }

  private def selfReferencesFrom(focusedMethod: RMethod) = {
    val selfReferences = new ListBuffer[PsiReference]
    val visitor = new RubyRecursiveElementVisitor() {
      override def visitRPseudoConstant(pseudoConstant: RPseudoConstant): Unit = {
        super.visitRPseudoConstant(pseudoConstant)

        if (pseudoConstant.textMatches("self")) {
          selfReferences += pseudoConstant.getReference
        }
      }
    }
    focusedMethod.accept(visitor)
    selfReferences.toList
  }

  private lazy val methodObjectClassName = {
    val methodName = methodToRefactor.getMethodName.getText
    snakeToCamelCase(methodName).capitalize + "MethodObject"
  }

  // Ojo: does it work when the name starts with _?
  // See: https://gist.github.com/sidharthkuruvila/3154845
  private def snakeToCamelCase(name: String) = "_([a-z\\d])".r.replaceAllIn(name, {m =>
    m.group(1).toUpperCase()
  })
}
