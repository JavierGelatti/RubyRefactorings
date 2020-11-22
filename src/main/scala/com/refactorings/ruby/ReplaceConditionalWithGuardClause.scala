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

    conditionalToRefactor.replace(guardEquivalentTo(conditionalToRefactor))
  }

  private def guardEquivalentTo(conditionalToRefactor: IfOrUnlessStatement)(implicit project: Project) = {
    if (conditionalToRefactor.hasNoElseBlock) {
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
        bodyBlock = conditionalToRefactor.getElseBlock.getBody
      )
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
    val returnValuePlaceholder = guard.getCommand.asInstanceOf[RReturnStatement].getReturnValues.head
    val bodyPlaceholder = newGuard.getLastChild

    returnValue match {
      case Some(value) => returnValuePlaceholder.replace(value)
      case None => returnValuePlaceholder.delete()
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
      focusedConditional <- findIfStatementWithoutElsif(focusedElement)
        .orElse(findUnlessStatement(focusedElement))
        .map(_.asInstanceOf[IfOrUnlessStatement])
      if (focusedConditional.hasNoElseBlock || focusedConditional.getThenBlock.getStatements.size() == 1) &&
        focusedConditional.isLastChildOfParent
      parentMethod <- focusedConditional.findParentOfType[RMethod](treeHeightLimit = 3)
    } yield (focusedConditional, parentMethod)
  }

  private def findUnlessStatement(focusedElement: PsiElement) = {
    focusedElement.findParentOfType[RUnlessStatement](treeHeightLimit = 1)
  }

  private def findIfStatementWithoutElsif(focusedElement: PsiElement) = {
    focusedElement.findParentOfType[RIfStatement](treeHeightLimit = 1)
      .filter(ifStatement => ifStatement.hasNoElsifBlocks)
  }
}

object ReplaceConditionalWithGuardClause extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace conditional with guard clause"
  override def optionDescription = "Replace with guard clause"
}
