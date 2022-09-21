package com.refactorings.ruby
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.Matchers.EndOfLine
import com.refactorings.ruby.psi.{IfOrUnlessStatement, IfOrUnlessStatementExtension, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement

import java.util
import scala.language.reflectiveCalls

class MoveIntoPrecedingConditional extends RefactoringIntention(MoveIntoPrecedingConditional) {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementsToRefactor(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (conditionalStatement, elseBlock, currentStatement) = elementsToRefactor(focusedElement).get
    val thenBlock = conditionalStatement.getThenBlock
    val elsifBlocks = conditionalStatement.getElsifBlocks.map(_.getBody)
    val firstThingAfterConditional = conditionalStatement.nextSiblingIgnoringWhitespaceAndNewlines.get
    val thingsToMove = firstThingAfterConditional
      .myselfAndSiblingsUntilAndIncluding(
        currentStatement.followingComment.getOrElse(currentStatement)
      )

    addThingsToMoveTo(thenBlock)
    elsifBlocks.foreach(addThingsToMoveTo(_))
    addThingsToMoveTo(elseBlock)

    deleteRange(thingsToMove.head, thingsToMove.last)

    def addThingsToMoveTo(container: PsiElement): Unit = thingsToMove.foreach(container.add)
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      container <- focusedElement.findParentOfType[RCompoundStatement]()
      siblingStatements = container.getStatements
      (conditionalStatement, currentStatement) <- pairwise(siblingStatements).collectFirst {
        case (conditionalStatement: IfOrUnlessStatement @unchecked, currentStatement)
          if currentStatement.contains(focusedElement) => (conditionalStatement, currentStatement)
      }
      elseBlock <- conditionalStatement.elseBlock
    } yield (conditionalStatement, elseBlock.getBody, currentStatement)
  }

  private def pairwise(siblingStatements: util.List[RPsiElement]) = {
    siblingStatements.zip(siblingStatements.drop(1))
  }

  private def deleteRange(rangeStart: PsiElement, rangeEnd: PsiElement): Unit = {
    rangeStart.getParent
      .deleteChildRange(
        rangeStart,
        rangeEnd.nextSiblingIgnoringWhitespaceAndNewlines.map(_.getPrevSibling).getOrElse(rangeEnd)
      )
  }
}

object MoveIntoPrecedingConditional extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Move statement into conditional"

  override def optionDescription: String = "Move into conditional above"
}


