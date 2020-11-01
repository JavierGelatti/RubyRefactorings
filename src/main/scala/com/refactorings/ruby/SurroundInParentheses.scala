package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.IntentionFamilyName
import com.intellij.codeInspection.util.IntentionName
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.jetbrains.plugins.ruby.ruby.lang.RubyLanguage

class SurroundInParentheses extends PsiElementBaseIntentionAction {
  @IntentionName override def getText = "Surround in parentheses"

  @IntentionFamilyName override def getFamilyName = "Surround"

  override def invoke(project: Project, editor: Editor, elementPosta: PsiElement) = {
    val element = if (elementPosta.isInstanceOf[LeafPsiElement]) elementPosta.getParent else elementPosta
    val elementFromText = getPsiElement(project, "(x)")
    elementFromText.getFirstChild.getChildren.apply(0).replace(element.getFirstChild)
    element.getFirstChild.replace(elementFromText)
  }

  private def getPsiElement(project: Project, code: String) = {
    val elementFromText = PsiFileFactory.getInstance(project)
      .createFileFromText("DUMMY.rb", RubyFileType.RUBY, code)
      .getFirstChild

    elementFromText
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    if (!element.getLanguage.is(RubyLanguage.INSTANCE)) return false
    if (element.isInstanceOf[PsiWhiteSpace]) return false

    true
  }
}