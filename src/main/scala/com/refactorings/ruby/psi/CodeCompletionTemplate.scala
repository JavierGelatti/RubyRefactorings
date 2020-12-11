package com.refactorings.ruby.psi

import com.intellij.codeInsight.template.TemplateBuilderFactory
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement

class CodeCompletionTemplate(editor: Editor, rootElement: PsiElement, elementsToRename: List[List[PsiElement]]) {
  private val builder = TemplateBuilderFactory.getInstance().createTemplateBuilder(rootElement)

  def run(): Unit = {
    WriteCommandAction.writeCommandAction(rootElement.getProject).run(() => {
      createPlaceholdersForElementsToRename()
      builder.run(editor, true)
    })
  }

  private def createPlaceholdersForElementsToRename(): Unit = {
    elementsToRename.zipWithIndex.foreach {
      case (elementReferences, index) => createPlaceholdersForElement(elementReferences, index)
    }
  }

  private def createPlaceholdersForElement(elementReferences: List[PsiElement], elementIndex: Int): Unit = {
    if (elementReferences.isEmpty) return

    createMainCompletionPlaceholderFor(elementIndex, elementReferences.head)
    elementReferences.tail.foreach(createReplicaPlaceholderFor(elementIndex, _))
  }

  private def createMainCompletionPlaceholderFor(elementIndex: Int, element: PsiElement): Unit = {
    builder.replaceElement(
      element,
      s"$$PLACEHOLDER_${elementIndex}$$",
      new ConstantNode(element.getText),
      true
    )
  }

  private def createReplicaPlaceholderFor(elementIndex: Int, element: PsiElement): Unit = {
    builder.replaceElement(
      element,
      s"$$PLACEHOLDER_${elementIndex}_REPLICA$$",
      s"$$PLACEHOLDER_${elementIndex}$$",
      false
    )
  }
}
