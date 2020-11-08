package com.refactorings.ruby

import java.util

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.RemoveUnnecessaryHashBraces.optionDescription
import com.refactorings.ruby.psi.PsiElementExtensions.PsiElementExtension
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RAssocList
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RArgumentToBlock, RCall, RHashToArguments}

import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala

class RemoveUnnecessaryHashBraces extends PsiElementBaseIntentionAction {
  implicit def list2Scala[T]: util.List[T] => mutable.Buffer[T] = list => list.asScala

  @IntentionName override def getText: String = optionDescription

  @IntentionFamilyName override def getFamilyName = "Remove unnecessary hash braces in message send"

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
      messageArguments = messageSendToRefactor.getCallArguments.getElements
      lastArgument <- messageArguments.lastOption match {
        case Some(_: RArgumentToBlock) => messageArguments.dropRight(1).lastOption
        case x => x
      }
      lastArgumentHash <- lastArgument match {
        case hash: RAssocList => Some(hash)
        case hashToArguments: RHashToArguments => Some(hashToArguments.childOfType[RAssocList]())
        case _ => None
      }
      lastArgumentHashAssociations = lastArgumentHash.getAssocElements
      if lastArgument.getTextRange.contains(focusedElement.getTextRange) &&
        lastArgumentHashAssociations.nonEmpty
    } yield (messageSendToRefactor, lastArgument, lastArgumentHashAssociations)
  }
}

object RemoveUnnecessaryHashBraces {
  val optionDescription = "Remove unnecessary braces"
}


