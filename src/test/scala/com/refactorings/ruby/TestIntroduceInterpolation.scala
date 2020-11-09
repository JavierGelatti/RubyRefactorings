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
        |"hola#{<selection>""<caret></selection>}mundo"
      """)
  }

  @Test
  def as(): Unit = {
    loadFileWith(
      """
        |m1()
        |"hola<caret>mundo"
      """)

    applyRefactor(IntroduceInterpolation)

    expectResultingCodeToBe(
      """
        |m1()
        |"hola#{<selection>""<caret></selection>}mundo"
      """)
  }
}
