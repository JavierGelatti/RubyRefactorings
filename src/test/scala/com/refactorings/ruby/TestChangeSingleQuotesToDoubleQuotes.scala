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
}
