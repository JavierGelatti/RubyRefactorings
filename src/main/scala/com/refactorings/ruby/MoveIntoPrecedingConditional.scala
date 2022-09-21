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
    val firstThingAfterConditional = conditionalStatement.nextSiblingIgnoringWhitespace.get

    addCurrentStatementTo(thenBlock)
    elsifBlocks.foreach(addCurrentStatementTo(_))
    addCurrentStatementTo(elseBlock)

    deleteRange(firstThingAfterConditional, currentStatement)

    def addCurrentStatementTo(container: PsiElement): Unit = {
      firstThingAfterConditional
        .myselfAndSiblingsUntilAndIncluding(currentStatement)
        .foreach(container.add)
    }
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

  private def deleteRange(rangeStart: PsiElement, rangeEnd: RPsiElement): Unit = {
    rangeStart.getParent
      .deleteChildRange(
        rangeStart,
        rangeEnd.nextSiblingIgnoringWhitespace.map(_.getPrevSibling).getOrElse(rangeEnd)
      )
  }
}

object MoveIntoPrecedingConditional extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Move statement into conditional"

  override def optionDescription: String = "Move into conditional above"
}


