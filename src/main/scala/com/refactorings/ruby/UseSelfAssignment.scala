package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.{Parser, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.{RAssignmentExpression, RBinaryExpression, RSelfAssignmentExpression}

import scala.PartialFunction.condOpt

class UseSelfAssignment extends RefactoringIntention(UseSelfAssignment) {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementsToRefactor(element)(project).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (assignment, binaryExpression) = elementsToRefactor(focusedElement).get
    val operator = binaryExpression.getOperation.getText

    val selfAssignment = Parser.parse(s"variable ${operator}= value").childOfType[RSelfAssignmentExpression]()

    selfAssignment.getValue.replaceWith(
      assignment.getOperation.siblingsUntilButNotIncluding(binaryExpression) ++
      binaryExpression.getOperation.siblingsUntilButNotIncluding(binaryExpression.getRightOperand) :+
      binaryExpression.getRightOperand: _*
    )
    selfAssignment.getObject.replace(assignment.getObject)

    assignment.replace(selfAssignment)
  }

  private def elementsToRefactor(focusedElement: PsiElement)(implicit project: Project) = {
    for {
      assignment <- focusedElement.findParentOfType[RAssignmentExpression]()
      if !assignment.isInstanceOf[RSelfAssignmentExpression]
      binaryExpression <- condOpt(assignment.getValue) {
        case binaryExpression: RBinaryExpression => binaryExpression
      }
      if binaryExpression.getLeftOperand.astEquivalentTo(assignment.getObject)
    } yield (assignment, binaryExpression)
  }
}

object UseSelfAssignment extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace binary operator assignment by self-assignment shorthand notation"

  override def optionDescription: String = "Replace by self-assignment"
}


