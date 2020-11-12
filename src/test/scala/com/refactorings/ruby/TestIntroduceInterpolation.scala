package com.refactorings.ruby

import org.junit.Test

import scala.language.reflectiveCalls

class TestIntroduceInterpolation extends RefactoringTestRunningInIde {
  @Test
  def introducesEmptyInterpolationAtCaretPositionIfNothingIsSelected(): Unit = {
    loadRubyFileWith(
      """
        |"hola<caret>mundo"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{<caret>}mundo"
      """)
  }

  @Test
  def introducesEmptyInterpolationAtCaretPositionIfThereIsNoPrefix(): Unit = {
    loadRubyFileWith(
      """
        |"<caret>mundo"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{<caret>}mundo"
      """)
  }

  @Test
  def introducesEmptyInterpolationAtCaretPositionIfThereIsNoSuffix(): Unit = {
    loadRubyFileWith(
      """
        |"hola<caret>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{<caret>}"
      """)
  }

  @Test
  def introducesEmptyInterpolationSelectingTheContentsCorrectlyWhenTheExpressionIsNotInTheFirstLine(): Unit = {
    loadRubyFileWith(
      """
        |m1()
        |"hola<caret>mundo"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |m1()
        |"hola#{<caret>}mundo"
      """)
  }

  @Test
  def introducesEmptyInterpolationMaintainingCurrentInterpolations(): Unit = {
    loadRubyFileWith(
      """
        |"a#{1}b<caret>c#{2}d"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"a#{1}b#{<caret>}c#{2}d"
      """)
  }

  @Test
  def worksForEmptyStrings(): Unit = {
    loadRubyFileWith(
      """
        |"<caret>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{<caret>}"
      """)
  }

  @Test
  def isNotAvailableIfTheCaretIsNotInsideAString(): Unit = {
    loadRubyFileWith(
      """
        |1 + 2<caret>
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheCaretIsNotDirectlyInsideAString(): Unit = {
    loadRubyFileWith(
      """
        |"#{<caret>}"
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheCaretIsJustAtTheBeginningOfAStringButNotInside(): Unit = {
    loadRubyFileWith(
      """
        |<caret>""
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheCaretIsJustAtTheEndOfAStringButNotInside(): Unit = {
    loadRubyFileWith(
      """
        |""<caret>
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheStringIsSingleQuoted(): Unit = {
    loadRubyFileWith(
      """
        |'con<caret>tent'
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheStringIsAConsoleCommand(): Unit = {
    loadRubyFileWith(
      """
        |`ec<caret>ho`
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def isNotAvailableIfTheStringIsAHeredoc(): Unit = {
    loadRubyFileWith(
      """
        |result = <<~TXT
        |  some text<caret>
        |TXT
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def introducesInterpolationIfSomeTextIsSelected(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>mundo</selection><caret>!"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"<caret>mundo"}!"
      """)
  }

  @Test
  def introducesInterpolationIfSomeTextIsSelectedWithNoPrefix(): Unit = {
    loadRubyFileWith(
      """
        |"<selection>mundo</selection><caret>!"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{"<caret>mundo"}!"
      """)
  }

  @Test
  def introducesInterpolationIfSomeTextIsSelectedWithNoSuffix(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>mundo</selection><caret>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"<caret>mundo"}"
      """)
  }

  @Test
  def introducesInterpolationIfAllTextIsSelected(): Unit = {
    loadRubyFileWith(
      """
        |"<selection>mundo</selection><caret>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{"<caret>mundo"}"
      """)
  }

  @Test
  def placesCaretCorrectlyWhenBeginsAsStartOfSelection(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection><caret>mundo</selection>!"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"<caret>mundo"}!"
      """)
  }

  @Test
  def introducesInterpolationEvenIfOtherInterpolationsAreSelectedInTheMiddle(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>m#{"und"}o</selection><caret>!"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"<caret>m#{"und"}o"}!"
      """)
  }

  @Test
  def introducesInterpolationEvenIfInterpolationsThatSpanTheWholeStringAreSelected(): Unit = {
    loadRubyFileWith(
      """
        |"<selection>#{"und"}#{"oder"}</selection><caret>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{"<caret>#{"und"}#{"oder"}"}"
      """)
  }

  @Test
  def isNotAvailableIfTheSelectionSpansMultipleStrings(): Unit = {
    loadRubyFileWith(
      """
        |"ho<selection>la" + "mu<caret></selection>ndo"
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def fixesSmallSelectionErrorsByExtendingTheSelectionAtStart(): Unit = {
    loadRubyFileWith(
      """
        |"#<selection>{"hola"}mun</selection>do"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{"<caret>#{"hola"}mun"}do"
      """)
  }

  @Test
  def fixesSmallSelectionErrorsByExtendingTheSelectionAtEnd(): Unit = {
    loadRubyFileWith(
      """
        |"ho<selection>la#{"mundo"</selection>}"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"ho#{"<caret>la#{"mundo"}"}"
      """)
  }

  @Test
  def fixesSmallSelectionErrorsByExtendingTheSelectionAtStartAndEnd(): Unit = {
    loadRubyFileWith(
      """
        |"#<selection>{"hola"}#{"mundo"</selection>}"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{"<caret>#{"hola"}#{"mundo"}"}"
      """)
  }

  @Test
  def itIsNotAvailableIfTheSelectionStartIncludesTheEndOfAnInterpolation(): Unit = {
    loadRubyFileWith(
      """
        |"#{"hola"<selection>}mundo</selection>"
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }

  @Test
  def itIsNotAvailableIfTheSelectionEndIncludesTheStartOfAnInterpolation(): Unit = {
    loadRubyFileWith(
      """
        |"<selection>hola#{</selection>"mundo"}"
      """)

    assertRefactorNotAvailable(IntroduceInterpolation)
  }
}
