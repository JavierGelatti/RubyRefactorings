package com.refactorings.ruby.ui

import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.popup.{JBPopupFactory, JBPopupListener, LightweightWindowEvent}
import com.intellij.openapi.util.{Pair, TextRange}
import com.refactorings.ruby._
import com.refactorings.ruby.psi.EditorExtension
import org.jetbrains.annotations.{Nls, TestOnly}

import java.util.Collections
import javax.swing.{DefaultListCellRenderer, ListSelectionModel}
import scala.jdk.CollectionConverters._

trait UI {
  def showErrorHint(textRange: TextRange, editor: Editor, messageText: String): Unit

  def showOptionsMenuWith[ConcreteOption <: SelectionOption]
  (title: String, options: Seq[ConcreteOption], editor: Editor, callback: ConcreteOption => Unit): Unit
}

trait SelectionOption {
  val textRange: TextRange

  val optionText: String

  override lazy val toString: String =  {
    var numberOfCharacters = 0
    optionText
      .takeWhile { nextChar =>
        numberOfCharacters += 1
        numberOfCharacters <= 100 && nextChar != '\n'
      }
  }
}

class SwingUI extends UI {
  def showErrorHint(textRange: TextRange, editor: Editor, @Nls messageText: String): Unit = {
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

  override def showOptionsMenuWith[ConcreteOption <: SelectionOption]
  (@Nls title: String, options: Seq[ConcreteOption], editor: Editor, callback: ConcreteOption => Unit): Unit = {
    if (options.isEmpty) return
    if (options.size == 1) return callback(options.head)

    val popup = {
      val highlighter = new ScopeHighlighter(editor)
      JBPopupFactory.getInstance()
        .createPopupChooserBuilder(options.asJava)
        .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        .setSelectedValue(options.head, true)
        .setAccessibleName(title)
        .setTitle(title)
        .setMovable(true)
        .setResizable(true)
        .setRequestFocus(true)
        .setItemSelectedCallback(highlighter.highlight(_))
        .setItemChosenCallback(callback(_))
        .addListener(onClose { highlighter.dropHighlight() })
        .setRenderer(new DefaultListCellRenderer())
        .createPopup
    }

    popup.showInBestPositionFor(editor)
  }

  private def onClose(callback: => Unit) = {
    new JBPopupListener() {
      override def onClosed(event: LightweightWindowEvent): Unit = callback
    }
  }

  private implicit class ScopeHighlighterExtension(highlighter: ScopeHighlighter) {
    def highlight(range: TextRange): Unit = {
      highlighter.highlight(
        Pair.create(range, Collections.singletonList(range))
      )
    }

    def highlight(selectionOption: SelectionOption): Unit = {
      highlighter.dropHighlight()
      if (selectionOption != null) highlighter.highlight(selectionOption.textRange)
    }
  }
}

object UI extends UI {
  private var currentUiImplementation: UI = new SwingUI

  @TestOnly
  def setImplementation(uiImplementation: UI): Unit = currentUiImplementation = uiImplementation

  override def showErrorHint(textRange: TextRange, editor: Editor, messageText: String): Unit =
    currentUiImplementation.showErrorHint(textRange, editor, messageText)

  override def showOptionsMenuWith[ConcreteOption <: SelectionOption]
  (title: String, options: Seq[ConcreteOption], editor: Editor, callback: ConcreteOption => Unit): Unit =
    currentUiImplementation.showOptionsMenuWith(title, options, editor, callback)
}
