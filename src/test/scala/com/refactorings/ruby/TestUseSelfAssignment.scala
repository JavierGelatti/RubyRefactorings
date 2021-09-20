package com.refactorings.ruby

import org.junit.{Ignore, Test}

@Ignore
class TestUseSelfAssignment extends RefactoringTestRunningInIde {
  @Test
  def replacesAssignmentOfBinaryMessageSendWithSelfAssignment(): Unit = {
    loadRubyFileWith(
      """
        |a =<caret> a + 1
      """)

    applyRefactor(UseSelfAssignment)

    expectResultingCodeToBe(
      """
        |a += 1
      """)
  }

  @Test
  def replacesAssignmentOfBinaryOperatorWithSelfAssignment(): Unit = {
    loadRubyFileWith(
      """
        |a =<caret> a || 1
      """)

    applyRefactor(UseSelfAssignment)

    expectResultingCodeToBe(
      """
        |a ||= 1
      """)
  }

  @Test
  def isNotAvailableIfTheCaretIsAlreadyInsideOfASelfAssignment(): Unit = {
    loadRubyFileWith(
      """
        |a +=<caret> a + 1
      """)

    assertRefactorNotAvailable(UseSelfAssignment)
  }

  @Test
  def isNotAvailableIfTheCaretIsInsideOfAMultiAssignment(): Unit = {
    loadRubyFileWith(
      """
        |a, b =<caret> a + 1
      """)

    assertRefactorNotAvailable(UseSelfAssignment)
  }

  @Test
  def isNotAvailableIfTheValueOfTheAssignmentValueIsNotTheResultOfABinaryOperation(): Unit = {
    loadRubyFileWith(
      """
        |a =<caret> a.plus(1)
      """)

    assertRefactorNotAvailable(UseSelfAssignment)
  }

  @Test
  def isNotAvailableIfTheLeftHandSideOfTheBinaryExpressionIsNotTheSameAsTheAssignmentTarget(): Unit = {
    loadRubyFileWith(
      """
        |a = 1 + a
      """)

    assertRefactorNotAvailable(UseSelfAssignment)
  }
}
