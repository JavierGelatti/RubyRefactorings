package com.refactorings.ruby.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.refactorings.ruby.fun2Runnable
import com.refactorings.ruby.psi.EditorExtension
import org.jetbrains.annotations.TestOnly

trait UI {
  def showErrorHint(textRange: TextRange, editor: Editor, messageText: String): Unit
}

class SwingUI extends UI {
  def showErrorHint(textRange: TextRange, editor: Editor, messageText: String): Unit = {
    ApplicationManager.getApplication.invokeLater({
      editor.scrollTo(textRange.getStartOffset, onScrollingFinished = {
        val timeout = 0
        HintManager.getInstance.showErrorHint(
          editor,
          messageText,
          textRange.getStartOffset,
          textRange.getEndOffset,
          HintManager.ABOVE,
          HintManager.HIDE_BY_ESCAPE | HintManager.HIDE_BY_TEXT_CHANGE,
          timeout
        )
      })
    })
  }
}

object UI extends UI {
  private var currentUiImplementation: UI = new SwingUI

  @TestOnly
  def setImplementation(uiImplementation: UI): Unit = currentUiImplementation = uiImplementation

  override def showErrorHint(textRange: TextRange, editor: Editor, messageText: String): Unit =
    currentUiImplementation.showErrorHint(textRange, editor, messageText)
}