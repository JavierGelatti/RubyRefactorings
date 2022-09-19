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

  @Test
  def preservesCommentsBeforeFirstOperand(): Unit = {
    loadRubyFileWith(
      """
        |a =<caret> # comment 1
        |  # comment 2
        |  a + 1
      """)

    applyRefactor(UseSelfAssignment)

    expectResultingCodeToBe(
      """
        |a += # comment 1
        |  # comment 2
        |  1
      """)
  }

  @Test
  def preservesCommentsBeforeSecondOperand(): Unit = {
    loadRubyFileWith(
      """
        |a =<caret> a + # comment 1
        |  # comment 2
        |  1
      """)

    applyRefactor(UseSelfAssignment)

    expectResultingCodeToBe(
      """
        |a += # comment 1
        |  # comment 2
        |  1
      """)
  }

  @Test
  def preservesCommentsAfterSecondOperand(): Unit = {
    loadRubyFileWith(
      """
        |a =<caret> a + 1 # comment 1
        |  # comment 2
      """)

    applyRefactor(UseSelfAssignment)

    expectResultingCodeToBe(
      """
        |a += 1 # comment 1
        |  # comment 2
      """)
  }

  @Test
  def preservesTheOrderOfComments(): Unit = {
    loadRubyFileWith(
      """
        |a =<caret> # comment 1
        |  # comment 2
        |  a + # comment 3
        |  # comment 4
        |  1 # comment 5
      """)

    applyRefactor(UseSelfAssignment)

    expectResultingCodeToBe(
      """
        |a += # comment 1
        |  # comment 2
        |  # comment 3
        |  # comment 4
        |  1 # comment 5
      """)
  }
}
