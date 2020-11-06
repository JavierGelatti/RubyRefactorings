package com.refactorings.ruby.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.{PsiElement, PsiFileFactory}
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType

object Parser {
  def parseHeredoc(code: String)(implicit project: Project): PsiElement = {
    parse(code.trim.stripMargin)
  }

  def parse(code: String)(implicit project: Project): PsiElement = {
    CodeStyleManager.getInstance(project).reformat(
      PsiFileFactory
        .getInstance(project)
        .createFileFromText("DUMMY.rb", RubyFileType.RUBY, code)
        .getFirstChild
    )
  }
}