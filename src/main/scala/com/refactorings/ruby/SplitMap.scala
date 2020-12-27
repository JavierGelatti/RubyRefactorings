package com.refactorings.ruby

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.refactorings.ruby.SplitMap.optionDescription
import com.refactorings.ruby.psi.{BlockCallExtension, CodeBlockExtension, CompoundStatementExtension, IdentifierExtension, Parser, PsiElementExtension, PsiListExtension}
import com.refactorings.ruby.ui.{SelectionOption, UI}
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.{RBlockCall, RCodeBlock}
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier

import scala.collection.mutable

class SplitMap extends RefactoringIntention(SplitMap) {

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementToRefactor(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val doBlock = elementToRefactor(focusedElement).get

    UI.showOptionsMenuWith[SplitStatements](
      "Select statements to include",
      statementOptionsForSplitting(doBlock),
      editor,
      selectedOption => runAsWriteCommand {
        new SplitMapApplier(
          selectedOption.blockCall,
          selectedOption.includedStatements
        ).apply()
      }
    )
  }

  private def elementToRefactor(element: PsiElement) = {
    for {
      block <- element.findParentOfType[RBlockCall](treeHeightLimit = 3)
      if block.getCommand == "map"
      if block.getBlock.getCompoundStatement.getStatements.size > 1
    } yield block
  }

  private def statementOptionsForSplitting(blockCall: RBlockCall) = {
    val statements = blockCall.getBlock.getCompoundStatement.getStatements

    (1 until statements.size)
      .map(statements.take(_).toList)
      .map(new SplitStatements(blockCall, _))
  }

  private def runAsWriteCommand(action: => Unit)(implicit project: Project): Unit = {
    WriteCommandAction
      .writeCommandAction(project)
      .withName(optionDescription)
      .run { action }
  }

  override def startInWriteAction = false

  private class SplitStatements(val blockCall: RBlockCall, val includedStatements: List[RPsiElement]) extends SelectionOption {
    override val textRange: TextRange = includedStatements.textRange
    override val optionText: String = includedStatements.last.getText
  }
}

private class SplitMapApplier(blockCallToRefactor: RBlockCall, includedStatements: List[RPsiElement])(implicit project: Project) {
  private val allStatements = blockCallToRefactor.getBlock.getCompoundStatement.getStatements.toList
  private val (beforeStatements, afterStatements) = allStatements.partition(includedStatements.contains(_))
  private val variableNamesFromBeforeBlockUsedInAfterBlock: List[String] = getVariableNamesFromBeforeBlockUsedInAfterBlock

  def apply(): Unit = {
    val (startDelimiter, endDelimiter) = blockCallToRefactor.delimiters
    val newMapAfter = Parser.parseHeredoc(
      s"""
        |receiver.map ${startDelimiter} ||
        |  BODY
        |${endDelimiter}
      """).childOfType[RBlockCall]()
    val newAfterBlock = newMapAfter.getBlock
    val existingBeforeBlock = blockCallToRefactor.getBlock

    replaceWithStatementsAfter(newAfterBlock)
    removeAfterStatementsFrom(existingBeforeBlock)
    addReturnValuesTo(existingBeforeBlock)
    addParametersTo(newAfterBlock)

    newMapAfter.getReceiver.replace(blockCallToRefactor)
    blockCallToRefactor.replace(newMapAfter)
  }

  private def replaceWithStatementsAfter(newAfterBlock: RCodeBlock): Unit = {
    newAfterBlock.getCompoundStatement.replaceStatementsWithRange(
      afterStatements.head,
      afterStatements.last
    )
  }

  private def removeAfterStatementsFrom(mapBeforeBlock: RCodeBlock): Unit = {
    mapBeforeBlock.getCompoundStatement.deleteChildRange(
      afterStatements.head.getPrevSibling.getPrevSibling, // TODO: Find better way to remove extra newline
      afterStatements.last
    )
  }

  private def addReturnValuesTo(existingBeforeBlock: RCodeBlock) = {
    if (variableNamesFromBeforeBlockUsedInAfterBlock.nonEmpty) {
      val codeForExpresionToReturn = if (variableNamesFromBeforeBlockUsedInAfterBlock.size == 1) {
        variableNamesFromBeforeBlockUsedInAfterBlock.head
      } else {
        s"[${variableNamesFromBeforeBlockUsedInAfterBlock.mkString(", ")}]"
      }

      existingBeforeBlock.getCompoundStatement.add(
        Parser.parse(codeForExpresionToReturn).getFirstChild
      )
    }
  }

  private def addParametersTo(newAfterBlock: RCodeBlock): Unit = {
    if (variableNamesFromBeforeBlockUsedInAfterBlock.nonEmpty) {
      variableNamesFromBeforeBlockUsedInAfterBlock.foreach { variableName =>
        newAfterBlock.addParameter(variableName)
      }
    } else {
      newAfterBlock.removeParametersBlock()
    }
  }

  private def getVariableNamesFromBeforeBlockUsedInAfterBlock = {
    beforeStatements.flatMap { statement =>
      val variablesFromBeforeBlockUsedInAfterBlock = new mutable.HashSet[String]()
      statement.forEachLocalVariableReference { identifier =>
        if (isDefinedInsideBeforeBlockAndUsedInAfterBlock(identifier)) {
          variablesFromBeforeBlockUsedInAfterBlock.addOne(identifier.getText)
        }
      }
      variablesFromBeforeBlockUsedInAfterBlock.toList
    }
  }

  private def isDefinedInsideBeforeBlockAndUsedInAfterBlock(identifier: RIdentifier) = {
    identifier
      .referencesInside(blockCallToRefactor)
      .find(isReferencedFromAfterStatements)
      .map(_.asInstanceOf[RIdentifier])
      .exists(wasDeclaredInsideBlockToRefactor)
  }

  private def isReferencedFromAfterStatements(element: PsiElement) = {
    afterStatements.textRange.contains(element.getTextRange)
  }

  private def wasDeclaredInsideBlockToRefactor(element: RIdentifier) = {
    blockCallToRefactor.getBlock.contains(
      element.firstDeclaration
    )
  }
}

object SplitMap extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Split a map block into two successive maps"

  override def optionDescription: String = "Split"
}
