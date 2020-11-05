package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.refactorings.ruby.ReplaceDefSelfByOpeningSingletonClass.optionDescription
import com.refactorings.ruby.psi.Matchers.EndOfLine
import com.refactorings.ruby.psi.Parser.{parse, parseHeredoc}
import com.refactorings.ruby.psi.PsiElementExtensions.PsiElementExtension
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RBodyStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RObjectClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.{RMethod, RSingletonMethod}

class ReplaceDefSelfByOpeningSingletonClass extends PsiElementBaseIntentionAction {
  @IntentionName override def getText: String = optionDescription

  @IntentionFamilyName override def getFamilyName = "Replace def self by opening singleton class"

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    findSingletonMethodEnclosing(element).isDefined
  }

  private def findSingletonMethodEnclosing(element: PsiElement) = {
    element.findParentOfType[RSingletonMethod](treeHeightLimit = 4)
  }

  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    implicit val currentProject: Project = project

    val singletonMethodToRefactor: RSingletonMethod = findSingletonMethodEnclosing(focusedElement).get
    normalizeSpacesAfterParameterList(singletonMethodToRefactor)

    val openSingletonClassTemplate = parseHeredoc(
    """
     |class << OBJECT
     |  def METHOD_NAME
     |    METHOD_BODY
     |  end
     |end
    """)
    val openSingletonClass = openSingletonClassTemplate.childOfType[RObjectClass]()
    val newMethodDefinition = openSingletonClassTemplate.childOfType[RMethod]()

    copyParametersAndBody(source = singletonMethodToRefactor, target = newMethodDefinition)

    openSingletonClass.getObject.replace(singletonMethodToRefactor.getClassObject)
    singletonMethodToRefactor.replace(openSingletonClass)
  }

  /**
   * This is necessary because the Ruby parser from org.jetbrains.plugins.ruby does not always use the same PsiElement
   * type to represent new lines in the code. The parser can produce either a PsiWhiteSpace('\n') or a
   * PsiElement(end of line).
   *
   * This should be an implementation detail that we shouldn't need to care about. However, sometimes the formatter
   * behaves differently if it receives either a PsiWhiteSpace with newlines or a PsiElement(end of line).
   *
   * In particular, this affects the code modification results when trying to edit methods. This happens because:
   * - When a method has its parameter declarations between parentheses, the parser produces a "Function argument list"
   *   followed by a PsiWhiteSpace('\n').
   * - When a method has its parameter declarations without parentheses, the parser produces a "Command argument list"
   *   followed by a PsiElement(end of line).
   * - After performing a change, the formatter correctly fixes the indentation when there's a PsiWhiteSpace after the
   *   method arguments, but it does not when there's a PsiElement(end of line).
   *
   * To overcome this problem, we normalize the spaces after the parameter list so that they're always represented by a
   * PsiWhiteSpace. In this way, the formatter works fine after performing changes in the PsiElements.
   */
  private def normalizeSpacesAfterParameterList
    (methodToNormalize: RMethod)
    (implicit project: Project):
  Unit = {
    val argumentList = methodToNormalize.getArgumentList

    (argumentList.getNextSibling, argumentList.getNextSibling.getNextSibling) match {
      case (EndOfLine(eol), space: PsiWhiteSpace) =>
        val whitespace = parse(s"\n${space.getText}")

        // Ordering here matters: swapping these two lines causes a PsiInvalidElementAccessException
        space.replace(whitespace)
        eol.delete()
      case _ => ()
    }
  }

  private def copyParametersAndBody
    (source: RSingletonMethod, target: RMethod)
    (implicit project: Project)
  = {
    val sourceName = source.getMethodName
    val sourceBody = source.childOfType[RBodyStatement]()
    val targetBody = target.childOfType[RBodyStatement]()

    target.getMethodName.setName(source.getNameIdentifier.getText)

    val targetArgumentList = target.getArgumentList
    target.addRangeBefore(sourceName.getNextSibling, sourceBody.getPrevSibling.getPrevSibling, targetArgumentList)
    targetArgumentList.delete()

    targetBody.replace(sourceBody)
  }
}

object ReplaceDefSelfByOpeningSingletonClass {
  val optionDescription = "Open singleton class instead"
}
