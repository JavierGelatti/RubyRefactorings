package com.refactorings.ruby

import java.util

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{IfStatementExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.RIfStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modifierStatements.{RModifierStatement, RUnlessModStatement}

class ReplaceConditionalWithGuardClause extends RefactoringIntention(ReplaceConditionalWithGuardClause) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalToRefactor, _) = elementsToRefactor(focusedElement).get

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
  }

  private def replaceConditionWith(template: PsiElement, conditionalToRefactor: RIfStatement): Unit = {
    val newGuardClause: RModifierStatement = template.childOfType[RUnlessModStatement]()

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

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      focusedConditional <- focusedElement.findParentOfType[RIfStatement](treeHeightLimit = 1)
      if focusedConditional.hasNoElseBlock &&
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
