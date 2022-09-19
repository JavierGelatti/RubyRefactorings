package com.refactorings.ruby

import org.jetbrains.plugins.ruby.ruby.sdk.LanguageLevel
import org.junit.{Before, Test}

class TestRemoveBracesFromLastHashArgument extends RefactoringTestRunningInIde {
  @Before
  def setLanguageVersion(): Unit = {
    setLanguageLevel(LanguageLevel.RUBY27)
  }

  @Test
  def removesHashBracesFromOneArgumentMessageWithHashArgumentWithOneKey(): Unit = {
    loadRubyFileWith(
      """
        |m1(<caret>{ a: 1 })
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
        |m1(a: 1)
      """)
  }

  @Test
  def removesHashBracesFromOneArgumentMessageWithHashArgumentWithManyKeys(): Unit = {
    loadRubyFileWith(
      """
        |m1(<caret>{ a: 1, b: 2 })
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
        |m1(a: 1, b: 2)
      """)
  }

  @Test
  def removesHashBracesFromMultiArgumentMessageWithHashAsLastArgument(): Unit = {
    loadRubyFileWith(
      """
        |m1(lala, <caret>{ a: 1, b: 2 })
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
       |m1(lala, a: 1, b: 2)
      """)
  }

  @Test
  def onlyRemovesBracesFromLastArgumentHash(): Unit = {
    loadRubyFileWith(
      """
        |m1({ x: 7 }, <caret>{ a: 1, b: 2 })
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
       |m1({ x: 7 }, a: 1, b: 2)
      """)
  }

  @Test
  def removesBracesFromLastArgumentHashEvenIfThereIsABlockArgumentAfterwards(): Unit = {
    loadRubyFileWith(
      """
        |m1(lala, <caret>{ a: 1, b: 2 }, &block)
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
       |m1(lala, a: 1, b: 2, &block)
      """)
  }

  @Test
  def worksIfThereAreNoParenthesesInTheMethodDefinition(): Unit = {
    loadRubyFileWith(
      """
        |m1 lala, <caret>{ a: 1, b: 2 }
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
       |m1 lala, a: 1, b: 2
      """)
  }

  @Test
  def deconstructsHashToArgumentsExpressionsWhenTheCaretIsAfterTheOperator(): Unit = {
    loadRubyFileWith(
      """
        |m1(**<caret>{ a: 1, b: 2 })
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
       |m1(a: 1, b: 2)
      """)
  }

  @Test
  def deconstructsHashToArgumentsExpressionsWhenTheCaretIsInsideTheHash(): Unit = {
    loadRubyFileWith(
      """
        |m1(**{<caret> a: 1, b: 2 })
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
       |m1(a: 1, b: 2)
      """)
  }

  @Test
  def removesHashBracesWhenTheCaretIsInsideTheHash(): Unit = {
    loadRubyFileWith(
      """
        |m1({<caret> a: 1, b: 2 })
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
       |m1(a: 1, b: 2)
      """)
  }

  @Test
  def removesHashBracesWhenTheCaretIsInsideTheHashAndThereAreNoSpacesBetweenTheCaretAndTheFirstKey(): Unit = {
    loadRubyFileWith(
      """
        |m1({<caret>a: 1, b: 2})
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
       |m1(a: 1, b: 2)
      """)
  }

  @Test
  def itIsNotAvailableForHashToArgumentExpressionsThatDoNotContainALiteralHash(): Unit = {
    loadRubyFileWith(
      """
        |m1(<caret>**a_hash)
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def itIsNotAvailableIfTheCursorIsNotInsideAMessageSend(): Unit = {
    loadRubyFileWith(
      """
        |m1(1, {a: 2})
        |<caret>
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def itIsNotAvailableIfTheMessageHasNoArguments(): Unit = {
    loadRubyFileWith(
      """
        |m1(<caret>)
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def itIsNotAvailableIfTheLastArgumentIsNotAHash(): Unit = {
    loadRubyFileWith(
      """
        |m1(1, <caret>2)
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def itIsNotAvailableIfTheLastArgumentIsAnEmptyHash(): Unit = {
    loadRubyFileWith(
      """
        |m1({<caret>})
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def itIsNotAvailableForBlocks(): Unit = {
    loadRubyFileWith(
      """
        |m1 <caret>{}
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def itIsNotAvailableIfTheCaretIsNotInsideTheLastArgument(): Unit = {
    loadRubyFileWith(
      """
        |m1(<caret>12, { a: 1 })
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def itIsNotAvailableIfTheCaretIsInsideTheLastArgumentButInsideASubElement(): Unit = {
    loadRubyFileWith(
      """
        |m1(12, { a: { b: <caret>2 } })
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def worksForMultilineHashes(): Unit = {
    loadRubyFileWith(
      """
        |m1(<caret>{
        |  a: 1,
        |  b: 2
        |})
      """)

    applyRefactor(RemoveBracesFromLastHashArgument)

    expectResultingCodeToBe(
      """
        |m1(a: 1,
        |   b: 2)
      """)
  }

  @Test
  def itIsNotAvailableForMessageSendsPassingOnlyABlock(): Unit = {
    loadRubyFileWith(
      """
        |m1(<caret>&block)
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }

  @Test
  def itIsNotAvailableForRuby3(): Unit = {
    setLanguageLevel(LanguageLevel.RUBY30)
    loadRubyFileWith(
      """
        |m1(<caret>{ a: 1 })
      """)

    assertRefactorNotAvailable(RemoveBracesFromLastHashArgument)
  }
}
