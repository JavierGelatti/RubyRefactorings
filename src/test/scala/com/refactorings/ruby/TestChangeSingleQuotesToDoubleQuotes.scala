package com.refactorings.ruby

import org.junit.Test

class TestChangeSingleQuotesToDoubleQuotes extends RefactoringTestRunningInIde {
  @Test
  def replacesSingleQuotesByDoubleQuotesForEmptyStrings(): Unit = {
    loadRubyFileWith(
      """
        |'<caret>'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |""
      """)
  }

  @Test
  def replacesSingleQuotesByDoubleQuotesForNonEmptyStrings(): Unit = {
    loadRubyFileWith(
      """
        |'<caret>hola'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"hola"
      """)
  }

  @Test
  def escapesHashesInsideStrings(): Unit = {
    loadRubyFileWith(
      """
        |'<caret>#{}##{}'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\#{}\#\#{}"
      """)
  }

  @Test
  def escapesDoubleQuotesInsideStrings(): Unit = {
    loadRubyFileWith(
      """
        |'<caret>""'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\"\""
      """)
  }

  @Test
  def escapesBackslashesInsideStrings(): Unit = {
    loadRubyFileWith(
      """
        |'<caret>\n'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\\n"
      """)
  }

  @Test
  def doesNotEscapeAlreadyEscapedBackslashes(): Unit = {
    loadRubyFileWith(
      """
        |'<caret>\\'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\\"
      """)
  }

  @Test
  def unescapesSingleQuotesInsideString(): Unit = {
    loadRubyFileWith(
      """
        |'<caret>\'\''
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"''"
      """)
  }

  @Test
  def isNotAvailableOutsideOfStrings(): Unit = {
    loadRubyFileWith(
      """
        |<caret> 'x'
      """)

    assertRefactorNotAvailable(ChangeSingleQuotesToDoubleQuotes)
  }

  @Test
  def isNotAvailableForDoubleQuotedStrings(): Unit = {
    loadRubyFileWith(
      """
        |"<caret>x"
      """)

    assertRefactorNotAvailable(ChangeSingleQuotesToDoubleQuotes)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenRemovingEscapeSequences(): Unit = {
    loadRubyFileWith(
      """
        |'\'\'\'<caret>'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"'''<caret>"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenRemovingEscapeSequencesForBackslashes(): Unit = {
    loadRubyFileWith(
      """
        |'\'\\\'\\<caret>'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"'\\'\\<caret>"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenAddingEscapeSequences(): Unit = {
    loadRubyFileWith(
      """
        |'"#<caret>'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\"\#<caret>"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenAddingEscapeSequencesForUnescapedBackslashes(): Unit = {
    loadRubyFileWith(
      """
        |'\a\a<caret>'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\\a\\a<caret>"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenThereIsContentBeforeAndAfterTheCaret(): Unit = {
    loadRubyFileWith(
      """
        |'""<caret>##'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\"\"<caret>\#\#"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenTheCaretIsAtTheBeginningOfTheString(): Unit = {
    loadRubyFileWith(
      """
        |'<caret>##'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"<caret>\#\#"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenTheCaretIsInTheMiddleOfAQuoteEscapeSequence(): Unit = {
    loadRubyFileWith(
      """
        |'\<caret>'x'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"'<caret>x"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenTheCaretIsNotInTheMiddleOfAnEscapeSequenceButIsAfterABackslash(): Unit = {
    loadRubyFileWith(
      """
        |'\<caret>a'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\\<caret>a"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenTheCaretIsInTheMiddleOfABackslashEscapeSequence(): Unit = {
    loadRubyFileWith(
      """
        |'\<caret>\x'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\\<caret>x"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenTheCaretAfterABackslashEscapeSequenceAndBeforeAnEscapedQuote(): Unit = {
    loadRubyFileWith(
      """
        |'\\<caret>\''
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\\<caret>'"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenTheCaretIsInTheMiddleOfAnEscapedQuoteButAfterABackslashEscapeSequence(): Unit = {
    loadRubyFileWith(
      """
        |'\\\<caret>''
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\\'<caret>"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionEvenAfterFormattingHasBeenApplied(): Unit = {
    loadRubyFileWith(
      """
        |
        |'hola<caret>mundo'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"hola<caret>mundo"
      """)
  }

  @Test
  def maintainsTheRelativeCaretPositionWhenTheCaretIsAtTheEndOfTheString(): Unit = {
    loadRubyFileWith(
      """
        |'##<caret>'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |"\#\#<caret>"
      """)
  }

  @Test
  def worksIfTheStringIsNotTheFirstElementInTheProgram(): Unit = {
    loadRubyFileWith(
      """
        |x = 2
        |'##<caret>'
      """)

    applyRefactor(ChangeSingleQuotesToDoubleQuotes)

    expectResultingCodeToBe(
      """
        |x = 2
        |"\#\#<caret>"
      """)
  }
}
