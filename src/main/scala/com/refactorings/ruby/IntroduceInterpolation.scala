package com.refactorings.ruby

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.refactorings.ruby.psi.PsiElementExtensions.{EditorExtension, LeafPsiElementExtension, PsiElementExtension, StringLiteralExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.{RExpressionSubstitution, RStringLiteral}

class IntroduceInterpolation extends RefactoringIntention(IntroduceInterpolation) {
  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    val (startElement, endElement) = elementsToRefactor(focusedElement, editor).get
    val start = startElement match {
      case s1: RExpressionSubstitution => s1.getTextRange.getStartOffset
      case _ => editor.getSelectionStart
    }
    val end = endElement match {
      case s1: RExpressionSubstitution => s1.getTextRange.getEndOffset
      case _ => editor.getSelectionEnd
    }
    val selectionModel = editor.getSelectionModel
    if (selectionModel.getSelectionStart != start || selectionModel.getSelectionEnd != end) {
      selectionModel.setSelection(start, end)
    }

    WriteCommandAction.runWriteCommandAction(project, {
      editor.getDocument.replaceString(
        selectionModel.getSelectionStart,
        selectionModel.getSelectionEnd,
        s"""#{"${Option(selectionModel.getSelectedText).getOrElse("")}"}"""
      )
    })
    editor.getCaretModel.getCurrentCaret.removeSelection()
    editor.getCaretModel.getCurrentCaret.moveToOffset(start + 3)
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement, editor).isDefined
  }

  private def elementsToRefactor(initialElement: PsiElement, editor: Editor) = {
    val containingFile = initialElement.getContainingFile
    for {
      focusedStartElement <- {
          Option(containingFile.findElementAt(editor.getSelectionStart)).collect {
            case x:LeafPsiElement if x.isOfType(RubyTokenTypes.tSTRING_DBEG) => x.getParent
            case x => x
          }
      }
      focusedEndElement <- {
        Option(containingFile.findElementAt(editor.getSelectionEnd)).collect {
          case x:LeafPsiElement if x.isOfType(RubyTokenTypes.tSTRING_DEND) => x.getParent
          case x => x
        }
      }
      stringParentFromStart <- focusedStartElement.findParentOfType[RStringLiteral](treeHeightLimit = 1)
      stringParentFromEnd <- focusedEndElement.findParentOfType[RStringLiteral](treeHeightLimit = 1)
      if stringParentFromStart == stringParentFromEnd
      stringLiteralToRefactor = stringParentFromStart
      if stringLiteralToRefactor.isDoubleQuoted &&
        !(focusedStartElement.isInstanceOf[LeafPsiElement] &&
          focusedStartElement.asInstanceOf[LeafPsiElement].isOfType(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG))
    } yield (focusedStartElement, focusedEndElement)
  }
}

object IntroduceInterpolation extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce new interpolation point inside of string"

  override def optionDescription: String = "Introduce interpolation"
}




