package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiReference}
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{MethodExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.{RBodyStatement, RCompoundStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.RInstanceVariable
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RIdentifier, RPseudoConstant}
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor

import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters._

class ExtractMethodObject extends RefactoringIntention(ExtractMethodObject) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val methodToRefactor = focusedMethodFrom(focusedElement)

    val selfReferences = selfReferencesFrom(methodToRefactor)
    methodObjectClassFor(methodToRefactor, selfReferences).putBefore(methodToRefactor)
    methodToRefactor.replaceBodyWith(methodObjectInvocationFor(methodToRefactor, selfReferences))
  }

  private def focusedMethodFrom(focusedElement: PsiElement)(implicit project: Project) = {
    val focusedMethod = elementToRefactor(focusedElement).get
    focusedMethod.normalizeSpacesAfterParameterList
    focusedMethod
  }

  private def methodObjectClassFor(focusedMethod: RMethod, selfReferences: List[PsiReference])(implicit project: Project) = {
    val methodObjectClass = Parser.parseHeredoc(
      s"""
         |class ${methodObjectClassNameFor(focusedMethod)}
         |  def invoke
         |    BODY
         |  end
         |end
      """)
    val invokeMethod = methodObjectClass.childOfType[RMethod]()

    if (selfReferences.nonEmpty || focusedMethod.hasParameters) {
      val parameterIdentifiers = focusedMethod.parameterIdentifiers

      replaceWithInstanceVariableNamed(selfReferences, "original_receiver")
      replaceReferencesWithInstanceVariables(parameterIdentifiers, focusedMethod.body)

      var parameterNames = parameterIdentifiers.map(_.getText)
      if (selfReferences.nonEmpty) {
        parameterNames = parameterNames.appended("original_receiver")
      }
      methodObjectConstructor(parameterNames).putBefore(invokeMethod)
    }

    val sourceBody = focusedMethod.body
    val targetBody = invokeMethod.body
    targetBody.replace(sourceBody)

    methodObjectClass
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

  private def methodObjectConstructor(parameterNames: List[String])(implicit project: Project) = {
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

  private def replaceReferencesWithInstanceVariables
  (parameterIdentifiers: List[RIdentifier], methodBody: RBodyStatement)(implicit project: Project): Unit = {
    parameterIdentifiers.foreach { parameterIdentifier =>
      val references = ReferencesSearch
        .search(parameterIdentifier, new LocalSearchScope(methodBody))
        .findAll()
        .asScala

      replaceWithInstanceVariableNamed(references, parameterIdentifier.getText)
    }
  }

  private def replaceWithInstanceVariableNamed
  (references: Iterable[PsiReference], parameterName: String)(implicit project: Project): Unit = {
    val instanceVariableRead = Parser.parse(s"@$parameterName").childOfType[RInstanceVariable]()
    references.foreach(parameterReference => {
      parameterReference.getElement.replace(instanceVariableRead)
    })
  }

  private def methodObjectInvocationFor(focusedMethod: RMethod, selfReferences: List[PsiReference])(implicit project: Project) = {
    val parameterNames = focusedMethod.parameterIdentifiers.map(_.getText) ++
      (if (selfReferences.nonEmpty) List("self") else List())

    val methodObjectConstructorArguments = if (parameterNames.nonEmpty) {
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

