package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.icons.AllIcons.Actions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.{Iconable, TextRange}
import com.intellij.psi.impl.source.PostprocessReformattingAspect
import com.intellij.psi.{PsiElement, PsiFile}
import com.refactorings.ruby.ui.UI

import javax.swing.Icon

abstract class RefactoringIntention(companionObject: RefactoringIntentionCompanionObject) extends PsiElementBaseIntentionAction with Iconable {
  override def getFamilyName: String = companionObject.familyName

  override def getText: String = companionObject.optionDescription

  override def getIcon(flags: Int): Icon = Actions.RefactoringBulb

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit = {
    handlingRefactoringErrors(editor) {
      invoke(editor, element)(project)
    }
  }

  protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit

  protected def handlingRefactoringErrors(editor: Editor)(action: => Unit): Unit = {
    try {
      action
    } catch {
      case ex: CannotApplyRefactoringException =>
        UI.showErrorHint(ex.textRange, editor, ex.getMessage)
    }
  }

  protected def disablePostprocessFormattingInside(action: => Unit)(implicit project: Project): Unit = {
    PostprocessReformattingAspect
      .getInstance(project)
      .disablePostprocessFormattingInside { action }
  }

  override def getElementToMakeWritable(currentFile: PsiFile): PsiElement = currentFile
}

trait RefactoringIntentionCompanionObject {
  self: Singleton =>

  @IntentionFamilyName
  def familyName: String

  @IntentionName
  def optionDescription: String
}

class CannotApplyRefactoringException(message: String, val textRange: TextRange) extends RuntimeException(message)
