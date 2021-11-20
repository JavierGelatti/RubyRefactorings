package com.refactorings.ruby.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.{PsiElement, PsiFileFactory}
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.jetbrains.plugins.ruby.ruby.lang.psi.controlStructures.blocks.{RBeginEndBlockStatement, RCompoundStatement}
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.{RConstant, RPseudoConstant}

object Parser {
  def endOfLine(implicit project: Project): LeafPsiElement = {
    parse("nil\n")
      .getNextSibling
      .asInstanceOf[LeafPsiElement]
  }

  def nil(implicit project: Project): RPseudoConstant = {
    parse("nil")
      .getFirstChild
      .asInstanceOf[RPseudoConstant]
  }

  def emptyCompoundStatement(implicit project: Project): RCompoundStatement = parse("").asInstanceOf[RCompoundStatement]

  def beginEndBlockWith(statements: RCompoundStatement)(implicit project: Project): RBeginEndBlockStatement = {
    val newBlock = parseHeredoc(
      """
        |begin
        |  BODY
        |end
        """
    ).childOfType[RBeginEndBlockStatement]()

    newBlock.childOfType[RConstant]().replaceWithBlock(statements)

    newBlock
  }

  def parseHeredoc(code: String)(implicit project: Project): PsiElement = {
    parse(code.trim.stripMargin)
  }

  def parse(code: String, reformat: Boolean = true)(implicit project: Project): PsiElement = {
    val parsedElement = PsiFileFactory
      .getInstance(project)
      .createFileFromText("DUMMY.rb", RubyFileType.RUBY, code)
      .getFirstChild

    if (reformat) reformatElement(parsedElement) else parsedElement
  }

  private def reformatElement(parsedElement: PsiElement)(implicit project: Project) = {
    val styleManager = CodeStyleManager.getInstance(project)
    styleManager.reformat(
      parsedElement
    )
  }
}
