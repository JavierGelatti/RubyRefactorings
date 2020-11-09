package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{EditorExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral

class IntroduceInterpolation extends RefactoringIntention(IntroduceInterpolation) {
  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    implicit val currentProject = project
    val stringLiteral = focusedElement.parentOfType[RStringLiteral]()

    val puntoCaret = editor.getCaretModel.getOffset - focusedElement.getTextRange.getStartOffset
    val antesDelCaret = focusedElement.getText.substring(0, puntoCaret)
    val despuesDelCaret = focusedElement.getText.substring(puntoCaret)
    val template = Parser.parseHeredoc(
      s"""
        |"${antesDelCaret}#{""}${despuesDelCaret}"
      """)
    val newStringLiteral = template.childOfType[RStringLiteral]()
    val expressionSubstitution = newStringLiteral.getExpressionSubstitutions.head
    val stringInside = expressionSubstitution.getCompoundStatement.getStatements.head.asInstanceOf[RStringLiteral]
    val mark = stringInside.mark()
    val newElement = stringLiteral.replace(newStringLiteral)
    editor.selectElement(newElement.childMarkedWith(mark))
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    true
  }
}

object IntroduceInterpolation extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce new interpolation point inside of string"

  override def optionDescription: String = "Introduce interpolation"
}




