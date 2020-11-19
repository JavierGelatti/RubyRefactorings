package com.refactorings.ruby

import java.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{IfStatementExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.{RIfStatement, RReturnStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modifierStatements.{RModifierStatement, RUnlessModStatement}

class ReplaceConditionalWithGuardClause extends RefactoringIntention(ReplaceConditionalWithGuardClause) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalToRefactor, _) = elementsToRefactor(focusedElement).get

    if (conditionalToRefactor.hasNoElseBlock) {
      val template = Parser.parseHeredoc(
        """
          |return unless CONDITION
          |
          |NEW_BODY
      """
      )
      replaceConditionWith(template, conditionalToRefactor)
      replaceNewBodyWith(template, conditionalToRefactor.getThenBlock.getStatements)

      conditionalToRefactor.replace(template)
    } else {
      val template = Parser.parseHeredoc(
        """
         |return THEN_BODY if CONDITION
         |
         |ELSE_BODY
        """
      )
      // TODO: We should get the template elements before we perform any replacements.
      replaceConditionWith(template, conditionalToRefactor)
      replaceThenBodyWith(template, conditionalToRefactor.getThenBlock.getStatements.head)
      replaceNewBodyWith(template, conditionalToRefactor.getElseBlock.getBody.getStatements)

      conditionalToRefactor.replace(template)
    }
  }

  private def replaceConditionWith(template: PsiElement, conditionalToRefactor: RIfStatement): Unit = {
    val newGuardClause = template.childOfType[RModifierStatement]()
    newGuardClause.getCondition.replace(conditionalToRefactor.getCondition)
  }

  private def replaceNewBodyWith(template: PsiElement, elementsToReplaceBodyWith: util.List[RPsiElement]): Unit = {
    val newBody = template.getLastChild

    template.addRangeBefore(
      elementsToReplaceBodyWith.head,
      elementsToReplaceBodyWith.last,
      newBody
    )

    newBody.delete()
    template.getLastChild.delete() // Removes extra newline
  }

  private def replaceThenBodyWith(template: PsiElement, elementsToReplaceBodyWith: RPsiElement): Unit = {
    val newGuardClause = template.childOfType[RModifierStatement]()
    val returnStatement = newGuardClause.childOfType[RReturnStatement]()
    returnStatement.getReturnValues.head.replace(elementsToReplaceBodyWith)
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      focusedConditional <- focusedElement.findParentOfType[RIfStatement](treeHeightLimit = 1)
      if (focusedConditional.hasNoElseBlock || focusedConditional.getThenBlock.getStatements.size() == 1) &&
        focusedConditional.hasNoElsifBlocks &&
        focusedConditional.isLastChildOfParent
      parentMethod <- focusedConditional.findParentOfType[RMethod](treeHeightLimit = 3)
    } yield (focusedConditional, parentMethod)
  }
}

object ReplaceConditionalWithGuardClause extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace conditional with guard clause"
  override def optionDescription = "Replace with guard clause"
}
