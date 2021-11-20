package com.refactorings.ruby

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.refactorings.ruby.psi.{CompoundStatementExtension, IfOrUnlessStatement, IfOrUnlessStatementExtension, Parser, PsiElementExtension}

import scala.language.{implicitConversions, reflectiveCalls}

class RemoveUselessConditionalStatement extends RefactoringIntention(RemoveUselessConditionalStatement) with HighPriorityAction {
  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalStatement, conditionValue) = elementsToRefactor(focusedElement).get

    val equivalentBlock = blockGivenConditionValue(conditionalStatement, conditionValue)

    if (conditionalStatement.isUsedAsExpression) {
        if (conditionalStatement.isInsideCompoundStatement) {
          if (equivalentBlock.statements.isEmpty) equivalentBlock.add(Parser.nil)
          conditionalStatement.replaceWithBlock(equivalentBlock)
        } else {
          conditionalStatement.replace(equivalentBlock.asExpression)
        }
    } else {
      conditionalStatement.replaceWithBlock(equivalentBlock)
    }
  }

  private def blockGivenConditionValue
  (conditionalStatement: IfOrUnlessStatement, conditionValue: Boolean)
  (implicit project: Project) = {
    if (conditionValue) {
      conditionalStatement.getThenBlock
    } else {
      conditionalStatement.alternativeBlockPreservingElsifPaths
        .getOrElse(Parser.emptyCompoundStatement)
    }
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      conditionalStatement <- focusedElement.findConditionalParent(treeHeightLimit = 3)
      conditionValue <- conditionalStatement.staticConditionValue
    } yield (conditionalStatement, conditionValue)
  }
}

object RemoveUselessConditionalStatement extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Remove useless if/unless statement"

  override def optionDescription: String = "Remove conditional statement"
}

