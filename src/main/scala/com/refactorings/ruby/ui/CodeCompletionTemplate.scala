package com.refactorings.ruby.ui

import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.{ConstantNode, TemplateState}
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.{Document, Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, SmartPsiElementPointer}
import com.refactorings.ruby.psi._
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.fields.RInstanceVariable

import java.util.Comparator
import scala.annotation.tailrec
import scala.collection.mutable

class CodeCompletionTemplate(editor: Editor, rootElement: PsiElement, elementsToRename: List[List[RangeMarker]]) {
  private implicit val project: Project = editor.getProject
  private val document: Document = editor.getDocument
  private val templateRunner = new TemplateRunner(
    rootElement, project, document
  )

  def run(): Unit = {
    createPlaceholdersForElementsToRename()

    WriteCommandAction
      .writeCommandAction(rootElement.getProject)
      .run(() => templateRunner.createAndStartTemplate(editor))
  }

  private def createPlaceholdersForElementsToRename(): Unit = {
    elementsToRename.zipWithIndex.foreach {
      case (mainElement :: replicaElements, index) =>
        templateRunner.addVariable(s"PLACEHOLDER_${index}", mainElement, replicaElements)
      case _ => ()
    }
  }
}

object CodeCompletionTemplate {
  private type ElementPointer = SmartPsiElementPointer[PsiElement]

  def startIn(editor: Editor, rootElement: PsiElement, pointersToElementsToRename: List[List[ElementPointer]]): Unit = {
    new CodeCompletionTemplate(
      editor,
      rootElement,
      elementsToRename = pointersToElementsToRename.map(_.map(rangeMarkerFor))
    ).run()

    def rangeMarkerFor(pointer: SmartPsiElementPointer[PsiElement]) = {
      pointer.getElement match {
        case ivar: RInstanceVariable =>
          editor.rangeMarkerFor(ivar.getTextRange.shrinkLeft(1))
        case element =>
          editor.rangeMarkerFor(element)
      }
    }
  }
}

private class TemplateRunner(rootElement: PsiElement, project: Project, document: Document) {
  private val rootElementRange = document.rangeMarkerFor(rootElement)
  private val templateManager = TemplateManager.getInstance(project)
  private val text = rootElementRange.getText

  private val variables: mutable.TreeSet[TemplateVariable] = new mutable.TreeSet[TemplateVariable]()

  def addVariable(variableName: String, mainRange: RangeMarker, replicaRanges: List[RangeMarker]): Unit = {
    variables.add(MainVariable(mainRange, variableName, mainRange.getText, rootElementRange))
    replicaRanges.foreach { replicaRange =>
      variables.add(ReplicaVariable(replicaRange, s"${variableName}_REPLICA", variableName, rootElementRange))
    }
  }

  def createAndStartTemplate(editor: Editor): Unit = {
    val template: Template = createTemplate

    editor.moveCaretTo(rootElementRange.getStartOffset)
    templateManager.startTemplate(editor, template, new TemplateEditingListener {
      override def beforeTemplateFinished(state: TemplateState, template: Template): Unit = {
        /**
         * We need to do this because, since v213.* the parsed code does not always match the text.
         *
         * This is caused by the parser acting weirdly while the template is being applied (and the source ranges for
         * the variables are cleared), and parsing newlines as WhiteSpace("\n"). Those are then maintained when the
         * source is reparsed after the template variables are filled in, which results in a wrong PSI structure.
         *
         * If you want to investigate why this happens, these are some relevant places in the code you can look at:
         * - com.intellij.codeInsight.template.impl.TemplateState#processAllExpressions(...)
         *   start debugging at the first call of calcResults.
         * - com.intellij.psi.impl.DocumentCommitThread#doCommit(...)
         *   this method is executed when a document is committed, which in turn triggers the reparsing process.
         * - com.intellij.psi.impl.BlockSupportImpl#findReparseableRoots(...)
         *   this is called when the IDE tries to reparse a block, and sometimes wrongly concludes that a given element
         *   can be reused during the reparsing process.
         */
        forceReparsingOfDocument()
      }

      override def templateFinished(template: Template, brokenOff: Boolean): Unit = ()

      override def templateCancelled(template: Template): Unit = ()

      override def currentVariableChanged(templateState: TemplateState, template: Template, oldIndex: Int, newIndex: Int): Unit = ()

      override def waitingForInput(template: Template): Unit = ()
    })
  }

  private def createTemplate = {
    val template: Template = templateManager.createTemplate("", "")
    template.setInline(true)
    template.setToReformat(false)
    template.setToIndent(false)

    addVariablesTo(template)

    template
  }

  private def addVariablesTo(template: Template): Unit = {
    addVariablesToTemplate(variables.toList, startOffset = 0)
    template.addTextSegment(
      text.substring(variables.last.endOffset, text.length)
    )

    @tailrec
    def addVariablesToTemplate(variablesToAdd: List[TemplateVariable], startOffset: Int): Unit = {
      variablesToAdd match {
        case variable :: restOfVariables =>
          require(startOffset <= variable.startOffset)

          template.addTextSegment(text.substring(startOffset, variable.startOffset))
          template.addVariable(variable)
          document.deleteStringIn(variable.range)

          addVariablesToTemplate(restOfVariables, startOffset = variable.endOffset)
        case Nil => ()
      }
    }
  }

  private def forceReparsingOfDocument(): Unit = {
    val variableRanges = variables.toList.map(_.range)
    val rangeStart = variableRanges.map(_.getStartOffset).min
    val rangeEnd = variableRanges.map(_.getEndOffset).max

    WriteCommandAction
      .writeCommandAction(project)
      .run(() => document.forceReparse(rangeStart, rangeEnd)(project))
  }
}

private sealed trait TemplateVariable {
  val range: RangeMarker
  val variableName: String
  val rootElementRange: RangeMarker

  val startOffset: Int = range.getStartOffset - rootElementRange.getStartOffset
  val endOffset: Int = range.getEndOffset - rootElementRange.getStartOffset
}

private object TemplateVariable {
  private implicit val rangeOrdering: Ordering[RangeMarker] = Ordering.comparatorToOrdering(
    RangeMarker.BY_START_OFFSET.asInstanceOf[Comparator[RangeMarker]]
  )

  implicit val ord: Ordering[TemplateVariable] = Ordering.by { element: TemplateVariable => element.range }
}

private case class MainVariable(range: RangeMarker, variableName: String, initialValue: String, rootElementRange: RangeMarker) extends TemplateVariable {
  val expression: Expression = new ConstantNode(initialValue)
}

private case class ReplicaVariable(range: RangeMarker, variableName: String, dependantVariableName: String, rootElementRange: RangeMarker) extends TemplateVariable
