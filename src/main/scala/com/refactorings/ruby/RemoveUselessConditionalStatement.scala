package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.refactorings.ruby.psi.{IfOrUnlessStatement, IfOrUnlessStatementExtension, PsiElementExtension}

import scala.language.{implicitConversions, reflectiveCalls}

class RemoveUselessConditionalStatement extends RefactoringIntention(RemoveUselessConditionalStatement) {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementsToRefactor(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalStatement, conditionIsTrue) = elementsToRefactor(focusedElement).get

    if (conditionIsTrue) {
      conditionalStatement.replaceWithBlock(conditionalStatement.getThenBlock)
    } else {
      conditionalStatement.replaceWithBlock(conditionalStatement.alternativeBlockPreservingElsifPaths)
    }
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      conditionalStatement <- focusedElement.findConditionalParent(treeHeightLimit = 3)
      conditionValue <- staticConditionValueOf(conditionalStatement)
    } yield (conditionalStatement, conditionValue)
  }

  private def staticConditionValueOf(conditionalStatement: IfOrUnlessStatement): Option[Boolean] = {
    conditionalStatement.condition.flatMap { condition =>
      if (condition.textMatches("false") || condition.textMatches("nil")) {
        Some(conditionalStatement.keyword == "unless")
      } else if (condition.textMatches("true")) {
        Some(conditionalStatement.keyword == "if")
      } else {
        None
      }
    }
  }
}

object RemoveUselessConditionalStatement extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Remove useless if/unless statement"

  override def optionDescription: String = "Remove conditional statement"
}

