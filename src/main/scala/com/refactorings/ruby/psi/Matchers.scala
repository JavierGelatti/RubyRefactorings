package com.refactorings.ruby.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.refactorings.ruby.psi.PsiElementExtensions.LeafPsiElementExtension
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes

import scala.PartialFunction.condOpt

object Matchers {
  object EndOfLine {
    def unapply(element: PsiElement): Option[LeafPsiElement] = condOpt(element) {
      case endOfLine: LeafPsiElement if endOfLine.isOfType(RubyTokenTypes.tEOL) => endOfLine
    }
  }
}
