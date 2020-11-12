package com.refactorings.ruby

import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.refactorings.ruby.psi.PsiElementExtensions.EditorExtension
import org.jetbrains.plugins.ruby.ruby.actions.intention.RubyIntroduceLocalVariableIntention

class IntroduceVariableInsideString extends RefactoringIntention(IntroduceVariableInsideString) {
  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    val containingFile = focusedElement.getContainingFile
    new IntroduceInterpolation().invoke(project, editor, focusedElement)

    PsiDocumentManager.getInstance(project).performForCommittedDocument(editor.getDocument, () => {
      CommandProcessor.getInstance.executeCommand(project, () => {
        val elementAtCaret = containingFile.findElementAt(editor.getCaretModel.getOffset)
        new RubyIntroduceLocalVariableIntention().invoke(project, editor, elementAtCaret)
      }, this.getText, null)
    })
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    editor.hasSelection && new IntroduceInterpolation().isAvailable(project, editor, focusedElement)
  }
}

object IntroduceVariableInsideString extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce new variable based on substring from string literal"

  override def optionDescription: String = "Introduce local variable"
}


