package com.refactorings.ruby.psi

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes

import scala.PartialFunction.condOpt

object Matchers {
  object Leaf {
    def unapply(element: LeafPsiElement): Option[IElementType] = Some(element.getElementType)
  }

  object EndOfLine {
    def unapply(element: LeafPsiElement): Option[LeafPsiElement] = condOpt(element) {
      case endOfLine@Leaf(RubyTokenTypes.tEOL) => endOfLine
    }
  }

  object EscapeSequence {
    def unapply(element: LeafPsiElement): Option[LeafPsiElement] = condOpt(element) {
      case escapeSequence@Leaf(RubyTokenTypes.tESCAPE_SEQUENCE) => escapeSequence
    }
  }
}
