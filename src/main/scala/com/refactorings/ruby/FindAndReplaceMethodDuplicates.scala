package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.refactorings.ruby.psi.{Parser, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RCompoundStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.RMethod
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RFName, RIdentifier}

class FindAndReplaceMethodDuplicates extends RefactoringIntention(FindAndReplaceMethodDuplicates) {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementsToRefactor(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val sourceMethod = elementsToRefactor(focusedElement).get
    val methodBody = sourceMethod.getCompoundStatement
    val containingFile = sourceMethod.getContainingFile
    val topLevelStatements = containingFile.childOfType[RCompoundStatement](treeHeightLimit = 1).getStatements

    topLevelStatements.foreach { statement =>
      if (statement.astEquivalentTo(methodBody.getStatements.head)) {
        val methodCall = Parser.parse(sourceMethod.getMethodName.getName).childOfType[RIdentifier]()
        statement.replace(methodCall)
      }
    }
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      methodName <- focusedElement.findParentOfType[RFName](treeHeightLimit = 1)
      method <- methodName.findParentOfType[RMethod](treeHeightLimit = 2)
    } yield method
  }
}

object FindAndReplaceMethodDuplicates extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Find and replace method duplicates"

  override def optionDescription: String = "Use where possible"
}
