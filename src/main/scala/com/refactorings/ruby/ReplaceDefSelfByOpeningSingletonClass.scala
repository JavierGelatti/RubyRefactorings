package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFileFactory, PsiWhiteSpace}
import com.refactorings.ruby.ReplaceDefSelfByOpeningSingletonClass.optionDescription
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.RBodyStatement
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RObjectClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.{RMethod, RSingletonMethod}
import org.jetbrains.plugins.ruby.ruby.lang.psi.expressions.RGroupedExpression

import scala.annotation.tailrec
import scala.reflect.ClassTag

class ReplaceDefSelfByOpeningSingletonClass extends PsiElementBaseIntentionAction {
  @IntentionName override def getText: String = optionDescription

  @IntentionFamilyName override def getFamilyName = "Replace def self by opening singleton class"

  override def invoke(project: Project, editor: Editor, focusedElement: PsiElement): Unit = {
    implicit val currentProject: Project = project

    val singletonMethodToRefactor: RSingletonMethod = singletonMethodEnclosing(focusedElement).get
    normalizeSpacesAfterParameterList(singletonMethodToRefactor)

    val openSingletonClassTemplate = getPsiElement(
    """
     |class << OBJECT
     |  def NAME
     |    BODY
     |  end
     |end
    """.trim.stripMargin)
    val openSingletonClass = findChild[RObjectClass](openSingletonClassTemplate).get
    val newMethodDefinition = findChild[RMethod](openSingletonClassTemplate).get

    copyParametersAndBody(source = singletonMethodToRefactor, target = newMethodDefinition)

    if (FeatureFlag.MergeSingletonClasses.isActive) {
      val previousElement = PsiTreeUtil.skipSiblingsBackward(singletonMethodToRefactor, classOf[LeafPsiElement], classOf[PsiWhiteSpace])
      previousElement match {
        case openSingletonClassBeforeMethod: RObjectClass =>
          openSingletonClassBeforeMethod.getCompoundStatement.add(newMethodDefinition)
          singletonMethodToRefactor.getNextSibling
          val whitespaceBetween = PsiTreeUtil.getElementsOfRange(openSingletonClassBeforeMethod, singletonMethodToRefactor)
          whitespaceBetween.remove(openSingletonClassBeforeMethod)
          whitespaceBetween.remove(singletonMethodToRefactor)

          whitespaceBetween.forEach {
            case _: PsiWhiteSpace => ()
            case element => element.delete()
          }
          singletonMethodToRefactor.delete()
        case _ =>
          openSingletonClass.getObject.replace(singletonMethodToRefactor.getClassObject)
          singletonMethodToRefactor.replace(openSingletonClass)
      }
    } else {
      openSingletonClass.getObject.replace(singletonMethodToRefactor.getClassObject)
      singletonMethodToRefactor.replace(openSingletonClass)
    }
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
        val whitespace = getPsiElement[PsiWhiteSpace](s"\n${space.getText}")

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
    target.getMethodName.setName(source.getNameIdentifier.getText)
    target.getArgumentList.replace(source.getArgumentList)
    if (hasParametersBetweenParentheses(source)) {
      addParenthesesToParameters(target)
    }

    val targetBodyStatement = findChild[RBodyStatement](target).get
    val sourceBodyStatement = findChild[RBodyStatement](source).get

    targetBodyStatement.replace(sourceBodyStatement)
  }

  private def addParenthesesToParameters
    (method: RMethod)
    (implicit project: Project)
  = {
    val parentheses = getPsiElement[RGroupedExpression]("()")
    val leftParen = parentheses.getFirstChild
    val rightParen = parentheses.getLastChild
    method.addBefore(leftParen, method.getArgumentList)
    method.addAfter(rightParen, method.getArgumentList)
  }

  private def hasParametersBetweenParentheses(method: RMethod) = {
    method.getArgumentList.getPrevSibling.textMatches("(")
  }

  private def getPsiElement[ElementType <: PsiElement]
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

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    singletonMethodEnclosing(element).isDefined
  }

  private def singletonMethodEnclosing(element: PsiElement) = {
    findParent[RSingletonMethod](element, treeHeightLimit = 4)
  }

  @tailrec
  private def findParent[T <: PsiElement]
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

  private def findChild[T <: PsiElement]
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
}

object ReplaceDefSelfByOpeningSingletonClass {
  val optionDescription = "Open singleton class instead"
}
