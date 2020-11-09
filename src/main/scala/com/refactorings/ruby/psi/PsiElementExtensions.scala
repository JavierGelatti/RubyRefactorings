package com.refactorings.ruby.psi

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.refactorings.ruby.list2Scala
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.RPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.RStringLiteral
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.{RArgumentToBlock, RCall}

import scala.reflect.ClassTag

object PsiElementExtensions {
  implicit class PsiElementExtension(sourceElement: PsiElement) {
    def contains(otherElement: PsiElement): Boolean = {
      sourceElement.getTextRange.contains(otherElement.getTextRange)
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
      arguments.lastOption.collect {
        case _: RArgumentToBlock => arguments.dropRight(1).last
        case x => x
      }
    }
  }

  implicit class StringLiteralExtension(sourceElement: RStringLiteral) extends PsiElementExtension(sourceElement) {
    def isDoubleQuoted: Boolean = {
      sourceElement.getStringBeginning.getNode.getElementType == RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG
    }
  }

  implicit class LeafPsiElementExtension(sourceElement: LeafPsiElement) extends PsiElementExtension(sourceElement) {
    def isStringContent: Boolean = {
      isOfType(RubyTokenTypes.tSTRING_CONTENT)
    }

    def isOfType(nodeType: IElementType): Boolean = {
      sourceElement.getElementType == nodeType
    }

    def partitionTextOn(firstOffset: Int, secondOffset: Int): (String, String, String) = {
      val relativeOffset1 = firstOffset - sourceElement.getTextRange.getStartOffset
      val relativeOffset2 = secondOffset - sourceElement.getTextRange.getStartOffset
      val text = sourceElement.getText
      (
        text.substring(0, relativeOffset1),
        text.substring(relativeOffset1, relativeOffset2),
        text.substring(relativeOffset2)
      )
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

