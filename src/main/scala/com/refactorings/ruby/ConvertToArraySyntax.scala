package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Matchers.{EscapeSequence, Leaf}
import com.refactorings.ruby.psi.{Parser, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RWords
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RArray

class ConvertToArraySyntax extends RefactoringIntention(ConvertToArraySyntax) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val wordsElement = elementToRefactor(focusedElement).get

    val stringArray = singleQuotedStringArrayWith(valuesFrom(wordsElement))

    wordsElement.replace(stringArray)
  }

  private def singleQuotedStringArrayWith(values: List[String])(implicit project: Project) = {
    val escapedValues = values.map(escapeForSingleQuotedString)
    Parser
      .parse(s"['${escapedValues.mkString("', '")}']")
      .childOfType[RArray]()
  }

  private def escapeForSingleQuotedString(unescapedString: String) = {
    unescapedString
      .replace("\\", "\\\\")
      .replace("'", "\\'")
  }

  def valuesFrom(sourceElement: RWords): List[String] = {
    val content = sourceElement.getPsiContent
    val contentWithoutDelimiters = content.drop(1).dropRight(1)

    contentWithoutDelimiters
      .map(unescape)
      .mkString("")
      .trim
      .split("\\s+")
      .toList
  }

  private def unescape(element: PsiElement) = element match {
    case EscapeSequence(escape) => escape.getText.stripPrefix("\\")
    case other => other.getText
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementToRefactor(element).isDefined
  }

  private def elementToRefactor(focusedElement: PsiElement) = for {
    wordsElement <- focusedElement.findParentOfType[RWords](treeHeightLimit = 1)
    singleQuoteWords <- wordsElement.getFirstChild match {
      case Leaf(RubyTokenTypes.tWORDS_BEG) => Some(wordsElement)
      case _ => None
    }
  } yield singleQuoteWords
}

object ConvertToArraySyntax extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Converts a literal word list with '%w' syntax to array syntax"

  override def optionDescription: String = "Convert to [] syntax"
}
