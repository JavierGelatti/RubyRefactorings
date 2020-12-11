package com.refactorings.ruby

import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{IdentifierExtension, MethodExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.RYieldStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.{ArgumentInfo, RBlockArgument, RMethod}
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RDotReference
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.RInstanceVariable
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RConstant, RIdentifier, RPseudoConstant}
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor

import scala.collection.mutable.ListBuffer

class ExtractMethodObject extends RefactoringIntention(ExtractMethodObject) {

  override def getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

  override def startInWriteAction = false

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit project: Project): Unit = {
    val methodToRefactor = elementToRefactor(focusedElement).get

    try {
      val pointersToElementsToRename = WriteAction.compute(() => {
        new ExtractMethodObjectApplier(methodToRefactor, project).apply()
      })

      runTemplate(
        editor,
        rootElement = methodToRefactor.getParent,
        elementsToRename = pointersToElementsToRename.map(_.map(_.getElement))
      )
    } catch {
      case ex: CannotApplyRefactoringException =>
        UI.showErrorHint(ex.textRange, editor, ex.getMessage)
    }
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
  private val parameterIdentifiers: List[RIdentifier] = methodToRefactor.parameterIdentifiers
  private var selfReferences: List[PsiReference] = _

  def apply(): List[List[SmartPsiElementPointer[PsiElement]]] = {
    assertNoInstanceVariablesAreReferenced()
    makeImplicitSelfReferencesExplicit()
    selfReferences = selfReferencesFrom(methodToRefactor)
    methodToRefactor.normalizeSpacesAfterParameterList

    val finalMethodObjectClassDefinition =
      methodObjectClassDefinition.putAfter(methodToRefactor)

    if (methodUsesBlock) {
      methodToRefactor.getArgumentList.addParameter("&block", ArgumentInfo.Type.BLOCK, true)
    }

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

  private lazy val methodUsesBlock = {
    var methodUsesBlock = false
    methodToRefactor.accept(new RubyRecursiveElementVisitor() {
      override def visitRYieldStatement(rYieldStatement: RYieldStatement): Unit = {
        methodUsesBlock = true
        super.visitRYieldStatement(rYieldStatement)
      }
    })

    methodUsesBlock
  }

  private def methodObjectInvocation = {
    val callArguments = if (methodUsesBlock) "(&block)" else ""
    Parser.parse(
      s"${methodObjectClassName}.new${methodObjectConstructorArguments}.call${callArguments}"
    ).asInstanceOf[RCompoundStatement]
  }

  private def methodObjectClassReferenceFrom(methodObjectInvocation: RCompoundStatement) = {
    val callMessageSend = callMessageSendFrom(methodObjectInvocation)
    val newMessageSend = callMessageSend.getReceiver.asInstanceOf[RPossibleCall]
    newMessageSend.getReceiver.asInstanceOf[RConstant]
  }

  private def callMessageSendFrom(methodObjectInvocation: RCompoundStatement) = {
    methodObjectInvocation.childOfType[RDotReference]()
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

  private def assertNoInstanceVariablesAreReferenced(): Unit = {
    methodToRefactor.accept(new RubyRecursiveElementVisitor() {
      override def visitRInstanceVariable(instanceVariable: RInstanceVariable): Unit = {
        throw new CannotApplyRefactoringException(
          "Cannot perform refactoring if there are references to instance variables",
          instanceVariable.getTextRange
        )
      }
    })
  }

  private def makeImplicitSelfReferencesExplicit(): Unit = {
    messageSendsWithImplicitReceiverIn(methodToRefactor).foreach { messageSend =>
      messageSend.getReference.resolve() match {
        case method: RMethod if !method.isPublic =>
          throw new CannotApplyRefactoringException(
            "Cannot perform refactoring if a private method is being called",
            messageSend.getTextRange
          )
        case _ =>
          val messageSendWithExplicitSelf = Parser.parse(s"self.${messageSend.getText}").getFirstChild
          messageSend.replace(messageSendWithExplicitSelf)
      }
    }
  }

  private def messageSendsWithImplicitReceiverIn(method: RMethod) = {
    val messageSends = new ListBuffer[RIdentifier]
    method.accept(new RubyRecursiveElementVisitor() {
      override def visitRIdentifier(rIdentifier: RIdentifier): Unit = {
        if (rIdentifier.isMessageSendWithImplicitReceiver) {
          messageSends.addOne(rIdentifier)
        }
        super.visitRIdentifier(rIdentifier)
      }
    })
    messageSends.toList
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
