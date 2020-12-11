package com.refactorings.ruby

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.refactorings.ruby.ExtractMethodObject.initialMethodObjectClassNameFrom
import com.refactorings.ruby.psi.PsiElementExtensions.{MethodExtension, PossibleCallExtension, PsiElementExtension}
import com.refactorings.ruby.psi.{CodeCompletionTemplate, Parser}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RDotReference
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.RInstanceVariable
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RConstant, RIdentifier, RPseudoConstant}
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor

import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

class ExtractMethodObject extends RefactoringIntention(ExtractMethodObject) {

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementToRefactor(focusedElement).isDefined
  }

  override def getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile

  override def startInWriteAction = false

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit project: Project): Unit = {
    val methodToRefactor = elementToRefactor(focusedElement).get

    try {
      val pointersToElementsToRename = WriteAction.compute(() => {
        new ExtractMethodObjectApplier(methodToRefactor, project).apply()
      })

      new CodeCompletionTemplate(
        editor,
        rootElement = methodToRefactor.getParent,
        elementsToRename = pointersToElementsToRename.map(_.map(_.getElement))
      ).run()
    } catch {
      case ex: CannotApplyRefactoringException =>
        UI.showErrorHint(ex.textRange, editor, ex.getMessage)
    }
  }

  private def elementToRefactor(focusedElement: PsiElement) = {
    focusedElement.findParentOfType[RMethod](treeHeightLimit = 3)
  }
}

object ExtractMethodObject extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Extracts a method object based on the contents of an existing method"

  override def optionDescription: String = "Extract method object"

  // See https://docs.ruby-lang.org/en/2.3.0/syntax/methods_rdoc.html#label-Method+Names
  private val messageName: Map[String, String] = Map(
    "+" -> "Add",
    "-" -> "Subtract",
    "*" -> "Multiply",
    "**" -> "Power",
    "/" -> "Divide",
    "%" -> "Modulo",
    "&" -> "And",
    "^" -> "Xor",
    ">>" -> "Shift",
    "<<" -> "Append",
    "==" -> "Equal",
    "!=" -> "NotEqual",
    "===" -> "CaseEqual",
    "=~" -> "Match",
    "!~" -> "NotMatch",
    "<=>" -> "Comparison",
    "<" -> "LessThan",
    "<=" -> "LessThanOrEqual",
    ">" -> "GreaterThan",
    ">=" -> "GreaterThanOrEqual",
    "-@" -> "Invert",
    "+@" -> "Plus",
    "~@" -> "Not",
    "!@" -> "Not",
    "[]" -> "ReadElement",
    "[]=" -> "WriteElement"
  ).withDefault { methodName =>
    val prefix = if (isWriteMethod(methodName)) "Write" else ""
    val suffix = if (methodName.endsWith("!")) "Bang" else ""

    prefix + methodName.stripSuffix("?").stripSuffix("=").stripSuffix("!") + suffix
  }

  def initialMethodObjectClassNameFrom(methodName: String): String = {
    messageName(methodName.snakeToPascalCase) + "MethodObject"
  }

  private def isWriteMethod(methodName: String) = {
    methodName.endsWith("=") && (methodName.head.isLetter || methodName.head == '_')
  }
}

private class ExtractMethodObjectApplier(methodToRefactor: RMethod, implicit val project: Project) {
  private val DEFAULT_INVOCATION_MESSAGE = "call"
  private val DEFAULT_RECEIVER_PARAMETER_NAME = "original_receiver"
  private val DEFAULT_BLOCK_PARAMETER_NAME = "block"

  private val parameterIdentifiers: List[RIdentifier] = methodToRefactor.parameterIdentifiers
  private var selfReferences: List[PsiReference] = _

  def apply(): List[List[SmartPsiElementPointer[PsiElement]]] = {
    assertNoInstanceVariablesAreReferenced()
    assertSuperIsNotUsed()
    assertThereAreOnlyPublicMessageSends()

    methodToRefactor.normalizeSpacesAfterParameterList()
    makeImplicitSelfReferencesExplicit()
    selfReferences = selfReferencesFrom(methodToRefactor)

    val finalMethodObjectClassDefinition =
      methodObjectClassDefinition.putAfter(methodToRefactor)

    if (methodUsesBlock && !methodToRefactor.hasBlockParameter) {
      methodToRefactor.addBlockParameter(DEFAULT_BLOCK_PARAMETER_NAME)
    }

    val finalMethodBody =
      methodToRefactor.replaceBodyWith(methodObjectInvocation)

    pointersToElementsToRenameFrom(finalMethodObjectClassDefinition, finalMethodBody)
  }

