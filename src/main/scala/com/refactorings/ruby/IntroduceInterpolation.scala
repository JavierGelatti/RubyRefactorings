package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.refactorings.ruby.psi.Parser
import com.refactorings.ruby.psi.PsiElementExtensions.{EditorExtension, LeafPsiElementExtension, PsiElementExtension, StringLiteralExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.stringLiterals.{RExpressionSubstitution, RStringLiteral}

import scala.PartialFunction.condOpt

class IntroduceInterpolation extends RefactoringIntention(IntroduceInterpolation) {
  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    implicit val currentProject: Project = project
    val (stringLiteralToRefactor, focusedStringPart) = elementsToRefactor(focusedElement).get

    val (antesDelCaret, despuesDelCaret) = focusedStringPart.textSplitOnOffset(editor.getCaretModel.getOffset)
    val template = Parser.parseHeredoc(
      s"""
        |"${antesDelCaret}#{""}${despuesDelCaret}"
      """)
    val newStringLiteral = template.childOfType[RStringLiteral]()
    val expressionSubstitution: RExpressionSubstitution = newStringLiteral.getExpressionSubstitutions.head
    val stringInside = expressionSubstitution.getCompoundStatement.getStatements.head.asInstanceOf[RStringLiteral]

    val stringInsideMark = stringInside.mark()
    newStringLiteral.getPsiContent.forEach(element => {
      stringLiteralToRefactor.addBefore(element, focusedStringPart)
    })
    if (focusedStringPart.isStringContent) {
      focusedStringPart.delete()
    }

    editor.selectElement(stringLiteralToRefactor.childMarkedWith(stringInsideMark))
  }

  override def isAvailable(project: Project, editor: Editor, focusedElement: PsiElement): Boolean = {
    elementsToRefactor(focusedElement).isDefined
  }

  private def elementsToRefactor(focusedElement: PsiElement) = {
    for {
      stringLiteralToRefactor <- focusedElement.findParentOfType[RStringLiteral](treeHeightLimit = 1)
      if stringLiteralToRefactor.isDoubleQuoted
      focusedStringPart <- condOpt(focusedElement) {
        case stringPart: LeafPsiElement
          if !stringPart.isOfType(RubyTokenTypes.tDOUBLE_QUOTED_STRING_BEG) => stringPart
      }
    } yield (stringLiteralToRefactor, focusedStringPart)
  }
}

object IntroduceInterpolation extends RefactoringIntentionCompanionObject {
  override def familyName: String = "Introduce new interpolation point inside of string"

  override def optionDescription: String = "Introduce interpolation"
}




