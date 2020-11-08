package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.RemoveBracesFromLastHashArgument.optionDescription
import com.refactorings.ruby.psi.PsiElementExtensions.{MessageSendExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RAssocList
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RCall, RHashToArguments}

class RemoveBracesFromLastHashArgument extends PsiElementBaseIntentionAction {
  @IntentionName override def getText: String = optionDescription

  @IntentionFamilyName override def getFamilyName = "Remove braces from last argument hash in message send"

  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    val (messageSendToRefactor, lastArgumentHash, hashArgumentAssociations) = elementsToRefactor(focusedElement).get

    messageSendToRefactor.getCallArguments.addRangeBefore(
      hashArgumentAssociations.head,
      hashArgumentAssociations.last,
      lastArgumentHash
    )
    lastArgumentHash.delete()
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      messageSendToRefactor <- focusedElement.findParentOfType[RCall](treeHeightLimit = 4)
      lastArgument <- messageSendToRefactor.lastArgument if lastArgument.contains(focusedElement)
      lastArgumentHash <- hashFromLastArgument(lastArgument)
      lastArgumentHashAssociations = lastArgumentHash.getAssocElements if lastArgumentHashAssociations.nonEmpty
    } yield (messageSendToRefactor, lastArgument, lastArgumentHashAssociations)
  }

  private def hashFromLastArgument(lastArgument: RPsiElement): Option[RAssocList] = lastArgument match {
    case hash: RAssocList => Some(hash)
    case hashToArguments: RHashToArguments => Some(hashToArguments.childOfType[RAssocList]())
    case _ => None
  }
}

object RemoveBracesFromLastHashArgument {
  val optionDescription = "Remove hash braces"
}


