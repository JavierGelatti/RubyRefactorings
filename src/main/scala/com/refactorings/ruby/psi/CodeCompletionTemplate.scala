package com.refactorings.ruby.psi

import com.intellij.codeInsight.template._
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.{Document, Editor, RangeMarker}
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFile}
import com.refactorings.ruby.psi.Extensions._

import java.util.Comparator
import scala.annotation.tailrec
import scala.collection.mutable

class CodeCompletionTemplate(editor: Editor, rootElement: PsiElement, elementsToRename: List[List[RangeMarker]]) {
  private val file: PsiFile = rootElement.getContainingFile
  private val project = file.getProject
  private val document: Document = file.getViewProvider.getDocument
  private val templateRunner = new TemplateRunner(
    document.rangeMarkerFor(rootElement), project, document
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

private class TemplateRunner(val rootElementRange: RangeMarker, project: Project, document: Document) {
  private val templateManager = TemplateManager.getInstance(project)
  private val text: String = rootElementRange.getText

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
    templateManager.startTemplate(editor, template)
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
