package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.PsiElementExtension
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.RIfStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.modifierStatements.{RIfModStatement, RModifierStatement, RUnlessModStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier

class ReplaceConditionalWithGuardClause extends RefactoringIntention(ReplaceConditionalWithGuardClause) {
  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalToRefactor, _) = elementsToRefactor(focusedElement).get

    val template = Parser.parseHeredoc(
      """
       |return unless CONDITION
       |
       |THEN_CLAUSE
      """
    )
    val newGuardClause: RModifierStatement = template.childOfType[RUnlessModStatement]()
    val newBody = template.getLastChild

    newGuardClause.getCondition.replace(conditionalToRefactor.getCondition)
    val conditionalThenStatements = conditionalToRefactor.getThenBlock.getStatements
    template.addRangeBefore(
      conditionalThenStatements.head,
      conditionalThenStatements.last,
      newBody
    )
    newBody.delete()
    template.getLastChild.delete() // Removes extra newline
    conditionalToRefactor.replace(template)
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      focusedConditional <- focusedElement.findParentOfType[RIfStatement](treeHeightLimit = 1)
      if focusedConditional.getElseBlock == null &&
        focusedConditional.getElsifBlocks.isEmpty &&
        focusedConditional.getParent.getLastChild == focusedConditional
      parentMethod <- focusedConditional.findParentOfType[RMethod](treeHeightLimit = 3)
    } yield (focusedConditional, parentMethod)
  }
}

object ReplaceConditionalWithGuardClause extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace conditional with guard clause"
  override def optionDescription = "Replace with guard clause"
}
