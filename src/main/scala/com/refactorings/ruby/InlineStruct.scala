package com.refactorings.ruby

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import com.refactorings.ruby.psi.Matchers.Leaf
import com.refactorings.ruby.psi.{Parser, PsiElementExtension}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.basicTypes.RSymbol
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.names.RSuperClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.methodCall.RCall
import org.jetbrains.plugins.ruby.ruby.lang.psi.references.RDotReference

import scala.PartialFunction._
import scala.annotation.tailrec

class InlineStruct extends RefactoringIntention(InlineStruct) {
  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    elementsToRefactorFrom(element).isDefined
  }

  override protected def invoke(editor: Editor, focusedElement: PsiElement)(implicit currentProject: Project): Unit = {
    val (currentClass, structMembers) = elementsToRefactorFrom(focusedElement).get

    addStructBodyTo(currentClass, structMembers)
    deleteSuperclassOf(currentClass)
  }

  private def elementsToRefactorFrom(focusedElement: PsiElement): Option[(RClass, List[RSymbol])] = {
    for {
      structCreationMessage <- structCreationMessageFrom(focusedElement)
      superClass <- structCreationMessage.findParentOfType[RSuperClass](treeHeightLimit = 1)
      currentClass <- superClass.findParentOfType[RClass](treeHeightLimit = 1)
      structMembers <- structMembersFrom(structCreationMessage)
    } yield (currentClass, structMembers)
  }

  private def structCreationMessageFrom(focusedElement: PsiElement): Option[RCall] = {
    for {
      structCreationMessage <- focusedElement.findParentOfType[RCall](treeHeightLimit = 4)
      structCreationCommand <- condOpt(structCreationMessage.getPsiCommand) {
        case dotReference: RDotReference => dotReference
      }
      if structCreationCommand.getReceiver.textMatches("Struct")
      if structCreationCommand.getCommand == "new"
    } yield structCreationMessage
  }

  private def structMembersFrom(structCreationMessage: RCall): Option[List[RSymbol]] = {
    val structCreationArguments = structCreationMessage.getCallArguments.getElements
    if (structCreationArguments.isEmpty) return None

    val structMembers = structCreationArguments.map {
      case literalSymbol: RSymbol => literalSymbol
      case _ => return None
    }

    Some(structMembers.toList)
  }

  private def addStructBodyTo(currentClass: RClass, structMembers: List[RSymbol])
                             (implicit project: Project) = {
    val structMemberNames = structMembers.map(_.getValue)
    val attrAccessorArguments = structMemberNames.map(memberName => s":${memberName}").mkString(", ")
    val initializeArguments = structMemberNames.mkString(", ")
    val instanceVariablesInitialization = structMemberNames.map(memberName => s"@${memberName} = ${memberName}").mkString("\n")

    val contentToInline = Parser.parseHeredoc(
      s"""
         |attr_accessor ${attrAccessorArguments}
         |
         |def initialize(${initializeArguments})
         |  ${instanceVariablesInitialization}
         |end
      """)

    val classBody = currentClass.getCompoundStatement
    classBody.addBefore(contentToInline, classBody.getFirstChild)
  }

  private def deleteSuperclassOf(aClass: RClass): Unit = {
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
  override def familyName: String = "Inline Struct superclass"

  override def optionDescription: String = "Inline Struct"
}
