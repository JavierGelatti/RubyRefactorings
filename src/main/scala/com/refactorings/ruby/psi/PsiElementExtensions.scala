package com.refactorings.ruby.psi

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.list2Scala
import com.refactorings.ruby.psi.Matchers.Leaf
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.{RCompoundStatement, RElseBlock, RElsifBlock}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.{RBlockStatement, RConditionalStatement, RIfStatement, RUnlessStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RExpression
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RArgumentToBlock, RCall}

import scala.PartialFunction.cond
import scala.language.reflectiveCalls
import scala.reflect.ClassTag

object PsiElementExtensions {
  implicit class PsiElementExtension(sourceElement: PsiElement) {
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

    def replaceWith(elementsToReplaceBodyWith: RCompoundStatement): Unit = {
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

    def isStartOfString: Boolean = {
      cond(sourceElement) {
        case Leaf(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG) => true
      }
    }

    def isLastChildOfParent: Boolean = {
      sourceElement.getParent.getLastChild == sourceElement
    }
  }

  type IfOrUnlessStatement = RExpression with RBlockStatement with RConditionalStatement {
    def getThenBlock: RCompoundStatement
    def getElseBlock: RElseBlock
  }

  implicit class IfOrUnlessStatementExtension
  (sourceElement: IfOrUnlessStatement) extends PsiElementExtension(sourceElement) {
    def hasNoAlternativePaths: Boolean = hasNoElseBlock && hasNoElsifBlocks

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

      newElsif.getBody.getStatements.head.replaceWith(elsifToAdd.getBody)
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

    def isRaise: Boolean = sourceElement.getCommand == "raise"
  }

  implicit class StringLiteralExtension(sourceElement: RStringLiteral) extends PsiElementExtension(sourceElement) {
    def isDoubleQuoted: Boolean = {
      stringBeginningElementType == RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG
    }

    def isSingleQuoted: Boolean = {
      stringBeginningElementType == RubyTokenTypes.tSINGLE_QUOTED_STRING_BEG
    }

    private def stringBeginningElementType = {
      // FIXME: We're getting java.lang.NullPointerException here, but we couldn't reproduce the issue yet.
      // I suspect the problem is that getStringBeginning returns null, but I don't know in which case it does.
      try {
        sourceElement
          .getStringBeginning
          .getNode
          .getElementType
      } catch {
        case originalException: NullPointerException =>
          throw new RuntimeException(s"NullPointerException for element ${sourceElement.getText}", originalException)
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
