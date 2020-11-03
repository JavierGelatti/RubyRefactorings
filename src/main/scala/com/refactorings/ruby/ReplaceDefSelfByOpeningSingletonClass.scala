package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.{PsiElement, PsiFileFactory, PsiWhiteSpace}
import com.refactorings.ruby.ReplaceDefSelfByOpeningSingletonClass.optionDescription
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
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
        case openSingletonClassBeforeMethod: RObjectClass => {
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
        }
        case _ => {
          openSingletonClass.getObject.replace(singletonMethodToRefactor.getClassObject)
          singletonMethodToRefactor.replace(openSingletonClass)
        }
      }
    } else {
      openSingletonClass.getObject.replace(singletonMethodToRefactor.getClassObject)
      singletonMethodToRefactor.replace(openSingletonClass)
    }
  }

  private def copyParametersAndBody
    (source: RSingletonMethod, target: RMethod)
    (implicit project: Project)
  = {
    target.setName(source.getNameIdentifier.getText)
    target.getArgumentList.replace(source.getArgumentList)
    if (hasParametersBetweenParentheses(source)) {
      addParenthesesToParameters(target)
    }
    target.getCompoundStatement.replace(source.getCompoundStatement)
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
    findChild[ElementType](
      PsiFileFactory
        .getInstance(project)
        .createFileFromText("DUMMY.rb", RubyFileType.RUBY, code)
    ).get
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

  sealed class DefaultsTo[Provided, Default]
  object DefaultsTo {
    implicit def useDefault[Default]: DefaultsTo[Default, Default] = new DefaultsTo
    implicit def useProvided[Provided, Default]: DefaultsTo[Provided, Default] = new DefaultsTo
  }
}

object ReplaceDefSelfByOpeningSingletonClass {
  val optionDescription = "Open singleton class instead"
}
