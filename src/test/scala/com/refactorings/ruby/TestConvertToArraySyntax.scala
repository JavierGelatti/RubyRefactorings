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
  def replacesSingleQuoteWordListSeparatedByMultipleSpacesByArraySyntax(): Unit = {
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
  def isNotAvailableForOtherLiterals(): Unit = {
    loadRubyFileWith(
      """
        |%W<caret>(hola mundo)
      """)

    assertRefactorNotAvailable(ConvertToArraySyntax)
  }
}