  private def assertNoInstanceVariablesAreReferenced(): Unit = {
    methodToRefactor.forEachInstanceVariable { instanceVariable =>
      throw new CannotApplyRefactoringException(
        "Cannot perform refactoring if there are references to instance variables",
        instanceVariable.getTextRange
      )
    }
  }

  private def assertSuperIsNotUsed(): Unit = {
    methodToRefactor.forEachSuperReference { superReference =>
      throw new CannotApplyRefactoringException(
        "Cannot perform refactoring if super is called",
        superReference.getTextRange
      )
    }
  }

  private def assertThereAreOnlyPublicMessageSends(): Unit = {
    messageSendsWithImplicitReceiver.foreach { messageSend =>
      // Possible issue: messageSend.getReference can be null, although we couldn't reproduce that case yet.
      messageSend.getReference.resolve() match {
        case method: RMethod if !method.isPublic =>
          throw new CannotApplyRefactoringException(
            // TODO: Change message: protected methods are also not allowed
            "Cannot perform refactoring if a private method is being called",
            messageSend.getTextRange
          )
        case _ => ()
      }
    }
  }

  private def makeImplicitSelfReferencesExplicit(): Unit = {
    messageSendsWithImplicitReceiver.foreach { messageSend =>
      val messageSendWithExplicitSelf = Parser.parse(s"self.${messageSend.getText}").getFirstChild
      messageSend.replace(messageSendWithExplicitSelf)
    }
  }

  private def selfReferencesFrom(focusedMethod: RMethod) = {
    val selfReferences = new ListBuffer[PsiReference]
    focusedMethod.forEachSelfReference(selfReferences += _.getReference)
    selfReferences.toList
  }

  private def methodObjectClassDefinition  = {
    val methodObjectClass = Parser.parseHeredoc(
      s"""
         |class ${methodObjectClassName}
         |  def ${DEFAULT_INVOCATION_MESSAGE}
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
      parameterNames = parameterNames.appended(DEFAULT_RECEIVER_PARAMETER_NAME)
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
    replaceAllWithInstanceVariableNamed(selfReferences, DEFAULT_RECEIVER_PARAMETER_NAME)
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

  private lazy val methodUsesBlock = methodToRefactor.usesImplicitBlock

  private def methodObjectInvocation = {
    Parser.parse(
      s"${methodObjectClassName}.new${methodObjectConstructorArguments}.${DEFAULT_INVOCATION_MESSAGE}${methodObjectCallArguments}"
    ).asInstanceOf[RCompoundStatement]
  }

  private def methodObjectConstructorArguments = {
    var parameterNames = parameterIdentifiers.map(_.getText)
    if (methodToRefactorUsesSelf) parameterNames = parameterNames.appended("self")

    if (parameterNames.nonEmpty) {
      parameterNames.mkString("(", ", ", ")")
    } else {
      ""
    }
  }

  private def methodObjectCallArguments = {
    if (methodUsesBlock) {
      s"(&${methodToRefactor.blockParameterName.getOrElse(DEFAULT_BLOCK_PARAMETER_NAME)})"
    } else {
      ""
    }
  }

  private lazy val messageSendsWithImplicitReceiver = {
    val messageSends = new ListBuffer[RPossibleCall]
    methodToRefactor.forEachMessageSendWithImplicitReceiver { messageSend =>
      if (!messageSend.textMatches("block_given?")) messageSends.addOne(messageSend)
    }
    messageSends.toList
  }

  private def pointersToElementsToRenameFrom(methodObjectClassDefinition: RClass, methodBody: RCompoundStatement) = {
    val methodObjectClassReferences = List(
      methodObjectClassReferenceFrom(methodBody),
      methodObjectClassDefinition.getClassName
    )

    val callMethodReferences = List(
      callMessageSendFrom(methodBody).getPsiCommand,
      methodObjectClassDefinition.findMethodByName(DEFAULT_INVOCATION_MESSAGE).getNameIdentifier
    )

    List(methodObjectClassReferences, callMethodReferences).map(
      _.map(SmartPointerManager.createPointer(_))
    )
  }

  private def methodObjectClassReferenceFrom(methodObjectInvocation: RCompoundStatement) = {
    val callMessageSend = callMessageSendFrom(methodObjectInvocation)
    val newMessageSend = callMessageSend.getReceiver.asInstanceOf[RPossibleCall]
    newMessageSend.getReceiver.asInstanceOf[RConstant]
  }

  private def callMessageSendFrom(methodObjectInvocation: RCompoundStatement) = {
    methodObjectInvocation.childOfType[RDotReference]()
  }

  private lazy val methodObjectClassName = {
    initialMethodObjectClassNameFrom(methodToRefactor.getMethodName.getText)
  }
}
