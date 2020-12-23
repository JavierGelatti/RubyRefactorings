package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Matchers.Leaf
import com.refactorings.ruby.psi.{Parser, PsiElementExtension, WordsExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RWords
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RArray

class ConvertToArraySyntax extends RefactoringIntention(ConvertToArraySyntax) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val wordsElement = elementToRefactor(focusedElement).get

    val stringArray = singleQuotedStringArrayWith(wordsElement.values)

    wordsElement.replace(stringArray)
  }

  private def singleQuotedStringArrayWith(values: List[String])(implicit project: Project) = {
    val arrayCode = if (values.isEmpty) {
      "[]"
    } else {
      val escapedValues = values.map(escapeForSingleQuotedString)
      s"['${escapedValues.mkString("', '")}']"
    }

    Parser.parse(arrayCode).childOfType[RArray]()
  }

  private def escapeForSingleQuotedString(unescapedString: String) = {
    unescapedString
      .replace("\\", "\\\\")
      .replace("'", "\\'")
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
