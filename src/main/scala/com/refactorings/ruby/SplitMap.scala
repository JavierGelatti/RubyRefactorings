package com.refactorings.ruby

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.refactorings.ruby.SplitMap.optionDescription
import com.refactorings.ruby.psi.{Parser, PsiElementExtension}
import com.refactorings.ruby.ui.{SelectionOption, UI}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.ArgumentInfo
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.RDoBlockCall
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor

import scala.collection.mutable

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
      .map(new SplitStatements(doBlock, _))

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

  private def performRefactoring(selectedOption: SplitStatements)(implicit project: Project): Unit = {
    val blockCallToRefactor = selectedOption.blockCall
    val allStatements = blockCallToRefactor.getBlock.getCompoundStatement.getStatements
    val (statementsBefore, statementsAfter) =
      allStatements.partition(selectedOption.includedStatements.contains(_))

    val variablesToIncludeInSecondBlock = new mutable.HashSet[String]()
    val visitor = new RubyRecursiveElementVisitor() {
      override def visitRIdentifier(identifier: RIdentifier): Unit = {
        super.visitRIdentifier(identifier)

        if (identifier.isLocalVariable) {
          identifier
            .referencesInside(blockCallToRefactor)
            .find { element => !selectedOption.textRange.contains(element.getTextRange) }
            .map { element =>
              variablesToIncludeInSecondBlock.addOne(element.getText)
            }
        }
      }
    }

    statementsBefore.foreach(_.accept(visitor))

    val newMap = Parser.parseHeredoc(
      """
        |receiver.map do ||
        |  BODY
        |end
      """).childOfType[RDoBlockCall]()

    newMap.getBlock.getCompoundStatement.addRange(
      statementsAfter.head,
      statementsAfter.last
    )
    newMap.getBlock.getCompoundStatement.getStatements.head.delete()
    blockCallToRefactor.getBlock.getCompoundStatement.deleteChildRange(
      statementsAfter.head.getPrevSibling.getPrevSibling, // TODO: Find better way to remove extra newline
      statementsAfter.last
    )
    blockCallToRefactor.getBlock.getCompoundStatement.add(
      Parser.parse(variablesToIncludeInSecondBlock.head).getFirstChild
    )
    newMap.getBlock.getBlockArguments.addParameter(variablesToIncludeInSecondBlock.head, ArgumentInfo.Type.SIMPLE, false)
    newMap.getReceiver.replace(blockCallToRefactor)
    blockCallToRefactor.replace(newMap)

    println(newMap)
  }

  override def startInWriteAction = false

  private class SplitStatements(val blockCall: RDoBlockCall, val includedStatements: List[RPsiElement]) extends SelectionOption {
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
