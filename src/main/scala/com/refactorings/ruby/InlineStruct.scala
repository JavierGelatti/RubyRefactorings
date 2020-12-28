package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.refactorings.ruby.psi.Matchers.{EndOfLine, Leaf}
import com.refactorings.ruby.psi.PsiElementExtension
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass

import scala.annotation.tailrec

class InlineStruct extends RefactoringIntention(InlineStruct) {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementToRefactorFrom(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val currentClass = elementToRefactorFrom(focusedElement).get

    deleteSuperclassOf(currentClass)
  }

  private def elementToRefactorFrom(focusedElement: PsiElement) = {
    focusedElement.findParentOfType[RClass]()//(treeHeightLimit = 5)
  }

  def deleteSuperclassOf(aClass: RClass): Unit = {
    val superClass = aClass.getPsiSuperClass
    aClass.deleteChildRange(
      extendBackwardsConsumingInheritanceOperator(superClass),
      superClass
    )

    @tailrec
    def extendBackwardsConsumingInheritanceOperator(element: PsiElement): PsiElement = {
      element.getPrevSibling match {
        case whitespace: PsiWhiteSpace => extendBackwardsConsumingInheritanceOperator(whitespace)
        case inheritanceOperator@Leaf(RubyTokenTypes.tLT) => extendBackwardsConsumingInheritanceOperator(inheritanceOperator)
        case _ => element
      }
    }
  }
}

object InlineStruct extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce map to split an existing map/each block"

  override def optionDescription: String = "Split by introducing map (may change semantics)"
}
