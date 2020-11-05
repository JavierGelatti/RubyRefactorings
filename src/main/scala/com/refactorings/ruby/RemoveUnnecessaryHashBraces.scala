package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.RemoveUnnecessaryHashBraces.optionDescription
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RAssocList
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall

class RemoveUnnecessaryHashBraces extends PsiElementBaseIntentionAction {
  @IntentionName override def getText: String = optionDescription

  @IntentionFamilyName override def getFamilyName = "Remove unnecessary hash braces in message send"

  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    implicit val currentProject = project
    val messageSendTemplate = getPsiElement(
      """
        |MESSAGE()
    """.trim.stripMargin)

    val messageSend = findChild[RCall](messageSendTemplate).get
    val emptyArguments = messageSend.getCallArguments

    val messageSendToRefactor = findParent[RCall](focusedElement).get
    emptyArguments.add(
      messageSendToRefactor.getCallArguments.getFirstElement.asInstanceOf[RAssocList].getElements.get(0)
    )

    messageSendToRefactor.getCallArguments.replace(emptyArguments)
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = true
}

object RemoveUnnecessaryHashBraces {
  val optionDescription = "Remove unnecessary braces"
}


