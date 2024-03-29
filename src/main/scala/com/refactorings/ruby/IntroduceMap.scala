package com.refactorings.ruby

import com.intellij.icons.AllIcons.Actions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement}
import com.refactorings.ruby.IntroduceMap.optionDescription
import com.refactorings.ruby.psi.Matchers.{EndOfLine, Leaf, Whitespace}
import com.refactorings.ruby.psi.{BlockCallExtension, CodeBlockExtension, CompoundStatementExtension, IdentifierExtension, Parser, PsiElementExtension, PsiListExtension}
import com.refactorings.ruby.ui.{SelectionOption, UI}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.{RBreakStatement, RNextStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.iterators.{RBlockCall, RCodeBlock}
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RIdentifier
import org.jetbrains.plugins.ruby.ruby.lang.psi.visitors.RubyRecursiveElementVisitor

import javax.swing.Icon
import scala.annotation.tailrec
import scala.collection.mutable

class IntroduceMap extends RefactoringIntention(IntroduceMap) {
  override def getIcon(flags: Int): Icon = Actions.RealIntentionBulb

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementToRefactor(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val doBlock = elementToRefactor(focusedElement).get

    UI.showOptionsMenuWith[SplitStatements](
      "Select statements to include in map block",
      statementOptionsForSplitting(doBlock),
      editor,
      selectedOption => runAsWriteCommand(editor) {
        new SplitMapApplier(
          selectedOption.blockCall,
          selectedOption.includedStatements,
          editor
        ).apply()
      }
    )
  }

  private def elementToRefactor(element: PsiElement) = {
    for {
      block <- element.findParentOfType[RBlockCall](treeHeightLimit = 3)
      if List("map", "collect", "each").contains(block.getCommand)
      if block.getBlock.getCompoundStatement.getStatements.size > 1
    } yield block
  }

  private def statementOptionsForSplitting(blockCall: RBlockCall) = {
    val statements = blockCall.getBlock.getCompoundStatement.getStatements

    (1 until statements.size)
      .map(statements.take(_).toList)
      .map(new SplitStatements(blockCall, _))
  }

  private def runAsWriteCommand(editor: Editor)(action: => Unit)(implicit project: Project): Unit = {
    WriteCommandAction
      .writeCommandAction(project)
      .withName(optionDescription)
      .run {
        handlingRefactoringErrors(editor) {
          disablePostprocessFormattingInside { action }
        }
      }
  }

  override def startInWriteAction = false

  private class SplitStatements(val blockCall: RBlockCall, val includedStatements: List[RPsiElement]) extends SelectionOption {
    override val textRange: TextRange = includedStatements.textRange
    override val optionText: String = includedStatements.last.getText
  }
}

private class SplitMapApplier(blockCallToRefactor: RBlockCall, includedStatements: List[RPsiElement], editor: Editor)
                             (implicit project: Project) {
  private val allStatements = blockCallToRefactor.getBlock.getCompoundStatement.allChildren
  private val selectedStatement = includedStatements.last
  private val beforeStatements = allStatements.takeWhile(_ != selectedStatement.getNextSibling)
  private val afterStatements = allStatements.diff(beforeStatements)
  private val variableNamesFromBeforeBlockUsedInAfterBlock: List[String] = getVariableNamesFromBeforeBlockUsedInAfterBlock

  def apply(): Unit = {
    assertThereAreNoIncludedNextOrBreakCalls()

    val (startDelimiter, endDelimiter) = blockCallToRefactor.delimiters
    val newAfterIterator = Parser.parseHeredoc(
      s"""
        |receiver.${commandForAfterBlock} ${startDelimiter}||
        |  BODY
        |${endDelimiter}
      """).childOfType[RBlockCall]()
    val newAfterBlock = newAfterIterator.getBlock
    val existingBeforeBlock = blockCallToRefactor.getBlock

    copyBlockBody(source = existingBeforeBlock, target = newAfterBlock)
    replaceWithAfterStatements(newAfterBlock)
    setCommandForBeforeBlockTo(existingBeforeBlock)
    removeAfterStatementsFrom(existingBeforeBlock)
    addReturnValuesTo(existingBeforeBlock)
    addParametersTo(newAfterBlock)

    val finalElement = putInPlace(newAfterIterator)

    format(finalElement)
  }

  private def assertThereAreNoIncludedNextOrBreakCalls(): Unit = {
    includedStatements.foreach { statement =>
      statement.accept(new RubyRecursiveElementVisitor() {
        override def visitRNextStatement(nextStatement: RNextStatement): Unit =
          throw new CannotApplyRefactoringException(
            "Cannot perform refactoring if next is called inside the selection",
            nextStatement.getTextRange
          )

        override def visitRBreakStatement(breakStatement: RBreakStatement): Unit =
          throw new CannotApplyRefactoringException(
            "Cannot perform refactoring if break is called inside the selection",
            breakStatement.getTextRange
          )

        override def visitRCodeBlock(codeBlock: RCodeBlock): Unit = ()
      })
    }
  }

  private def copyBlockBody(source: RCodeBlock, target: RCodeBlock) = {
    if (source.getBodyStatement != null) {
      target.getBodyStatement.replace(source.getBodyStatement)
    }
  }

  private def replaceWithAfterStatements(newAfterBlock: RCodeBlock): Unit = {
    newAfterBlock.getCompoundStatement.replaceStatementsWithRange(
      shrinkForwardsConsumingWhitespaceAndDelimiters(afterStatements.head), // This ensures we remove extra newlines and semicolons
      afterStatements.last
    )

    @tailrec
    def shrinkForwardsConsumingWhitespaceAndDelimiters(element: PsiElement): PsiElement = {
      element match {
        case Whitespace(_) | Leaf(RubyTokenTypes.tSEMICOLON) | EndOfLine(_) =>
          shrinkForwardsConsumingWhitespaceAndDelimiters(element.getNextSibling)
        case _ => element
      }
    }
  }

  private def removeAfterStatementsFrom(introducedMapBlock: RCodeBlock): Unit = {
    introducedMapBlock.getCompoundStatement.deleteChildRange(
      extendBackwardsConsumingWhitespace(afterStatements.head), // This ensures we remove extra newlines and semicolons
      afterStatements.last
    )

    @tailrec
    def extendBackwardsConsumingWhitespace(element: PsiElement): PsiElement = {
      val previousElement = element.getPrevSibling
      previousElement match {
        case Whitespace(_) | Leaf(RubyTokenTypes.tSEMICOLON) | EndOfLine(_) =>
          extendBackwardsConsumingWhitespace(previousElement)
        case _ => element
      }
    }
  }

  private def addReturnValuesTo(existingBeforeBlock: RCodeBlock) = {
    if (newAfterBlockHasParameters) {
      val codeForExpressionToReturn = if (variableNamesFromBeforeBlockUsedInAfterBlock.size == 1) {
        variableNamesFromBeforeBlockUsedInAfterBlock.head
      } else {
        s"[${variableNamesFromBeforeBlockUsedInAfterBlock.mkString(", ")}]"
      }

      existingBeforeBlock.getCompoundStatement.add(
        Parser.parse(codeForExpressionToReturn).getFirstChild
      )
    }
  }

  private def addParametersTo(newAfterBlock: RCodeBlock): Unit = {
    if (newAfterBlockHasParameters) {
      variableNamesFromBeforeBlockUsedInAfterBlock.foreach { variableName =>
        newAfterBlock.addParameter(variableName)
      }
    } else {
      newAfterBlock.removeParametersBlock()
    }
  }

  private def newAfterBlockHasParameters = {
    variableNamesFromBeforeBlockUsedInAfterBlock.nonEmpty
  }

  private def setCommandForBeforeBlockTo(existingBeforeBlock: RCodeBlock) = {
    existingBeforeBlock.getBlockCall.getCall.getPsiCommand.replace(
      Parser.parse(commandForBeforeBlock).getFirstChild
    )
  }

  private def commandForBeforeBlock = {
    if (originalCommand == "each") "map" else originalCommand
  }

  private def commandForAfterBlock = {
    originalCommand
  }

  private lazy val originalCommand = {
    blockCallToRefactor.getCall.getCommand
  }

  private def putInPlace(newMapAfter: RBlockCall) = {
    newMapAfter.getReceiver.replace(blockCallToRefactor)
    blockCallToRefactor.replace(newMapAfter).asInstanceOf[RBlockCall]
  }

  private def format(finalElement: RBlockCall): Unit = {
    PsiDocumentManager.getInstance(project)
      .doPostponedOperationsAndUnblockDocument(editor.getDocument)

    finalElement.reindent()
    if (newAfterBlockHasParameters) finalElement.getBlock.reformatParametersBlock()
  }

  private def getVariableNamesFromBeforeBlockUsedInAfterBlock = {
    val variablesFromBeforeBlockUsedInAfterBlock = new mutable.LinkedHashSet[String]()
    beforeStatements.foreach { statement =>
      statement.forEachLocalVariableReference { identifier =>
        if (isDefinedInsideBeforeBlockAndUsedInAfterBlock(identifier)) {
          variablesFromBeforeBlockUsedInAfterBlock.addOne(identifier.getText)
        }
      }
    }
    variablesFromBeforeBlockUsedInAfterBlock.toList
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

object IntroduceMap extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce map to split an existing map/each block"

  override def optionDescription: String = "Split by introducing map (may change semantics)"
}
