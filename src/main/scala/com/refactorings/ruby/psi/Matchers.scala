package com.refactorings.ruby.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes

object Matchers {
  object EndOfLine {
    def unapply(element: PsiElement): Option[LeafPsiElement] = element match {
      case endOfLine: LeafPsiElement if endOfLine.getElementType == RubyTokenTypes.tEOL => Some(endOfLine)
      case _ => None
    }
  }
}

