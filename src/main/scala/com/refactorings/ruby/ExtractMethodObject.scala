package com.refactorings.ruby

import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{MethodExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.RInstanceVariable
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RConstant, RIdentifier, RPseudoConstant}
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor
import org.jetbrains.plugins.ruby.ruby.lang.psi.{RPossibleCall, RPsiElement}

import scala.collection.mutable.ListBuffer

class ExtractMethodObject extends RefactoringIntention(ExtractMethodObject) {

  override def getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

  override def startInWriteAction = false

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit project: Project): Unit = {
    val methodToRefactor = elementToRefactor(focusedElement).get
    val pointersToElementsToRename = WriteAction.compute(() => {
      methodToRefactor.normalizeSpacesAfterParameterList
      new ExtractMethodObjectApplier(methodToRefactor, project).apply()
    })

    runTemplate(
      editor,
      rootElement = methodToRefactor.getParent,
      elementsToRename = pointersToElementsToRename.map(_.map(_.getElement))
    )
  }

  private def runTemplate(editor: Editor, rootElement: PsiElement, elementsToRename: List[List[PsiElement]]): Unit = {
    WriteCommandAction.writeCommandAction(rootElement.getProject).run(() => {
      val builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(rootElement)

      elementsToRename.zipWithIndex.foreach {
        case (elementReferences, index) =>
          elementReferences match {
            case firstElement :: restOfElements =>
              builder.replaceElement(
                firstElement,
                s"$$PLACEHOLDER_${index}$$",
                new ConstantNode(firstElement.getText),
                true
              )

              restOfElements.foreach { element =>
                builder.replaceElement(
                  element,
                  s"$$PLACEHOLDER_${index}_REPLICA$$",
                  s"$$PLACEHOLDER_${index}$$",
                  false
                )
              }
            case Nil => ()
          }
      }

      builder.run(editor, true)
    })
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

  def apply(): List[List[SmartPsiElementPointer[PsiElement]]] = {
    val finalMethodObjectClassDefinition =
      methodObjectClassDefinition.putAfter(methodToRefactor)

    val finalMethodBody =
      methodToRefactor.replaceBodyWith(methodObjectInvocation)

    pointersToElementsToRenameFrom(finalMethodObjectClassDefinition, finalMethodBody)
  }

  private def methodObjectClassDefinition  = {
    val methodObjectClass = Parser.parseHeredoc(
      s"""
         |class ${methodObjectClassName}
         |  def call
         |    BODY
         |  end
         |end
        """
    ).childOfType[RClass]()
    val callMethodTemplate = methodObjectClass.childOfType[RMethod]()

    if (methodToRefactorUsesSelf || methodToRefactorHasParameters) {
      methodObjectConstructor.putBefore(callMethodTemplate)
    }

    callMethodTemplate.body.replace(invocationMethodBody)

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
      s"${methodObjectClassName}.new${methodObjectConstructorArguments}.call"
    ).asInstanceOf[RCompoundStatement]
  }

  private def methodObjectClassReferenceFrom(methodObjectInvocation: RCompoundStatement) = {
    val callMessageSend = callMessageSendFrom(methodObjectInvocation)
    val newMessageSend = callMessageSend.getReceiver.asInstanceOf[RPossibleCall]
    newMessageSend.getReceiver.asInstanceOf[RConstant]
  }

  private def callMessageSendFrom(methodObjectInvocation: RCompoundStatement) = {
    methodObjectInvocation.getStatements.head.asInstanceOf[RPossibleCall]
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

  private def pointersToElementsToRenameFrom(methodObjectClassDefinition: RClass, methodBody: RCompoundStatement) = {
    val methodObjectClassReferences = List(
      methodObjectClassReferenceFrom(methodBody),
      methodObjectClassDefinition.getClassName
    )

    val callMethodReferences = List(
      callMessageSendFrom(methodBody).getPsiCommand,
      methodObjectClassDefinition.findMethodByName("call").getNameIdentifier
    )

    List(methodObjectClassReferences, callMethodReferences).map(
      _.map(SmartPointerManager.createPointer(_))
    )
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
