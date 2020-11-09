package com.refactorings.ruby.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.refactorings.ruby.psi.PsiElementExtensions.LeafPsiElementExtension
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes

object Matchers {
  object EndOfLine {
    def unapply(element: PsiElement): Option[LeafPsiElement] = element match {
      case endOfLine: LeafPsiElement if endOfLine.isOfType(RubyTokenTypes.tEOL) => Some(endOfLine)
      case _ => None
    }
  }
}

