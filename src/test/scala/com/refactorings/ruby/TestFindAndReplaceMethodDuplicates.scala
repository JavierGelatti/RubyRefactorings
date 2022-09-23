package com.refactorings.ruby

import org.junit.Test

class TestFindAndReplaceMethodDuplicates extends RefactoringTestRunningInIde {
  @Test
  def replacesTopLevelOccurrencesOfMethodsWithoutParametersThatOnlyContainLiterals(): Unit = {
    loadRubyFileWith(
      """
        |def m1<caret>
        |  "hola" + "mundo"
        |end
        |
        |"hola" + "mundo"
      """)

    applyRefactor(FindAndReplaceMethodDuplicates)

    expectResultingCodeToBe(
      """
        |def m1
        |  "hola" + "mundo"
        |end
        |
        |m1
      """)
  }

  @Test
  def replacesMoreThanOneTopLevelOccurrence(): Unit = {
    loadRubyFileWith(
      """
        |def m1<caret>
        |  123
        |end
        |
        |123
        |123
      """)

    applyRefactor(FindAndReplaceMethodDuplicates)

    expectResultingCodeToBe(
      """
        |def m1
        |  123
        |end
        |
        |m1
        |m1
      """)
  }

  @Test
  def isNotAvailableIfTheCaretIsOutsideAMethod(): Unit = {
    loadRubyFileWith(
      """
        |<caret>
        |def m1
        |  123
        |end
      """)

    assertRefactorNotAvailable(FindAndReplaceMethodDuplicates)
  }

  @Test
  def isNotAvailableIfTheCaretIsInsideTheMethodBody(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  <caret>
        |  123
        |end
      """)

    assertRefactorNotAvailable(FindAndReplaceMethodDuplicates)
  }
}
