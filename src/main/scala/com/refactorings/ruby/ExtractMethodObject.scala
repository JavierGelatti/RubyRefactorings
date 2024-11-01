package com.refactorings.ruby

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.refactorings.ruby.ExtractMethodObjectApplier.{initialMethodObjectClassNameFrom, objectPrivateMethods}
import com.refactorings.ruby.psi.{MethodExtension, Parser, PossibleCallExtension, PsiElementExtension}
import com.refactorings.ruby.ui.CodeCompletionTemplate
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPossibleCall
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RDotReference
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.RInstanceVariable
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RConstant, RIdentifier}

import scala.collection.mutable.ListBuffer
import scala.language.implicitConversions

class ExtractMethodObject extends RefactoringIntention(ExtractMethodObject) {

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementToRefactor(focusedElement).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit project: Project): Unit = {
    val methodToRefactor = elementToRefactor(focusedElement).get

    val elementsToRename = WriteAction.compute {
      new ExtractMethodObjectApplier(methodToRefactor, project).apply()
    }

    CodeCompletionTemplate.startIn(
      editor,
      rootElement = methodToRefactor.container,
      elementsToRename
    )
  }

  private def elementToRefactor(focusedElement: PsiElement) = {
    focusedElement.findParentOfType[RMethod](treeHeightLimit = 3)
  }

  override def startInWriteAction = false
}

object ExtractMethodObject extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Extracts a method object based on the contents of an existing method"

  override def optionDescription: String = "Extract method object"
}

private class ExtractMethodObjectApplier(methodToRefactor: RMethod, implicit val project: Project) {
  private val DEFAULT_INVOCATION_MESSAGE = "call"
  private val DEFAULT_RECEIVER_PARAMETER_NAME = "original_receiver"
  private val DEFAULT_BLOCK_PARAMETER_NAME = "block"

  private val parameterIdentifiers: List[RIdentifier] = methodToRefactor.parameterIdentifiers
  private var selfReferences: List[PsiElement] = _

