package com.refactorings.ruby

import org.junit.{Ignore, Test}

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

  @Test
  def introducesInterpolationAtTheStartOfTheStringEndingOnAnExistingInterpolation(): Unit = {
    loadRubyFileWith(
      """
        |"<selection>hola</selection>#{"mundo"}"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{"<caret>hola"}#{"mundo"}"
      """)
  }

  @Test
  def introducesInterpolationWithoutSelectionAtTheStartOfTheStringEndingOnAnExistingInterpolation(): Unit = {
    loadRubyFileWith(
      """
        |"<caret>#{"mundo"}"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{<caret>}#{"mundo"}"
      """)
  }

  @Test
  def includesEscapeSequenceIfSelectionStartIsInTheMiddleOfIt(): Unit = {
    loadRubyFileWith(
      """
        |"hola\<selection>nmundo</selection>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"\nmundo"}"
      """)
  }

  @Test
  def includesEscapeSequenceIfItIsJustAfterSelectionStart(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>\nmundo</selection>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"\nmundo"}"
      """)
  }

  @Test
  def doesNotIncludeEscapeSequenceIfItIsJustBeforeSelectionStart(): Unit = {
    loadRubyFileWith(
      """
        |"hola\n<selection>mundo</selection>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola\n#{"mundo"}"
      """)
  }

  @Test
  def doesNotIncludeEscapeSequenceIfSelectionEndIsInTheMiddleOfIt(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>mundo\</selection>n"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"mundo"}\n"
      """)
  }

  @Test
  def includesEscapeSequenceIfItIsJustBeforeSelectionEnd(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>mundo\n</selection>"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"mundo\n"}"
      """)
  }

  @Test
  def doesNotIncludeEscapeSequenceIfItIsJustAfterSelectionEnd(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>mundo</selection>\n"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{"mundo"}\n"
      """)
  }

  @Test
  def shiftsCaretBeforeEscapeSequenceIfCaretWasInsideOneWhenPerformingTheRefactoring(): Unit = {
    loadRubyFileWith(
      """
        |"hola\<caret>nmundo"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"hola#{}\nmundo"
      """)
  }

  @Test
  @Ignore("For some reason, the start of this Ruby String is wrongly parsed as 'PsiElement(invalid escape sequence)'")
  def shiftsCaretInsideLongEscapeSequence(): Unit = {
    // Unicode escape sequences are used inside triple-quoted strings in Scala, and that prevents us from
    // literally writing "/u" (see https://github.com/scala/bug/issues/4706).
    // As a workaround, we encoded the \u itself in Unicode, and use that instead:
    // \u005c\u0075 == \u
    loadRubyFileWith(
      """
        |"\u005c\u007527<caret>28"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |"#{}\u005c\u00752728"
      """)
  }
}
