package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{IfOrUnlessStatement, IfOrUnlessStatementExtension, IfStatementExtension, MessageSendExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.actions.intention.StatementToModifierIntention
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modifierStatements.RModifierStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.{RCondition, RIfStatement, RReturnStatement, RUnlessStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall

import scala.language.reflectiveCalls

class ReplaceConditionalWithGuardClause extends RefactoringIntention(ReplaceConditionalWithGuardClause) {

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalToRefactor, _) = elementsToRefactor(focusedElement).get

    if (conditionalToRefactor.hasNoAlternativePaths) {
      conditionalToRefactor.replace(
        guardWith(
          modifierKeyword = conditionalToRefactor.negatedKeyword,
          condition = conditionalToRefactor.getCondition,
          bodyBlock = conditionalToRefactor.getThenBlock
        )
      )
    } else {
      wrapInReturnStatement(conditionalToRefactor.getThenBlock.getStatements.last)
      addAfter(conditionalToRefactor, bodyBlockPreservingAlternativePaths(conditionalToRefactor))
      removeElsifBlocks(conditionalToRefactor)
      removeElseBlock(conditionalToRefactor)

      simplifyToModifierIfApplicable(editor, currentProject, conditionalToRefactor)
    }
  }

  private def simplifyToModifierIfApplicable
  (editor: Editor, currentProject: Project, conditionalToRefactor: IfOrUnlessStatement): Unit = {
    val convertConditionalStatementToModifier = new StatementToModifierIntention()
    if (convertConditionalStatementToModifier.isAvailable(currentProject, editor, conditionalToRefactor)) {
      convertConditionalStatementToModifier.invoke(currentProject, editor, conditionalToRefactor)
    }
  }

  private def wrapInReturnStatement(lastStatement: RPsiElement)(implicit project: Project) = {
    lastStatement match {
      case returnStatement: RReturnStatement => returnStatement
      case raiseSend: RCall if raiseSend.isRaise => raiseSend
      case normalStatement =>
        val returnStatementTemplate = Parser.parse("return SOMETHING").childOfType[RReturnStatement]()
        returnStatementTemplate.getReturnValues.head.replace(normalStatement)
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
        ifFromNewBody.getThenBlock.getStatements.head.replaceWith(firstElsif.getBody)

        restOfElsifs.foreach(ifFromNewBody.addElsif(_))

        conditionalToRefactor.elseBlock
          .map(elseBlock => ifFromNewBody.addElse(elseBlock))

        newBody
      case Nil => conditionalToRefactor.getElseBlock.getBody
    }
  }

  private def guardWith
  (modifierKeyword: String, condition: RCondition, bodyBlock: RCompoundStatement)(implicit project: Project) = {
    val newGuard = Parser.parseHeredoc(
      s"""
         |return $modifierKeyword GUARD_CONDITION
         |
         |BODY
      """
    )
    val guard = newGuard.childOfType[RModifierStatement]()
    val guardConditionPlaceholder = guard.getCondition
    val bodyPlaceholder = newGuard.getLastChild

    guardConditionPlaceholder.replace(condition)
    bodyPlaceholder.replaceWith(bodyBlock)

    newGuard
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      focusedConditional <- focusedElement.findParentOfType[RIfStatement](treeHeightLimit = 1)
        .orElse(focusedElement.findParentOfType[RUnlessStatement](treeHeightLimit = 1))
        .map(_.asInstanceOf[IfOrUnlessStatement])
      if !focusedConditional.hasEmptyThenBlock
      if focusedConditional.isLastChildOfParent || endsWithReturnOrRaise(focusedConditional.getThenBlock)
      parentMethod <- focusedConditional.findParentOfType[RMethod](treeHeightLimit = 3)
    } yield (focusedConditional, parentMethod)
  }

  private def endsWithReturnOrRaise(nonEmptyBlock: RCompoundStatement) = {
    nonEmptyBlock.getStatements.last match {
      case _: RReturnStatement => true
      case raiseSend: RCall if raiseSend.isRaise => true
      case _ => false
    }
  }
}

object ReplaceConditionalWithGuardClause extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace conditional with guard clause"
  override def optionDescription = "Replace with guard clause"
}
