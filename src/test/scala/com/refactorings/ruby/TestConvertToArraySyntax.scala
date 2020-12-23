package com.refactorings.ruby

import org.junit.Test

class TestConvertToArraySyntax extends RefactoringTestRunningInIde {
  @Test
  def replacesSingleQuoteWordListByArraySyntax(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>(hola mundo)
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |['hola', 'mundo']
      """)
  }

  @Test
  def replacesSingleQuoteWordListWithNoWordsByArraySyntax(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>()
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |[]
      """)
  }

  @Test
  def replacesSingleQuoteWordListWithNoWordsButSpacesByArraySyntax(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>(      )
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |[]
      """)
  }

  @Test
  def replacesSingleQuoteWordListWithOneWordSeparatedByMultipleSpacesByArraySyntax(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>(   hola   )
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |['hola']
      """)
  }

  @Test
  def replacesSingleQuoteWordListWithManyWordsSeparatedByMultipleSpacesByArraySyntax(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>(   hola    mundo    !   )
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |['hola', 'mundo', '!']
      """)
  }

  @Test
  def replacesSingleQuoteWordListContainingScapedDelimiterByArraySyntax(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>(\)\) \))
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |['))', ')']
      """)
  }

  @Test
  def maintainsNotEscapedBacklashInSingleQuoteWordList(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>(\a)
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |['\\a']
      """)
  }

  @Test
  def escapesSingleQuotesInSingleQuoteWordList(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>('' ')
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |['\'\'', '\'']
      """)
  }

  @Test
  def escapesBackslashInSingleQuoteWordList(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>(\\)
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |['\\']
      """)
  }

  @Test
  def unescapesEscapedSpaces(): Unit = {
    loadRubyFileWith(
      """
        |%w<caret>(\  \ \ )
      """)

    applyRefactor(ConvertToArraySyntax)

    expectResultingCodeToBe(
      """
        |[' ', '  ']
      """)
  }

  @Test
  def isNotAvailableForOtherLiterals(): Unit = {
    loadRubyFileWith(
      """
        |%W<caret>(hola mundo)
      """)

    assertRefactorNotAvailable(ConvertToArraySyntax)
  }
}
