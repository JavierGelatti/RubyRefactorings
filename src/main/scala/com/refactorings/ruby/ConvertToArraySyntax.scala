package com.refactorings.ruby

import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.refactorings.ruby.psi.Matchers.Leaf
import com.refactorings.ruby.psi.{Parser, PsiElementExtension, WordsExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RWords
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RArray

class ConvertToArraySyntax extends RefactoringIntention(ConvertToArraySyntax) {
  override def startInWriteAction = false

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementToRefactor(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val wordsElement = elementToRefactor(focusedElement).get

    val stringArray = singleQuotedStringArrayWith(wordsElement)

    runWriteActionWithoutReformatting {
      wordsElement.replace(stringArray)
    }
  }

  private def elementToRefactor(focusedElement: PsiElement) = for {
    wordsElement <- focusedElement.findParentOfType[RWords](treeHeightLimit = 1)
    singleQuoteWords <- wordsElement.getFirstChild match {
      case Leaf(RubyTokenTypes.tWORDS_BEG) => Some(wordsElement)
      case _ => None
    }
  } yield singleQuoteWords

  private def singleQuotedStringArrayWith(wordsElement: RWords)(implicit project: Project) = {
    val values = wordsElement.values
    val separators = wordsElement.wordSeparators

    val singleQuotedValues = separators.zip(values).map {
      case (separator, value) => s"${separator}'${escapeForSingleQuotedString(value)}'"
    }

    Parser
      .parse(s"[${singleQuotedValues.mkString(",")}${separators.last}]", reformat = false)
      .childOfType[RArray]()
  }

  private def escapeForSingleQuotedString(unescapedString: String) = {
    unescapedString
      .replace("\\", "\\\\")
      .replace("'", "\\'")
  }

  private def runWriteActionWithoutReformatting(command: => Unit)(implicit project: Project): Unit = {
    PostprocessReformattingAspect.getInstance(project).disablePostprocessFormattingInside({
      WriteAction.run { () => command }
    })
  }
}

object ConvertToArraySyntax extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Converts a literal word list with '%w' syntax to array syntax"

  override def optionDescription: String = "Convert to [] syntax"
}
