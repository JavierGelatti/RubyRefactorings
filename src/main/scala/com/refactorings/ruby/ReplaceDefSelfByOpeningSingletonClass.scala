package com.refactorings.ruby

import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction
import com.intellij.codeInspection.util.{IntentionFamilyName, IntentionName}
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.{PsiElement, PsiFileFactory, PsiWhiteSpace}
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.{PsiTreeUtil, PsiUtil}
import com.refactorings.ruby.ReplaceDefSelfByOpeningSingletonClass.optionDescription
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.classes.RObjectClass
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.methods.{RMethod, RSingletonMethod}
import org.jetbrains.plugins.ruby.ruby.lang.{RubyFileType, RubyLanguage}

class ReplaceDefSelfByOpeningSingletonClass extends PsiElementBaseIntentionAction {
  @IntentionName override def getText: String = optionDescription

  @IntentionFamilyName override def getFamilyName = "Replace def self by opening singleton class"

  override def invoke(project: Project, editor: Editor, elementPosta: PsiElement): Unit = {
    val singletonMethod: RSingletonMethod = PsiTreeUtil.findFirstParent(
      elementPosta,
      (elemento: PsiElement) => {
        elemento.isInstanceOf[RSingletonMethod]
      }
    ).asInstanceOf[RSingletonMethod]

    val openSingletonClassTemplate = getPsiElement(project,
      """
        |class PARENT
        |  class << self
        |    def NAME
        |      BODY
        |    end
        |  end
        |end
      """.trim.stripMargin).getFirstChild

    val method = PsiTreeUtil.findChildOfType(openSingletonClassTemplate, classOf[RMethod])
    method.setName(singletonMethod.getNameIdentifier.getText)
    method.getCompoundStatement.replace(singletonMethod.getCompoundStatement)
    val openSingletonClass = PsiTreeUtil.findChildOfType(openSingletonClassTemplate, classOf[RObjectClass])

    singletonMethod.replace(openSingletonClass)
  }

  private def getPsiElement(project: Project, code: String) = {
    PsiFileFactory.getInstance(project)
      .createFileFromText("DUMMY.rb", RubyFileType.RUBY, code)
  }

  override def isAvailable(project: Project, editor: Editor, element: PsiElement): Boolean = {
    true
  }
}

object ReplaceDefSelfByOpeningSingletonClass {
  val optionDescription = "Open singleton class instead"
}