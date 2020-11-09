package com.refactorings.ruby.psi

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.refactorings.ruby.list2Scala
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RArgumentToBlock, RCall}

import scala.reflect.ClassTag

object PsiElementExtensions {
  implicit class PsiElementExtension(sourceElement: PsiElement) {
    def contains(otherElement: PsiElement): Boolean = {
      sourceElement.getTextRange.contains(otherElement.getTextRange)
    }

    def parentOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : T = {
      findParentOfType[T](treeHeightLimit, matching).get
    }

    def findParentOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : Option[T] = {
      if (treeHeightLimit == 0) return None

      sourceElement.getParent match {
        case null => None
        case element: T if tag.runtimeClass.isInstance(element) && matching(element) => Some(element)
        case otherElement => otherElement.findParentOfType[T](treeHeightLimit - 1)
      }
    }

    def childOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : T = {
      findChildOfType[T](treeHeightLimit, matching).get
    }

    def findChildOfType[T <: PsiElement]
    (treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
    (implicit tag: ClassTag[T])
    : Option[T] = {
      if (treeHeightLimit == 0) return None

      sourceElement.getChildren
        .find(
          element => tag.runtimeClass.isInstance(element) && matching(element.asInstanceOf[T])
        )
        .map(_.asInstanceOf[T])
        .orElse({
          sourceElement.getChildren
            .view
            .flatMap(directChild => directChild.findChildOfType[T](treeHeightLimit - 1, matching))
            .headOption
        })
    }

    def mark(): PsiElementMarker = {
      val marker = new PsiElementMarker
      PsiTreeUtil.mark(sourceElement, marker)
      marker
    }

    def childMarkedWith(mark: PsiElementMarker): PsiElement = {
      PsiTreeUtil.releaseMark(sourceElement, mark)
    }
  }

  class PsiElementMarker

  implicit class MessageSendExtension(sourceElement: RCall) extends PsiElementExtension(sourceElement) {
    def lastArgument: Option[RPsiElement] = {
      val arguments = sourceElement.getCallArguments.getElements
      arguments.lastOption.flatMap {
        case _: RArgumentToBlock => arguments.dropRight(1).lastOption
        case x => Some(x)
      }
    }
  }

  implicit class EditorExtension(editor: Editor) {
    def selectElement(elementToSelect: PsiElement): Unit = {
      val elementTextRange = elementToSelect.getTextRange
      editor.getCaretModel.getPrimaryCaret.moveToOffset(
        elementTextRange.getEndOffset
      )
      editor.getCaretModel.getPrimaryCaret.setSelection(
        elementTextRange.getStartOffset,
        elementTextRange.getEndOffset
      )
    }
  }
}

