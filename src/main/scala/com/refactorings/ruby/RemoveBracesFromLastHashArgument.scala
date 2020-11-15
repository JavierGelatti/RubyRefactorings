package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.PsiElementExtensions.{MessageSendExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RAssocList
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RCall, RHashToArguments}

import scala.PartialFunction.condOpt

class RemoveBracesFromLastHashArgument extends RefactoringIntention(RemoveBracesFromLastHashArgument) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
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

  private def hashFromLastArgument(lastArgument: RPsiElement): Option[RAssocList] = condOpt(lastArgument) {
    case hash: RAssocList => hash
    case hashToArguments: RHashToArguments => hashToArguments.childOfType[RAssocList]()
  }
}

object RemoveBracesFromLastHashArgument extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Remove braces from last argument hash in message send"
  override def optionDescription = "Remove hash braces"
}


