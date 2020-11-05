package com.refactorings

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiFileFactory}
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes

import scala.annotation.tailrec
import scala.reflect.ClassTag

package object ruby {
  object EndOfLine {
    def unapply(element: PsiElement): Option[LeafPsiElement] = element match {
      case endOfLine: LeafPsiElement if endOfLine.getElementType == RubyTokenTypes.tEOL => Some(endOfLine)
      case _ => None
    }
  }

  sealed class DefaultsTo[Provided, Default]
  object DefaultsTo {
    implicit def useDefault[Default]: DefaultsTo[Default, Default] = new DefaultsTo
    implicit def useProvided[Provided, Default]: DefaultsTo[Provided, Default] = new DefaultsTo
  }



  def getPsiElement[ElementType <: PsiElement]
  (code: String)
  (implicit project: Project, e: ElementType DefaultsTo PsiElement, tag: ClassTag[ElementType]):
  ElementType = {
    CodeStyleManager.getInstance(project).reformat(
      findChild[ElementType](
        PsiFileFactory
          .getInstance(project)
          .createFileFromText("DUMMY.rb", RubyFileType.RUBY, code)
      ).get
    ).asInstanceOf[ElementType]
  }


  @tailrec
  def findParent[T <: PsiElement]
  (descendantElement: PsiElement, treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
  (implicit tag: ClassTag[T]):
  Option[T] = {
    if (treeHeightLimit == 0) return None

    descendantElement.getParent match {
      case null => None
      case element: T if tag.runtimeClass.isInstance(element) && matching(element) => Some(element)
      case otherElement => findParent[T](otherElement, treeHeightLimit - 1)
    }
  }

  def findChild[T <: PsiElement]
  (ancestorElement: PsiElement, treeHeightLimit: Int = -1, matching: T => Boolean = (_: T) => true)
  (implicit tag: ClassTag[T]):
  Option[T] = {
    if (treeHeightLimit == 0) return None

    ancestorElement.getChildren
      .find(
        element => tag.runtimeClass.isInstance(element) && matching(element.asInstanceOf[T])
      )
      .map(_.asInstanceOf[T])
      .orElse({
        ancestorElement.getChildren
          .view
          .flatMap(directChild => findChild[T](directChild, treeHeightLimit - 1, matching))
          .headOption
      })
  }
}
