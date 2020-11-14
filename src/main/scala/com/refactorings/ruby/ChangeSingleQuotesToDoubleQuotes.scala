package com.refactorings.ruby
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{EditorExtension, PsiElementExtension, StringLiteralExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral

class ChangeSingleQuotesToDoubleQuotes extends RefactoringIntention(ChangeSingleQuotesToDoubleQuotes) {
  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    implicit val currentProject: Project = project
    val stringElement = singleQuotedStringFrom(element).get

    val originalStringContent = stringElement.getContentValue
    val newStringContent = escapeForDoubleQuotedString(originalStringContent)

    val doubleQuotedString = Parser.parseHeredoc(
      s"""
       |"$newStringContent"
      """
    ).childOfType[RStringLiteral]()

    performReplacementMaintainingCaretPosition(editor, stringElement) {
      stringElement.replace(doubleQuotedString)
    }
  }

  private def performReplacementMaintainingCaretPosition
  (editor: Editor, stringElement: RStringLiteral)(performReplacement: => PsiElement): Unit = {
    val textBeforeCaret = getTextBeforeCaret(editor, stringElement)
    val escapedTextBeforeCaret = escapeForDoubleQuotedString(
      unescapeFromSingleQuotedString(textBeforeCaret)
    )

    val newStringElement = performReplacement

    editor.moveCaretTo(newStringElement.getTextOffset + escapedTextBeforeCaret.length)
  }

  private def getTextBeforeCaret(editor: Editor, stringElement: RStringLiteral) = {
    val originalText = stringElement.getText
    val caretOffsetRelativeToStringStart = editor.getCaretOffset - stringElement.getTextOffset

    val textBeforeCaret = originalText.take(caretOffsetRelativeToStringStart)
    val textAfterCaret = originalText.substring(caretOffsetRelativeToStringStart)
      .dropRight(1) // Discard the closing quote mark

    val caretInsideEscape = textBeforeCaret.endsWith("\\") && textAfterCaret.startsWith("'")
    if (caretInsideEscape) {
      textBeforeCaret + "'"
    } else {
      textBeforeCaret
    }
  }

  private def unescapeFromSingleQuotedString(textBeforeCaret: String) = {
    textBeforeCaret
      .replace("\\'", "'")
      .replace("\\\\", "\\")
  }

  private def escapeForDoubleQuotedString(value: String) = {
    value
      .replace("\\", "\\\\")
      .replace("#", "\\#")
      .replace("\"", "\\\"")
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    singleQuotedStringFrom(element).isDefined
  }

  private def singleQuotedStringFrom(element: PsiElement) = {
    element.findParentOfType[RStringLiteral]().filter(string => string.isSingleQuoted)
  }
}

object ChangeSingleQuotesToDoubleQuotes extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Change a single-quoted string literal to have double quotes"

  override def optionDescription: String = "Convert to double quoted"
}
