package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement

abstract class RefactoringIntention(companionObject: RefactoringIntentionCompanionObject) extends PsiElementBaseIntentionAction {
  override def getFamilyName: String = companionObject.familyName

  override def getText: String = companionObject.optionDescription

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean

  override def invoke(project: Project, editor: Editor, element: PsiElement): Unit
}

trait RefactoringIntentionCompanionObject {
  self: Singleton =>

  @IntentionFamilyName
  def familyName: String

  @IntentionName
  def optionDescription: String
}
