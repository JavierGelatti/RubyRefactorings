package com.refactorings.ruby
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{PsiElementExtension, StringLiteralExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral

class ChangeSingleQuotesToDoubleQuotes extends RefactoringIntention(ChangeSingleQuotesToDoubleQuotes) {
  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    implicit val currentProject: Project = project
    val stringElement = singleQuotedStringFrom(element).get

    val newStringContent = stringElement.getContentValue
      .replace("\\'", "'")
      .replace("\\", "\\\\")
      .replace("#", "\\#")
      .replace("\"", "\\\"")

    val doubleQuotedString = Parser.parseHeredoc(
      s"""
       |"$newStringContent"
      """
    ).childOfType[RStringLiteral]()

    stringElement.replace(doubleQuotedString)
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
