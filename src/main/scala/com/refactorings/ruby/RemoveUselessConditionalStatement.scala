package com.refactorings.ruby

import com.intellij.codeInsight.intention.HighPriorityAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi._
import com.refactorings.ruby.psi.Matchers.PseudoConstant
import com.refactorings.ruby.psi.{IfOrUnlessStatement, IfOrUnlessStatementExtension, PsiElementExtension, SymbolExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.{RNumericConstant, RSymbol}

import scala.PartialFunction.condOpt
import scala.language.{implicitConversions, reflectiveCalls}

class RemoveUselessConditionalStatement extends RefactoringIntention(RemoveUselessConditionalStatement) with HighPriorityAction {
  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
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
    conditionalStatement.condition
      .flatMap { condition => staticTruthValueOf(condition.getFirstChild) }
      .map { conditionValue =>
        if (conditionalStatement.keyword == "unless") !conditionValue else conditionValue
      }
  }

  private def staticTruthValueOf(conditionValue: PsiElement) = condOpt(conditionValue) {
    case PseudoConstant("true") =>
      true
    case PseudoConstant("false") | PseudoConstant("nil") =>
      false
    case _: RNumericConstant =>
      true
    case symbol: RSymbol if !symbol.hasExpressionSubstitutions =>
      true
    case string: RStringLiteral if !string.hasExpressionSubstitutions =>
      true
  }
}

object RemoveUselessConditionalStatement extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Remove useless if/unless statement"

  override def optionDescription: String = "Remove conditional statement"
}

