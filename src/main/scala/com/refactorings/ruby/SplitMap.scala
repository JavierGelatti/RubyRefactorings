package com.refactorings.ruby

import com.intellij.icons.AllIcons.Actions
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.{PsiDocumentManager, PsiElement, PsiWhiteSpace}
import com.refactorings.ruby.SplitMap.optionDescription
import com.refactorings.ruby.psi.Matchers.{EndOfLine, Leaf}
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

class SplitMap extends RefactoringIntention(SplitMap) {
  override def getIcon(flags: Int): Icon = Actions.RealIntentionBulb

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementToRefactor(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val doBlock = elementToRefactor(focusedElement).get

    UI.showOptionsMenuWith[SplitStatements](
      "Select statements to include",
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
  private val allStatements = blockCallToRefactor.getBlock.getCompoundStatement.getStatements.toList
  private val (beforeStatements, afterStatements) = allStatements.partition(includedStatements.contains(_))
  private val variableNamesFromBeforeBlockUsedInAfterBlock: List[String] = getVariableNamesFromBeforeBlockUsedInAfterBlock

  def apply(): Unit = {
    assertThereAreNoIncludedNextOrBreakCalls()

    val (startDelimiter, endDelimiter) = blockCallToRefactor.delimiters
    val newMapAfter = Parser.parseHeredoc(
      s"""
        |receiver.${commandForAfterBlock} ${startDelimiter}||
        |  BODY
        |${endDelimiter}
      """).childOfType[RBlockCall]()
    val newAfterBlock = newMapAfter.getBlock
    val existingBeforeBlock = blockCallToRefactor.getBlock

    copyBlockBody(source = existingBeforeBlock, target = newAfterBlock)
    replaceWithStatementsAfter(newAfterBlock)
    setCommandForBeforeBlockTo(existingBeforeBlock)
    removeAfterStatementsFrom(existingBeforeBlock)
    addReturnValuesTo(existingBeforeBlock)
    addParametersTo(newAfterBlock)

    val finalElement = putInPlace(newMapAfter)

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

  private def replaceWithStatementsAfter(newAfterBlock: RCodeBlock): Unit = {
    newAfterBlock.getCompoundStatement.replaceStatementsWithRange(
      afterStatements.head,
      afterStatements.last
    )
  }

  private def removeAfterStatementsFrom(mapBeforeBlock: RCodeBlock): Unit = {
    mapBeforeBlock.getCompoundStatement.deleteChildRange(
      extendBackwardsConsumingWhitespace(afterStatements.head), // This ensures we remove extra newlines and semicolons
      afterStatements.last
    )

    @tailrec
    def extendBackwardsConsumingWhitespace(element: PsiElement): PsiElement = {
      element.getPrevSibling match {
        case whitespace: PsiWhiteSpace => extendBackwardsConsumingWhitespace(whitespace)
        case semicolon@Leaf(RubyTokenTypes.tSEMICOLON) => semicolon
        case endOfLine@EndOfLine(_) => endOfLine
        case _ => element
      }
    }
  }

  private def addReturnValuesTo(existingBeforeBlock: RCodeBlock) = {
    if (newAfterBlockHasParameters) {
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
  override def familyName: String = "Split a map/each block, generating a map block preceding it"

  override def optionDescription: String = "Split into map (may change semantics)"
}
