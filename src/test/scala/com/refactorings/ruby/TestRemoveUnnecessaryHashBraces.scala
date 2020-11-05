package com.refactorings.ruby

import org.junit.{Ignore, Test}

import scala.language.reflectiveCalls

class TestRemoveUnnecessaryHashBraces extends RefactoringTestRunningInIde {
  @Test
  def removesHashBracesFromOneArgumentMessageWithHashArgument(): Unit = {
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
  def canRemoveEmptyHashes(): Unit = {
    loadFileWith(
      """
        |m1({ x: 7 }, <caret>{})
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
       |m1({ x: 7 })
      """)
  }

  @Test
  def canRemoveEmptyHashesWhenTheyAreTheOnlyArgument(): Unit = {
    loadFileWith(
      """
        |m1(<caret>{})
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
       |m1()
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
  def deconstructsHashToArgumentsExpressions(): Unit = {
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
  @Ignore
  def itIsNotAvailableForBlocks(): Unit = {
    loadFileWith(
      """
        |m1 <caret>{}
      """)

    assertRefactorNotAvailable(RemoveUnnecessaryHashBraces)
  }
}
