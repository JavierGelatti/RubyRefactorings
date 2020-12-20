package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.ReplaceConditionalWithGuardClause.{InterruptionKeyword, Next, Return}
import com.refactorings.ruby.psi.Extensions.{IfOrUnlessStatement, IfOrUnlessStatementExtension, IfStatementExtension, PsiElementExtension}
import com.refactorings.ruby.psi.Parser
import org.jetbrains.plugins.ruby.ruby.actions.intention.StatementToModifierIntention
import org.jetbrains.plugins.ruby.ruby.codeInsight.resolve.scope.ScopeHolder
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modifierStatements.RModifierStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.{RCondition, RControlStructureStatement, RIfStatement, RUnlessStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RListOfExpressions
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RCodeBlock

import scala.language.reflectiveCalls

class ReplaceConditionalWithGuardClause extends RefactoringIntention(ReplaceConditionalWithGuardClause) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalToRefactor, flowInterruptionKeywordToUse) = elementsToRefactor(focusedElement).get

    if (conditionalToRefactor.hasNoAlternativePaths) {
      conditionalToRefactor.replace(
        guardWith(
          modifierKeyword = conditionalToRefactor.negatedKeyword,
          condition = conditionalToRefactor.getCondition,
          flowInterruptionKeyword = flowInterruptionKeywordToUse,
          bodyBlock = conditionalToRefactor.getThenBlock
        )
      )
    } else {
      wrapInFlowInterruptionStatement(
        conditionalToRefactor.getThenBlock.getStatements.last,
        interruptionKeyword = flowInterruptionKeywordToUse
      )
      addAfter(conditionalToRefactor, bodyBlockPreservingAlternativePaths(conditionalToRefactor))
      removeElsifBlocks(conditionalToRefactor)
      removeElseBlock(conditionalToRefactor)

      simplifyToModifierIfApplicable(editor, currentProject, conditionalToRefactor)
    }
  }

  private def wrapInFlowInterruptionStatement
  (lastStatement: RPsiElement, interruptionKeyword: InterruptionKeyword)(implicit project: Project) = {
    lastStatement match {
      case flowInterruption if flowInterruption.isFlowInterruptionStatement => flowInterruption
      case normalStatement =>
        val returnStatementTemplate = Parser.parse(s"$interruptionKeyword SOMETHING").childOfType[RControlStructureStatement]()
        returnStatementTemplate.childOfType[RListOfExpressions]().getFirstElement.replace(normalStatement)
        normalStatement.replace(returnStatementTemplate)
    }
  }

  private def addAfter
  (conditionalToRefactor: IfOrUnlessStatement, compoundStatement: RCompoundStatement)(implicit project: Project) = {
    val container = conditionalToRefactor.getParent
    container.addAfter(
      compoundStatement,
      conditionalToRefactor
    )
    container.addAfter(Parser.parse("\n"), conditionalToRefactor)
  }

  private def removeElsifBlocks(conditionalToRefactor: IfOrUnlessStatement): Unit = {
    conditionalToRefactor.getElsifBlocks.foreach { elsif =>
      elsif.delete()
      conditionalToRefactor.getThenBlock.getNextSibling.delete() // Remove extra newline
      conditionalToRefactor.getThenBlock.getNextSibling.delete() // Remove extra whitespace
    }
  }

  private def removeElseBlock(conditionalToRefactor: IfOrUnlessStatement): Unit = {
    conditionalToRefactor.elseBlock.foreach(elseBlock => {
      elseBlock.delete()
      conditionalToRefactor.getThenBlock.getNextSibling.delete() // Remove extra newline
    })
  }

  private def bodyBlockPreservingAlternativePaths(conditionalToRefactor: IfOrUnlessStatement)(implicit project: Project) = {
    conditionalToRefactor.getElsifBlocks match {
      case firstElsif :: restOfElsifs =>
        val newBody = Parser.parseHeredoc(
          """
            |if CONDITION
            |  THEN_BODY
            |end
            """).asInstanceOf[RCompoundStatement]
        val ifFromNewBody = newBody.childOfType[RIfStatement]()

        ifFromNewBody.getCondition.replace(firstElsif.getCondition)
        ifFromNewBody.getThenBlock.getStatements.head.replaceWithBlock(firstElsif.getBody)

        restOfElsifs.foreach(ifFromNewBody.addElsif(_))

        conditionalToRefactor.elseBlock.foreach { elseBlock =>
          ifFromNewBody.addElse(elseBlock)
        }

        newBody
      case Nil => conditionalToRefactor.getElseBlock.getBody
    }
  }

  private def guardWith
  (modifierKeyword: String, condition: RCondition, flowInterruptionKeyword: InterruptionKeyword, bodyBlock: RCompoundStatement)(implicit project: Project) = {
    val newGuard = Parser.parseHeredoc(
      s"""
         |$flowInterruptionKeyword $modifierKeyword GUARD_CONDITION
         |
         |BODY
      """
    )
    val guard = newGuard.childOfType[RModifierStatement]()
    val guardConditionPlaceholder = guard.getCondition
    val bodyPlaceholder = newGuard.getLastChild

    guardConditionPlaceholder.replace(condition)
    bodyPlaceholder.replaceWithBlock(bodyBlock)

    newGuard
  }

  private def simplifyToModifierIfApplicable
  (editor: Editor, currentProject: Project, conditionalToRefactor: IfOrUnlessStatement): Unit = {
    val convertConditionalStatementToModifier = new StatementToModifierIntention()
    if (convertConditionalStatementToModifier.isAvailable(currentProject, editor, conditionalToRefactor)) {
      convertConditionalStatementToModifier.invoke(currentProject, editor, conditionalToRefactor)
    }
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      focusedConditional <- findFocusedConditional(focusedElement) if !focusedConditional.hasEmptyThenBlock

      flowInterruptionKeyword <- if (focusedConditional.hasAlternativePaths && endsInterruptingFlow(focusedConditional.getThenBlock)) {
        Some(Return) // We return this as a placeholder value, because the existing flow interruption statement will be reused
      } else if (focusedConditional.isLastChildOfParent) {
        findParentBlockOrMethod(focusedConditional).map(flowInterruptionKeywordFor)
      } else {
        None
      }
    } yield (focusedConditional, flowInterruptionKeyword)
  }

  private def findFocusedConditional(focusedElement: PsiElement) = {
    focusedElement.findParentOfType[RIfStatement](treeHeightLimit = 1)
      .orElse(focusedElement.findParentOfType[RUnlessStatement](treeHeightLimit = 1))
      .map(_.asInstanceOf[IfOrUnlessStatement])
  }

  private def findParentBlockOrMethod(focusedConditional: IfOrUnlessStatement) = {
    focusedConditional.findParentOfType[RMethod](treeHeightLimit = 3)
      .orElse(focusedConditional.findParentOfType[RCodeBlock](treeHeightLimit = 3))
  }

  private def endsInterruptingFlow(nonEmptyBlock: RCompoundStatement) = {
    nonEmptyBlock.getStatements.last.isFlowInterruptionStatement
  }

  private def flowInterruptionKeywordFor(parentBlockOrMethod: ScopeHolder): InterruptionKeyword = {
    parentBlockOrMethod match {
      case _: RCodeBlock => Next
      case _: RMethod => Return
    }
  }
}

object ReplaceConditionalWithGuardClause extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace conditional with guard clause"
  override def optionDescription = "Replace with guard clause"

  private sealed abstract class InterruptionKeyword(keyword: String) {
    override def toString: String = keyword
  }
  private case object Return extends InterruptionKeyword("return")
  private case object Next extends InterruptionKeyword("next")
}
