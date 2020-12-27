package com.refactorings.ruby

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.refactorings.ruby.SplitMap.optionDescription
import com.refactorings.ruby.psi.PsiElementExtension
import com.refactorings.ruby.ui.{SelectionOption, UI}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RDoBlockCall

class SplitMap extends RefactoringIntention(SplitMap) {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementToRefactor(element).isDefined
  }

  private def elementToRefactor(element: PsiElement) = {
    element.findParentOfType[RDoBlockCall](treeHeightLimit = 3)
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val doBlock = elementToRefactor(focusedElement).get
    val statements = doBlock.getBlock.getCompoundStatement.getStatements

    val options = (1 until statements.size)
      .map(statements.take(_).toList)
      .map(new SplitStatements(_))

    UI.showOptionsMenuWith[SplitStatements](
      "Select statements to include",
      options,
      editor,
      selectedOption => WriteCommandAction
        .writeCommandAction(currentProject)
        .withName(optionDescription)
        .run { performRefactoring(selectedOption) }
    )
  }

  private def performRefactoring(selectedOption: SplitStatements): Unit = {
    selectedOption.includedStatements.foreach {
      statement => statement.delete()
    }
  }

  override def startInWriteAction = false

  private class SplitStatements(val includedStatements: List[RPsiElement]) extends SelectionOption {
    override val textRange: TextRange = new TextRange(
      includedStatements.head.getTextRange.getStartOffset,
      includedStatements.last.getTextRange.getEndOffset
    )
    override val optionText: String = includedStatements.last.getText}
}

object SplitMap extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Split a map block into two successive maps"

  override def optionDescription: String = "Split"
}


