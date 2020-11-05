package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.RemoveUnnecessaryHashBraces.optionDescription
import com.refactorings.ruby.psi.PsiElementExtensions.PsiElementExtension
import org.jetbrains.plugins.ruby.ruby.lang.psi.assoc.RAssoc
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RAssocList
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RCall, RHashToArguments}

import scala.jdk.CollectionConverters.ListHasAsScala

class RemoveUnnecessaryHashBraces extends PsiElementBaseIntentionAction {
  @IntentionName override def getText: String = optionDescription

  @IntentionFamilyName override def getFamilyName = "Remove unnecessary hash braces in message send"

  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    implicit val currentProject = project

    val messageSendToRefactor = focusedElement.parentOfType[RCall]()

    val hashArgumentThingyToRemoveBracesFrom = messageSendToRefactor.getCallArguments.getElements.asScala.last


    val elHashPostaPiolin = hashArgumentThingyToRemoveBracesFrom match {
      case hash: RAssocList => hash
      case hashToArguments: RHashToArguments => hashToArguments.childOfType[RAssocList]()
    }

    val hashArgumentAssociations = elHashPostaPiolin.getChildren.filter {
      case _: RAssoc => true
      case _ => false
    }

    if (hashArgumentAssociations.isEmpty) {
      // do nothing
    } else if(hashArgumentAssociations.length == 1) {
      messageSendToRefactor.getCallArguments.addBefore(
        hashArgumentAssociations.head,
        hashArgumentThingyToRemoveBracesFrom
      )
    } else {
      messageSendToRefactor.getCallArguments.addRangeBefore(
        hashArgumentAssociations.head,
        hashArgumentAssociations.last,
        hashArgumentThingyToRemoveBracesFrom
      )
    }

    hashArgumentThingyToRemoveBracesFrom.delete()
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = true
}

object RemoveUnnecessaryHashBraces {
  val optionDescription = "Remove unnecessary braces"
}


