package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{EditorExtension, LeafPsiElementExtension, PsiElementExtension, StringLiteralExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.{RExpressionSubstitution, RStringLiteral}

import scala.PartialFunction.condOpt

class IntroduceInterpolation extends RefactoringIntention(IntroduceInterpolation) {
  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    implicit val currentProject: Project = project
    val (stringLiteralToRefactor, focusedStringPart) = elementsToRefactor(focusedElement, editor).get

    val (antesDelCaret, enElCaret, despuesDelCaret) = if (editor.getSelectionModel.hasSelection) {
      val (a, _, _) = focusedStringPart.partitionTextOn(
        editor.getSelectionModel.getSelectionStart,
        editor.getSelectionModel.getSelectionStart
      )

      val lala = Option(
        focusedStringPart.getContainingFile.findElementAt(editor.getSelectionModel.getSelectionEnd)
      ).collect { case leafElement: LeafPsiElement => leafElement }.get
      val (_, _, c) = lala.partitionTextOn(
        editor.getSelectionModel.getSelectionEnd,
        editor.getSelectionModel.getSelectionEnd
      )
      val b = editor.getSelectionModel.getSelectedText

      (a, b, c)
    } else {
      focusedStringPart.partitionTextOn(
        editor.getSelectionModel.getSelectionStart,
        editor.getSelectionModel.getSelectionEnd
      )
    }
    val template = Parser.parseHeredoc(
      s"""
        |"${antesDelCaret}#{"${enElCaret}"}${despuesDelCaret}"
      """)
    val newStringLiteral = template.childOfType[RStringLiteral]()
    val expressionSubstitution: RExpressionSubstitution = newStringLiteral.getExpressionSubstitutions.head
    val stringInside = expressionSubstitution.getCompoundStatement.getStatements.head.asInstanceOf[RStringLiteral]

    val stringInsideMark = stringInside.mark()
    newStringLiteral.getPsiContent.forEach(element => {
      stringLiteralToRefactor.addBefore(element, focusedStringPart)
    })

    if (editor.getSelectionModel.hasSelection) {
      val lala = Option(
        focusedStringPart.getContainingFile.findElementAt(editor.getSelectionModel.getSelectionEnd)
      ).collect { case leafElement: LeafPsiElement => leafElement }.get

      if (lala == focusedStringPart) {
        if (focusedStringPart.isStringContent) {
          focusedStringPart.delete()
        }
      } else {
        if (focusedStringPart.isStringContent && lala.isStringContent) {
          stringLiteralToRefactor.deleteChildRange(focusedStringPart, lala)
        } else if (focusedStringPart.isStringContent) {
          focusedStringPart.delete()
        }
      }
    } else {
      if (focusedStringPart.isStringContent) {
        focusedStringPart.delete()
      }
    }

    editor.getCaretModel.getPrimaryCaret.moveToOffset(
      stringLiteralToRefactor.childMarkedWith(stringInsideMark).getTextOffset + 1
    )
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement, editor).isDefined
  }

  private def elementsToRefactor(initialElement: PsiElement, editor: Editor) = {
    for {
      focusedElement <- Option(
        initialElement.getContainingFile.findElementAt(editor.getSelectionModel.getSelectionStart)
      ).collect { case leafElement: LeafPsiElement => leafElement }
      stringLiteralToRefactor <- focusedElement.findParentOfType[RStringLiteral](treeHeightLimit = 1)
      if stringLiteralToRefactor.isDoubleQuoted
      focusedStringPart <- condOpt(focusedElement) {
        case stringPart: LeafPsiElement
          if !stringPart.isOfType(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG) => stringPart
      }
    } yield (stringLiteralToRefactor, focusedStringPart)
  }
}

object IntroduceInterpolation extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce new interpolation point inside of string"

  override def optionDescription: String = "Introduce interpolation"
}




