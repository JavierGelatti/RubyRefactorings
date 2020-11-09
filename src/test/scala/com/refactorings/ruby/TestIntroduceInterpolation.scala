package com.refactorings.ruby

import org.junit.Test

import scala.language.reflectiveCalls

class TestIntroduceInterpolation extends RefactoringTestRunningInIde {
  @Test
  def introducesEmptyInterpolationAtCaretPositionIfNothingIsSelected(): Unit = {
    loadFileWith(
      """
        |"hola<caret>mundo"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"<caret>"}mundo"
      """)
  }

  @Test
  def introducesEmptyInterpolationAtCaretPositionIfThereIsNoPrefix(): Unit = {
    loadFileWith(
      """
        |"<caret>mundo"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{"<caret>"}mundo"
      """)
  }

  @Test
  def introducesEmptyInterpolationAtCaretPositionIfThereIsNoSuffix(): Unit = {
    loadFileWith(
      """
        |"hola<caret>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"<caret>"}"
      """)
  }

  @Test
  def introducesEmptyInterpolationSelectingTheContentsCorrectlyWhenTheExpressionIsNotInTheFirstLine(): Unit = {
    loadFileWith(
      """
        |m1()
        |"hola<caret>mundo"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |m1()
        |"hola#{"<caret>"}mundo"
      """)
  }

  @Test
  def introducesEmptyInterpolationMaintainingCurrentInterpolations(): Unit = {
    loadFileWith(
      """
        |"a#{1}b<caret>c#{2}d"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"a#{1}b#{"<caret>"}c#{2}d"
      """)
  }

  @Test
  def worksForEmptyStrings(): Unit = {
    loadFileWith(
      """
        |"<caret>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{"<caret>"}"
      """)
  }

  @Test
  def isNotAvailableIfTheCaretIsNotInsideAString(): Unit = {
    loadFileWith(
      """
        |1 + 2<caret>
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheCaretIsNotDirectlyInsideAString(): Unit = {
    loadFileWith(
      """
        |"#{<caret>}"
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheCaretIsJustAtTheBeginningOfAStringButNotInside(): Unit = {
    loadFileWith(
      """
        |<caret>""
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheCaretIsJustAtTheEndOfAStringButNotInside(): Unit = {
    loadFileWith(
      """
        |""<caret>
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheStringIsSingleQuoted(): Unit = {
    loadFileWith(
      """
        |'con<caret>tent'
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheStringIsAConsoleCommand(): Unit = {
    loadFileWith(
      """
        |`ec<caret>ho`
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheStringIsAHeredoc(): Unit = {
    loadFileWith(
      """
        |result = <<~TXT
        |  some text<caret>
        |TXT
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }
}
