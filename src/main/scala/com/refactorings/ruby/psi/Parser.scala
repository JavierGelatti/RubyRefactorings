package com.refactorings.ruby.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiFileFactory}
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType

object Parser {
  def endOfLine(implicit project: Project): LeafPsiElement = {
    parse("nil\n")
      .getNextSibling
      .asInstanceOf[LeafPsiElement]
  }

  def parseHeredoc(code: String)(implicit project: Project): PsiElement = {
    parse(code.trim.stripMargin)
  }

  def parse(code: String)(implicit project: Project): PsiElement = {
    val parsedElement = PsiFileFactory
      .getInstance(project)
      .createFileFromText("DUMMY.rb", RubyFileType.RUBY, code)
      .getFirstChild

    reformatElement(parsedElement)
  }

  private def reformatElement(parsedElement: PsiElement)(implicit project: Project) = {
    val styleManager = CodeStyleManager.getInstance(project)
    styleManager.reformat(
      parsedElement
    )
  }
}
