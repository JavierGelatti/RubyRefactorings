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
    selfAssignment.getObject.replace(assignment.getObject)
    selfAssignment.getValue.replace(binaryExpression.getRightOperand)

    assignment.replace(selfAssignment)
  }

  private def elementsToRefactor(focusedElement: PsiElement)(implicit project: Project) = {
    for {
      assignment <- focusedElement.findParentOfType[RAssignmentExpression]()
      if !assignment.isInstanceOf[RSelfAssignmentExpression]
      binaryExpression <- condOpt(assignment.getValue) {
        case binaryExpression: RBinaryExpression => binaryExpression
      }
      if binaryExpression.getLeftOperand.textMatches(assignment.getObject.getText) // TODO: consider whitespace differences
    } yield (assignment, binaryExpression)
  }
}

object UseSelfAssignment extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Replace binary operator assignment by self-assignment shorthand notation"

  override def optionDescription: String = "Replace by self-assignment"
}


