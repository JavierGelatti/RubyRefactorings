package com.refactorings.ruby

import org.junit.{Ignore, Test}

import scala.language.reflectiveCalls

class TestRemoveUnnecessaryHashBraces extends RefactoringTestRunningInIde {
  @Test
  def removesHashBracesFromOneArgumentMessageWithHashArgumentWithOneKey(): Unit = {
    loadFileWith(
      """
        |m1(<caret>{ a: 1 })
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
        |m1(a: 1)
      """)
  }

  @Test
  def removesHashBracesFromOneArgumentMessageWithHashArgumentWithManyKeys(): Unit = {
    loadFileWith(
      """
        |m1(<caret>{ a: 1, b: 2 })
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
        |m1(a: 1, b: 2)
      """)
  }

  @Test
  def removesHashBracesFromMultiArgumentMessageWithHashAsLastArgument(): Unit = {
    loadFileWith(
      """
        |m1(lala, <caret>{ a: 1, b: 2 })
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
       |m1(lala, a: 1, b: 2)
      """)
  }

  @Test
  def onlyRemovesBracesFromLastArgumentHash(): Unit = {
    loadFileWith(
      """
        |m1({ x: 7 }, <caret>{ a: 1, b: 2 })
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
       |m1({ x: 7 }, a: 1, b: 2)
      """)
  }

  @Test
  def removesBracesFromLastArgumentHashEvenIfThereIsABlockArgumentAfterwards(): Unit = {
    loadFileWith(
      """
        |m1(lala, <caret>{ a: 1, b: 2 }, &block)
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
       |m1(lala, a: 1, b: 2, &block)
      """)
  }

  @Test
  def worksIfThereAreNoParenthesesInTheMethodDefinition(): Unit = {
    loadFileWith(
      """
        |m1 lala, <caret>{ a: 1, b: 2 }
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
       |m1 lala, a: 1, b: 2
      """)
  }

  @Test
  def deconstructsHashToArgumentsExpressionsWhenTheCaretIsAfterTheOperator(): Unit = {
    loadFileWith(
      """
        |m1(**<caret>{ a: 1, b: 2 })
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
       |m1(a: 1, b: 2)
      """)
  }

  @Test
  def deconstructsHashToArgumentsExpressionsWhenTheCaretIsInsideTheHash(): Unit = {
    loadFileWith(
      """
        |m1(**{<caret> a: 1, b: 2 })
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
       |m1(a: 1, b: 2)
      """)
  }

  @Test
  def itIsNotAvailableIfTheCursorIsNotInsideAMessageSend(): Unit = {
    loadFileWith(
      """
        |m1(1, {a: 2})
        |<caret>
      """)

    assertRefactorNotAvailable(RemoveUnnecessaryHashBraces)
  }

  @Test
  def itIsNotAvailableIfTheMessageHasNoArguments(): Unit = {
    loadFileWith(
      """
        |m1(<caret>)
      """)

    assertRefactorNotAvailable(RemoveUnnecessaryHashBraces)
  }

  @Test
  def itIsNotAvailableIfTheLastArgumentIsNotAHash(): Unit = {
    loadFileWith(
      """
        |m1(1, <caret>2)
      """)

    assertRefactorNotAvailable(RemoveUnnecessaryHashBraces)
  }

  @Test
  def itIsNotAvailableIfTheLastArgumentIsAnEmptyHash(): Unit = {
    loadFileWith(
      """
        |m1({<caret>})
      """)

    assertRefactorNotAvailable(RemoveUnnecessaryHashBraces)
  }

  @Test
  def itIsNotAvailableForBlocks(): Unit = {
    loadFileWith(
      """
        |m1 <caret>{}
      """)

    assertRefactorNotAvailable(RemoveUnnecessaryHashBraces)
  }

  @Test
  def itIsNotAvailableIfTheCaretIsNotInsideTheLastArgument(): Unit = {
    loadFileWith(
      """
        |m1(<caret>12, { a: 1 })
      """)

    assertRefactorNotAvailable(RemoveUnnecessaryHashBraces)
  }

  @Test
  def itIsNotAvailableIfTheCaretIsInsideTheLastArgumentButInsideASubElement(): Unit = {
    loadFileWith(
      """
        |m1(12, { a: { b: <caret>2 } })
      """)

    assertRefactorNotAvailable(RemoveUnnecessaryHashBraces)
  }

  @Test
  def worksForMultilineHashes(): Unit = {
    loadFileWith(
      """
        |m1(<caret>{
        |  a: 1,
        |  b: 2
        |})
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
        |m1(a: 1,
        |   b: 2)
      """)
  }
}
