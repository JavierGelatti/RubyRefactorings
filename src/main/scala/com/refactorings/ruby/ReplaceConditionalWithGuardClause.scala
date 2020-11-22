package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{IfOrUnlessStatement, IfOrUnlessStatementExtension, IfStatementExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modifierStatements.RModifierStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.{RCondition, RIfStatement, RReturnStatement, RUnlessStatement}

import scala.language.reflectiveCalls

class ReplaceConditionalWithGuardClause extends RefactoringIntention(ReplaceConditionalWithGuardClause) {

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalToRefactor, _) = elementsToRefactor(focusedElement).get

    if (conditionalToRefactor.hasNoAlternativePaths || conditionalToRefactor.getThenBlock.getStatements.size == 1) {
      conditionalToRefactor.replace(guardEquivalentTo(conditionalToRefactor))
    } else {
      wrapInReturnStatement(conditionalToRefactor.getThenBlock.getStatements.last)
      addAfter(conditionalToRefactor, bodyBlockPreservingAlternativePaths(conditionalToRefactor))
      removeElsifBlocks(conditionalToRefactor)
      removeElseBlock(conditionalToRefactor)
    }
  }

  private def wrapInReturnStatement(lastStatement: RPsiElement)(implicit project: Project) = {
    lastStatement match {
      case returnStatement: RReturnStatement => returnStatement
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
    conditionalToRefactor.getElseBlock.delete()
    conditionalToRefactor.getThenBlock.getNextSibling.delete() // Remove extra newline
  }

  private def guardEquivalentTo(conditionalToRefactor: IfOrUnlessStatement)(implicit project: Project) = {
    if (conditionalToRefactor.hasNoAlternativePaths) {
      guardWith(
        modifierKeyword = conditionalToRefactor.negatedKeyword,
        condition = conditionalToRefactor.getCondition,
        returnValue = None,
        bodyBlock = conditionalToRefactor.getThenBlock
      )
    } else {
      guardWith(
        modifierKeyword = conditionalToRefactor.keyword,
        condition = conditionalToRefactor.getCondition,
        returnValue = Some(conditionalToRefactor.getThenBlock.getStatements.head),
        bodyBlock = bodyBlockPreservingAlternativePaths(conditionalToRefactor)
      )
    }
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
  (modifierKeyword: String, condition: RCondition, returnValue: Option[RPsiElement], bodyBlock: RCompoundStatement)
  (implicit project: Project)
  = {
    val newGuard = Parser.parseHeredoc(
      s"""
         |return RETURN_VALUE $modifierKeyword GUARD_CONDITION
         |
         |BODY
      """
    )
    val guard = newGuard.childOfType[RModifierStatement]()
    val guardConditionPlaceholder = guard.getCondition
    val returnStatementPlaceholder = guard.getCommand.asInstanceOf[RReturnStatement]
    val bodyPlaceholder = newGuard.getLastChild

    returnValue match {
      case Some(value) => returnStatementPlaceholder.replace(wrapInReturnStatement(value))
      case None =>
        val returnValuePlaceholder = returnStatementPlaceholder.getReturnValues.head
        returnValuePlaceholder.delete()
    }
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
      if focusedConditional.isLastChildOfParent && !focusedConditional.getThenBlock.getStatements.isEmpty
      parentMethod <- focusedConditional.findParentOfType[RMethod](treeHeightLimit = 3)
    } yield (focusedConditional, parentMethod)
  }
}

object ReplaceConditionalWithGuardClause extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace conditional with guard clause"
  override def optionDescription = "Replace with guard clause"
}
