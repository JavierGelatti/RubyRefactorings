package com.refactorings.ruby

import org.junit.Test

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

  @Test
  def replacesAssignmentOfHashAccessWithSelfAssignment(): Unit = {
    loadRubyFileWith(
      """
        |h[:key] = h[:key] + 1
      """)

    applyRefactor(UseSelfAssignment)

    expectResultingCodeToBe(
      """
        |h[:key] += 1
      """)
  }

  @Test
  def canBeAppliedRegardlessOfWhitespaceDifferences(): Unit = {
    loadRubyFileWith(
      """
        |h[:key ] = h[ :key] + 1
      """)

    applyRefactor(UseSelfAssignment)

    expectResultingCodeToBe(
      """
        |h[:key ] += 1
      """)
  }
}