  def apply(): List[List[SmartPsiElementPointer[PsiElement]]] = {
    assertNoInstanceVariablesAreReferenced()
    assertNoClassVariablesAreReferenced()
    assertSuperIsNotUsed()
    assertThereAreOnlyPublicMessageSends()

    makeImplicitSelfReferencesExplicit()

    selfReferences = selfReferencesFrom(methodToRefactor)

    val finalMethodObjectClassDefinition =
      methodObjectClassDefinition.putAfter(methodToRefactorElementInContainer)

    if (methodUsesBlock && !methodToRefactor.hasBlockParameter) {
      methodToRefactor.addBlockParameter(blockParameterName)
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

  private def assertNoClassVariablesAreReferenced(): Unit = {
    methodToRefactor.forEachClassVariable { classVariable =>
      throw new CannotApplyRefactoringException(
        "Cannot perform refactoring if there are references to class variables",
        classVariable.getTextRange
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
            "Cannot perform refactoring if a private/protected method is being called",
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
    focusedMethod.body.forEachSelfReference { selfReference =>
      selfReferences += selfReference.getReference
    }

    selfReferences
      .map(_.getElement)
      .toList
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
    var parameterNames = originalParameterNames
    if (methodToRefactorUsesSelf) parameterNames = parameterNames.prepended(receiverParameterName)

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
    replaceAllWithInstanceVariableNamed(selfReferences, receiverParameterName)
    parameterIdentifiers.foreach { parameterIdentifier =>
      replaceAllWithInstanceVariableNamed(
        parameterIdentifier.referencesInside(methodToRefactor.body),
        parameterIdentifier.getText
      )
    }

    methodToRefactor.body
  }

  private lazy val receiverParameterName = {
    chooseName(DEFAULT_RECEIVER_PARAMETER_NAME, satisfying = !originalParameterNames.contains(_))
  }

  private lazy val originalParameterNames = parameterIdentifiers.map(_.getText)

  private lazy val methodToRefactorElementInContainer = methodToRefactor
    .container
    .getChildren
    .find(_.contains(methodToRefactor))
    .get

  private def replaceAllWithInstanceVariableNamed(references: List[PsiElement], parameterName: String): Unit = {
    val instanceVariableRead = Parser.parse(s"@$parameterName").childOfType[RInstanceVariable]()

    references.foreach(
      _.replace(instanceVariableRead)
    )
  }

  private lazy val methodUsesBlock = methodToRefactor.usesImplicitBlock

  private lazy val blockParameterName = {
    chooseName(DEFAULT_BLOCK_PARAMETER_NAME, satisfying = !originalParameterNames.contains(_))
  }

  private def chooseName(default: String, satisfying: String => Boolean) = {
    var chosenName = default
    var tries = 0

    while (!satisfying.apply(chosenName)) {
      tries += 1
      chosenName = s"${default}_${tries}"
    }

    chosenName
  }

  private def methodObjectInvocation = {
    Parser.parse(
      s"${methodObjectClassName}.new${methodObjectConstructorArguments}.${DEFAULT_INVOCATION_MESSAGE}${methodObjectCallArguments}"
    ).asInstanceOf[RCompoundStatement]
  }

  private def methodObjectConstructorArguments = {
    var parameterNames = originalParameterNames
    if (methodToRefactorUsesSelf) parameterNames = parameterNames.prepended("self")

    if (parameterNames.nonEmpty) {
      parameterNames.mkString("(", ", ", ")")
    } else {
      ""
    }
  }

  private def methodObjectCallArguments = {
    if (methodUsesBlock) {
      s"(&${methodToRefactor.blockParameterName.get})"
    } else {
      ""
    }
  }

  private lazy val messageSendsWithImplicitReceiver = {
    val messageSends = new ListBuffer[RPossibleCall]
    methodToRefactor.body.forEachMessageSendWithImplicitReceiver { messageSend =>
      if (!objectPrivateMethods.contains(messageSend.getCommand)) {
        messageSends.addOne(messageSend)
      }
    }
    messageSends.toList
  }

  private def pointersToElementsToRenameFrom(methodObjectClassDefinition: RClass, methodBody: RCompoundStatement) = {
    List(
      methodObjectClassReferencesIn(methodObjectClassDefinition, methodBody),
      callMethodReferencesIn(methodObjectClassDefinition, methodBody),
      originalReceiverReferencesIn(methodObjectClassDefinition)
    ).map(
      _.map(SmartPointerManager.createPointer(_))
    )
  }

  private def methodObjectClassReferencesIn(methodObjectClassDefinition: RClass, methodBody: RCompoundStatement) = {
    List(
      methodObjectClassReferenceFrom(methodBody),
      methodObjectClassDefinition.getClassName
    )
  }

  private def methodObjectClassReferenceFrom(methodObjectInvocation: RCompoundStatement) = {
    val callMessageSend = callMessageSendFrom(methodObjectInvocation)
    val newMessageSend = callMessageSend.getReceiver.asInstanceOf[RPossibleCall]
    newMessageSend.getReceiver.asInstanceOf[RConstant]
  }

  private def callMethodReferencesIn(methodObjectClassDefinition: RClass, methodBody: RCompoundStatement) = {
    List(
      callMessageSendFrom(methodBody).getPsiCommand,
      methodObjectClassDefinition.findMethodByName(DEFAULT_INVOCATION_MESSAGE).getNameIdentifier
    )
  }

  private def callMessageSendFrom(methodObjectInvocation: RCompoundStatement) = {
    methodObjectInvocation.childOfType[RDotReference]()
  }

  private def originalReceiverReferencesIn(methodObjectClassDefinition: RClass): List[PsiElement] = {
    if (!methodToRefactorUsesSelf) return List()

    referencesToOriginalReceiverParameterIn(methodObjectClassDefinition) ++
      referencesToOriginalReceiverInstanceVariableIn(methodObjectClassDefinition)
  }

  private def referencesToOriginalReceiverParameterIn(methodObjectClassDefinition: RClass) = {
    val methodObjectConstructor = methodObjectClassDefinition.findMethodByName("initialize")

    val originalReceiverParameter = methodObjectConstructor
      .getArguments
      .find(arg => arg.textMatches(receiverParameterName))
      .get
      .getIdentifier

    originalReceiverParameter
      .referencesInside(methodObjectConstructor)
      .movingToStart(originalReceiverParameter)
  }

  private def referencesToOriginalReceiverInstanceVariableIn(methodObjectClassDefinition: RClass) = {
    methodObjectClassDefinition
      .instanceVariableNamed(s"@${receiverParameterName}")
      .get
      .referencesInside(methodObjectClassDefinition)
  }

  private lazy val methodObjectClassName = {
    initialMethodObjectClassNameFrom(methodToRefactor.getNameIdentifier.getText)
  }
}

object ExtractMethodObjectApplier {
  // Obtained by manually filtering the result of running Object.new.private_methods
  private val objectPrivateMethods = List(
    "Array",
    "Complex",
    "DelegateClass",
    "Float",
    "Hash",
    "Integer",
    "Pathname",
    "Rational",
    "String",
    "URI",
    "__callee__",
    "__dir__",
    "__method__",
    "`",
    "abort",
    "at_exit",
    "autoload",
    "autoload?",
    "binding",
    "block_given?",
    "caller",
    "caller_locations",
    "catch",
    "eval",
    "exec",
    "exit",
    "exit!",
    "fail",
    "fork",
    "format",
    "gem",
    "gem_original_require",
    "gets",
    "global_variables",
    "irb_binding",
    "iterator?",
    "lambda",
    "load",
    "local_variables",
    "loop",
    "open",
    "p",
    "pp",
    "print",
    "printf",
    "proc",
    "putc",
    "puts",
    "raise",
    "rand",
    "readline",
    "readlines",
    "require",
    "require_relative",
    "respond_to_missing?",
    "select",
    "set_trace_func",
    "sleep",
    "spawn",
    "sprintf",
    "srand",
    "syscall",
    "system",
    "test",
    "throw",
    "timeout",
    "trace_var",
    "trap",
    "untrace_var",
    "warn"
  )

  // See https://docs.ruby-lang.org/en/2.3.0/syntax/methods_rdoc.html#label-Method+Names
  private[this] val messageName: Map[String, String] = Map(
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

  private[this] def isWriteMethod(methodName: String) = {
    methodName.endsWith("=") && (methodName.head.isLetter || methodName.head == '_')
  }
}
