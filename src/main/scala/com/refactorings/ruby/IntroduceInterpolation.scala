package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{EditorExtension, LeafPsiElementExtension, PsiElementExtension, PsiFileExtension, StringLiteralExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.{RExpressionSubstitution, RStringLiteral}

class IntroduceInterpolation extends RefactoringIntention(IntroduceInterpolation) {
  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    implicit val currentProject: Project = project
    val (stringLiteralToRefactor, focusedStartElement, focusedEndElement) = elementsToRefactor(focusedElement, editor).get

    val (antesDelCaret, enElCaret, despuesDelCaret) = {
      val (a, _) = focusedStartElement.partitionTextOn(editor.getSelectionModel.getSelectionStart)

      val (_, c) = focusedEndElement.partitionTextOn(editor.getSelectionModel.getSelectionEnd)
      val b = Option(editor.getSelectionModel.getSelectedText).getOrElse("")

      (a, b, c)
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
      stringLiteralToRefactor.addBefore(element, focusedStartElement)
    })

    if (focusedStartElement.isStringContent && focusedEndElement.isStringContent) {
      stringLiteralToRefactor.deleteChildRange(focusedStartElement, focusedEndElement)
    } else if (focusedStartElement.isStringContent) {
      focusedStartElement.delete()
    }

    editor.getCaretModel.getPrimaryCaret.moveToOffset(
      stringLiteralToRefactor.childMarkedWith(stringInsideMark).getTextOffset + 1
    )
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement, editor).isDefined
  }

  private def elementsToRefactor(initialElement: PsiElement, editor: Editor) = {
    val containingFile = initialElement.getContainingFile
    for {
      focusedStartElement <- containingFile.leafElementAt(editor.getSelectionStart)
      focusedEndElement <- containingFile.leafElementAt(editor.getSelectionEnd)
      stringLiteralToRefactor <- focusedStartElement.findParentOfType[RStringLiteral](treeHeightLimit = 1)
      if stringLiteralToRefactor.isDoubleQuoted &&
        !focusedStartElement.isOfType(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG)
    } yield (stringLiteralToRefactor, focusedStartElement, focusedEndElement)
  }
}

object IntroduceInterpolation extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce new interpolation point inside of string"

  override def optionDescription: String = "Introduce interpolation"
}




