package com.refactorings.ruby.psi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.{PsiElement, PsiNamedElement, PsiReference, PsiWhiteSpace}
import com.refactorings.ruby.{CannotApplyRefactoringException, list2Scala}
import com.refactorings.ruby.psi.Matchers.{EndOfLine, Leaf}
import com.refactorings.ruby.psi.Parser.parse
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures._
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.{RBodyStatement, RCompoundStatement, RElseBlock, RElsifBlock}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.{ArgumentInfo, RMethod, Visibility}
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RArgumentToBlock, RCall, RubyCallTypes}
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RDotReference
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.RInstanceVariable
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RFid, RIdentifier, RPseudoConstant}
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor
import org.jetbrains.plugins.ruby.ruby.lang.psi.{RPossibleCall, RPsiElement}

import scala.PartialFunction.cond
import scala.jdk.CollectionConverters.CollectionHasAsScala
import scala.language.reflectiveCalls
import scala.reflect.ClassTag

object PsiElementExtensions {
  implicit class PsiElementExtension[ElementType <: PsiElement](sourceElement: ElementType) {
    def contains(otherElement: PsiElement): Boolean = {
      sourceElement.getTextRange.contains(otherElement.getTextRange)
    }

    def findParentOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : Option[T] = {
      if (treeHeightLimit == 0) return None

      sourceElement.getParent match {
        case null => None
        case element: T if tag.runtimeClass.isInstance(element) && matching(element) => Some(element)
        case otherElement => otherElement.findParentOfType[T](treeHeightLimit - 1)
      }
    }

    def childOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : T = {
      findChildOfType[T](treeHeightLimit, matching).get
    }

    def findChildOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : Option[T] = {
      if (treeHeightLimit == 0) return None

      sourceElement.getChildren
        .find(
          element => tag.runtimeClass.isInstance(element) && matching(element.asInstanceOf[T])
        )
        .map(_.asInstanceOf[T])
        .orElse({
          sourceElement.getChildren
            .view
            .flatMap(directChild => directChild.findChildOfType[T](treeHeightLimit - 1, matching))
            .headOption
        })
    }

    def replaceWithBlock(elementsToReplaceBodyWith: RCompoundStatement): Unit = {
      val statements = elementsToReplaceBodyWith.getStatements
      sourceElement.getParent.addRangeBefore(
        statements.head,
        statements.last,
        sourceElement
      )

      val extraNewLine = sourceElement.getPrevSibling
      sourceElement.delete()
      extraNewLine.delete()
    }

    def putBefore(referenceElement: RPsiElement): ElementType = {
      referenceElement.getParent.addBefore(sourceElement, referenceElement).asInstanceOf[ElementType]
    }

    def putAfter(referenceElement: RPsiElement): ElementType = {
      referenceElement.getParent.addAfter(sourceElement, referenceElement).asInstanceOf[ElementType]
    }

    def isStartOfString: Boolean = {
      cond(sourceElement) {
        case Leaf(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG) => true
      }
    }

    def isLastChildOfParent: Boolean = {
      sourceElement.getParent.getLastChild == sourceElement
    }

    def isFlowInterruptionStatement: Boolean = sourceElement match {
      case _: RReturnStatement | _: RBreakStatement | _: RNextStatement => true
      case raiseSend: RCall if raiseSend.isRaise => true
      case _ => false
    }

    def referencesInside(scope: PsiElement): Iterable[PsiReference] = {
      ReferencesSearch
        .search(sourceElement, new LocalSearchScope(scope))
        .findAll()
        .asScala
    }

    def forEachSelfReference(functionToApply: RPseudoConstant => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRPseudoConstant(pseudoConstant: RPseudoConstant): Unit = {
          super.visitRPseudoConstant(pseudoConstant)

          if (pseudoConstant.textMatches("self")) functionToApply.apply(pseudoConstant)
        }
      })
    }

    def forEachMessageSendWithImplicitReceiver(functionToApply: (RPossibleCall with PsiNamedElement) => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRIdentifier(rIdentifier: RIdentifier): Unit = {
          super.visitRIdentifier(rIdentifier)
          if (rIdentifier.isMessageSendWithImplicitReceiver) functionToApply(rIdentifier)
        }

        override def visitRFid(rFid: RFid): Unit = {
          super.visitRFid(rFid)
          if (rFid.isMessageSendWithImplicitReceiver) functionToApply(rFid)
        }
      })
    }

    def forEachInstanceVariable(functionToApply: RInstanceVariable => Unit): Unit = {
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRInstanceVariable(instanceVariable: RInstanceVariable): Unit = {
          functionToApply(instanceVariable)
        }
      })
    }
  }

  type IfOrUnlessStatement = RExpression with RBlockStatement with RConditionalStatement {
    def getThenBlock: RCompoundStatement
    def getElseBlock: RElseBlock
  }

  implicit class IfOrUnlessStatementExtension
  (sourceElement: IfOrUnlessStatement) extends PsiElementExtension(sourceElement) {
    def hasNoAlternativePaths: Boolean = hasNoElseBlock && hasNoElsifBlocks

    def hasAlternativePaths: Boolean = !hasNoAlternativePaths

    def hasNoElseBlock: Boolean = sourceElement.getElseBlock == null

    def hasNoElsifBlocks: Boolean = getElsifBlocks.isEmpty

    def hasEmptyThenBlock: Boolean = sourceElement.getThenBlock.getStatements.isEmpty

    def negatedKeyword: String = sourceElement match {
      case _: RIfStatement => "unless"
      case _: RUnlessStatement => "if"
    }

    def getElsifBlocks: List[RElsifBlock] = {
      sourceElement match {
        case ifStatement: RIfStatement => ifStatement.getElsifBlocks.toList
        case _: RUnlessStatement => Nil
      }
    }

    def elseBlock: Option[RElseBlock] = Option(sourceElement.getElseBlock)
  }

  implicit class IfStatementExtension
  (sourceElement: RIfStatement) extends IfOrUnlessStatementExtension(sourceElement.asInstanceOf[IfOrUnlessStatement]) {
    def addElsif(elsifToAdd: RElsifBlock)(implicit project: Project): PsiElement = {
      val newElsif = Parser.parseHeredoc(
        """
          |if CONDITION
          |  BODY
          |elsif CONDITION2
          |  ELSIF_BODY
          |end
        """).childOfType[RElsifBlock]()

      newElsif.getBody.getStatements.head.replaceWithBlock(elsifToAdd.getBody)
      newElsif.getCondition.replace(elsifToAdd.getCondition)
      sourceElement.addBefore(newElsif, sourceElement.getLastChild)
    }

    def addElse(elseToAdd: RElseBlock)(implicit project: Project): PsiElement = {
      val newElse = Parser.parseHeredoc(
        """
          |if CONDITION
          |  BODY
          |else
          |  ELSE_BODY
          |end
        """).childOfType[RElseBlock]()

      newElse.getBody.replace(elseToAdd.getBody)
      sourceElement.addBefore(newElse, sourceElement.getLastChild)
    }
  }

  implicit class MessageSendExtension(sourceElement: RCall) extends PsiElementExtension(sourceElement) {
    def lastArgument: Option[RPsiElement] = {
      val arguments = sourceElement.getCallArguments.getElements
      arguments.lastOption.flatMap {
        case _: RArgumentToBlock => arguments.dropRight(1).lastOption
        case x => Some(x)
      }
    }

    def isRaise: Boolean = RubyCallTypes.isRaiseCall(sourceElement)
  }

  implicit class StringLiteralExtension(sourceElement: RStringLiteral) extends PsiElementExtension(sourceElement) {
    def isDoubleQuoted: Boolean = {
      stringBeginningElementType.contains(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG)
    }

    def isSingleQuoted: Boolean = {
      stringBeginningElementType.contains(RubyTokenTypes.tSINGLE_QUOTED_STRING_BEG)
    }

    private def stringBeginningElementType = {
      Option(sourceElement.getStringBeginning).map(_.getNode.getElementType)
    }
  }

  implicit class PossibleCallExtension(sourceElement: RPossibleCall) extends PsiElementExtension(sourceElement) {
    def isMessageSendWithImplicitReceiver: Boolean = {
      sourceElement.isCall && !sourceElement.getParent.isInstanceOf[RDotReference]
    }
  }

  implicit class MethodExtension(sourceElement: RMethod) extends PsiElementExtension(sourceElement) {
    def parameterIdentifiers: List[RIdentifier] = sourceElement.getArguments.map(_.getIdentifier).toList

    def body: RBodyStatement = sourceElement.childOfType[RBodyStatement]()

    def hasParameters: Boolean = sourceElement.getArguments.nonEmpty

    def hasBlockParameter: Boolean = blockParameterInfo.isDefined

    def blockParameterName: Option[String] = blockParameterInfo.map(_.getName)

    def blockParameterInfo: Option[ArgumentInfo] = {
      sourceElement
        .getArgumentInfos
        .find(info => ArgumentInfo.Type.BLOCK.equals(info.getType))
    }

    def addBlockParameter(blockName: String): Int = {
      require(!hasBlockParameter, "The method already has a block parameter")

      sourceElement.getArgumentList.addParameter(s"&${blockName}", ArgumentInfo.Type.BLOCK, true)
    }

    def usesImplicitBlock: Boolean = {
      var usesImplicitBlock = false
      sourceElement.accept(new RubyRecursiveElementVisitor() {
        override def visitRYieldStatement(rYieldStatement: RYieldStatement): Unit = {
          super.visitRYieldStatement(rYieldStatement)
          usesImplicitBlock = true
        }

        override def visitRFid(rFid: RFid): Unit = {
          super.visitRFid(rFid)
          usesImplicitBlock ||= rFid.textMatches("block_given?")
        }
      })
      usesImplicitBlock
    }

    def isPublic: Boolean = Visibility.PUBLIC == sourceElement.getVisibility

    def replaceBodyWith(newBody: RCompoundStatement): RCompoundStatement =
      body.getCompoundStatement
        .replace(newBody).asInstanceOf[RCompoundStatement]

    /**
     * Sometimes this is necessary because the Ruby parser from org.jetbrains.plugins.ruby does not always use the same
     * PsiElement type to represent new lines in the code. The parser can produce either a PsiWhiteSpace('\n') or a
     * PsiElement(end of line).
     *
     * This should be an implementation detail that we shouldn't need to care about. However, sometimes the formatter
     * behaves differently if it receives either a PsiWhiteSpace with newlines or a PsiElement(end of line).
     *
     * In particular, this affects the code modification results when trying to edit methods. This happens because:
     * - When a method has its parameter declarations between parentheses, the parser produces a
     *   "Function argument list" followed by a PsiWhiteSpace('\n').
     * - When a method has its parameter declarations without parentheses, the parser produces a "Command argument list"
     *   followed by a PsiElement(end of line).
     * - After performing a change, the formatter correctly fixes the indentation when there's a PsiWhiteSpace after the
     *   method arguments, but it does not when there's a PsiElement(end of line).
     *
     * To overcome this problem, we normalize the spaces after the parameter list so that they're always represented by
     * a PsiWhiteSpace. In this way, the formatter works fine after performing changes in the PsiElements.
     */
    def normalizeSpacesAfterParameterList()(implicit project: Project): Unit = {
      val argumentList = sourceElement.getArgumentList

      (argumentList.getNextSibling, argumentList.getNextSibling.getNextSibling) match {
        case (EndOfLine(eol), space: PsiWhiteSpace) =>
          val endOfLineAndWhitespace = parse(s"\n${space.getText}")

          // Ordering here matters: swapping these two lines causes a PsiInvalidElementAccessException
          space.replace(endOfLineAndWhitespace)
          eol.delete()
        case _ => ()
      }
    }
  }

  implicit class EditorExtension(editor: Editor) {
    def getSelectionStart: Int = editor.getSelectionModel.getSelectionStart

    def getSelectionEnd: Int = editor.getSelectionModel.getSelectionEnd

    def hasSelection: Boolean = editor.getSelectionModel.hasSelection

    def getSelectedText: String = {
      Option(editor.getSelectionModel.getSelectedText).getOrElse("")
    }

    def replaceSelectionWith(newText: String): Unit = {
      editor.getDocument.replaceString(
        this.getSelectionStart,
        this.getSelectionEnd,
        newText
      )
      editor.getCaretModel.getCurrentCaret.removeSelection()
    }

    def getCaretOffset: Int = {
      editor.getCaretModel.getCurrentCaret.getOffset
    }

    def moveCaretTo(targetOffset: Int): Unit = {
      editor.getCaretModel.getCurrentCaret.moveToOffset(targetOffset)
    }
  }
}
