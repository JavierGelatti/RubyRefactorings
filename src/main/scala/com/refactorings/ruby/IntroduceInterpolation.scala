package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Matchers.Leaf
import com.refactorings.ruby.psi.PsiElementExtensions.{EditorExtension, PsiElementExtension, StringLiteralExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.{RExpressionSubstitution, RStringLiteral}

class IntroduceInterpolation extends RefactoringIntention(IntroduceInterpolation) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    extendSelectionToCoverExistingInterpolations(focusedElement, editor)

    val (prefix, content, suffix) = if (editor.hasSelection) {
      ("#{\"", editor.getSelectedText, "\"}")
    } else {
      ("#{", "", "}")
    }
    val finalCaretPosition = editor.getSelectionStart + prefix.length
    editor.replaceSelectionWith(prefix + content + suffix)
    editor.moveCaretTo(finalCaretPosition)
  }

  private def extendSelectionToCoverExistingInterpolations(focusedElement: PsiElement, editor: Editor): Unit = {
    val selectionModel = editor.getSelectionModel
    val (currentSelectionStart, currentSelectionEnd) = (selectionModel.getSelectionStart, selectionModel.getSelectionEnd)
    val (startElement, endElement) = elementsToRefactor(focusedElement, editor).get

    val start = startElement match {
      case s1: RExpressionSubstitution => s1.getTextRange.getStartOffset
      case escape@Leaf(RubyTokenTypes.tESCAPE_SEQUENCE) => escape.getStartOffset
      case _ => currentSelectionStart
    }
    val end = endElement match {
      case s1: RExpressionSubstitution => s1.getTextRange.getEndOffset
      case escape@Leaf(RubyTokenTypes.tESCAPE_SEQUENCE) => escape.getStartOffset
      case _ => currentSelectionEnd
    }
    if ((currentSelectionStart, currentSelectionEnd) != (start, end)) {
      if (start == end) { editor.moveCaretTo(end) }
      selectionModel.setSelection(start, end)
    }
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement, editor).isDefined
  }

  private def elementsToRefactor(initialElement: PsiElement, editor: Editor) = {
    val containingFile = initialElement.getContainingFile
    def findElementAt(offset: Int) = Option(containingFile.findElementAt(offset))

    for {
      focusedStartElement <- findElementAt(editor.getSelectionStart).mapIf {
        case leafElement@Leaf(RubyTokenTypes.tSTRING_DBEG) => leafElement.getParent
      }
      focusedEndElement <- findElementAt(editor.getSelectionEnd).mapIf {
        case leafElement@Leaf(RubyTokenTypes.tSTRING_DBEG) => leafElement.getParent.getPrevSibling
        case leafElement@Leaf(RubyTokenTypes.tSTRING_DEND) => leafElement.getParent
      }
      stringParentFromStart <- focusedStartElement.findParentOfType[RStringLiteral](treeHeightLimit = 1)
      stringParentFromEnd <- focusedEndElement.findParentOfType[RStringLiteral](treeHeightLimit = 1)
      if stringParentFromStart == stringParentFromEnd
      stringLiteralToRefactor = stringParentFromStart
      if stringLiteralToRefactor.isDoubleQuoted && !focusedStartElement.isStartOfString
    } yield (focusedStartElement, focusedEndElement)
  }
}

object IntroduceInterpolation extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce new interpolation point inside of string"

  override def optionDescription: String = "Introduce interpolation"
}
