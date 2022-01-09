package com.refactorings.ruby.psi

import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.{PsiElement, PsiWhiteSpace}
import org.jetbrains.plugins.ruby.ruby.lang.lexer.RubyTokenTypes
import org.jetbrains.plugins.ruby.ruby.lang.psi.variables.RPseudoConstant

import scala.PartialFunction.condOpt

object Matchers {
  object Leaf {
    def unapply(element: LeafPsiElement): Some[IElementType] = Some(element.getElementType)
  }

  object EndOfLine {
    def unapply(element: LeafPsiElement): Option[LeafPsiElement] = condOpt(element) {
      case endOfLine@Leaf(RubyTokenTypes.tEOL) => endOfLine
    }
  }

  object Whitespace {
    def unapply(element: PsiWhiteSpace): Some[String] = Some(element.getText)
  }

  object EscapeSequence {
    def unapply(element: LeafPsiElement): Option[LeafPsiElement] = condOpt(element) {
      case escapeSequence@Leaf(RubyTokenTypes.tESCAPE_SEQUENCE) => escapeSequence
    }
  }

  object PseudoConstant {
    def unapply(element: PsiElement): Option[String] = condOpt(element) {
      case pseudoConstant: RPseudoConstant => pseudoConstant.getText
    }
  }
}
