package com.refactorings.ruby

import org.junit.Test

class TestRemoveUselessConditionalStatement extends RefactoringTestRunningInIde {
  @Test
  def removesAnIfStatementWithAFalsePredicate(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> false
        |  42
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |
      """)
  }

  @Test
  def cannotBeAppliedOnAnIfStatementWithAnUnknownValuedPredicate(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> unknown_value
        |  42
        |end
      """)

    assertRefactorNotAvailable(RemoveUselessConditionalStatement)
  }

  @Test
  def removesAnIfStatementWithANilPredicate(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> nil
        |  42
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |
      """)
  }

  @Test
  def replacesAnIfStatementWithATruePredicateWithItsBody(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> true
        |  41
        |  42
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |41
        |42
      """)
  }

  @Test
  def preservesIndentationOfThenBlock(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> true
        |  if condition
        |    42
        |  end
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |if condition
        |  42
        |end
      """)
  }

  @Test
  def preservesIndentationOfElseBlock(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> false
        |else
        |  if condition
        |    42
        |  end
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |if condition
        |  42
        |end
      """)
  }

  @Test
  def replacesAnIfStatementWithAFalsePredicateAndAnElseBlockWithTheElseBody(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> false
        |  7
        |else
        |  41
        |  42
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |41
        |42
      """)
  }

  @Test
  def replacesAnIfStatementWithAFalsePredicateAndAnElsifBlockWithAnIfEquivalentToTheAlternativePath(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> false
        |  7
        |elsif condition
        |  42
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |if condition
        |  42
        |end
      """)
  }

  @Test
  def replacesAnIfStatementWithAFalsePredicateAndAnElsifAndElseBlockWithAnIfEquivalentToTheAlternativePath(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> false
        |  7
        |elsif condition
        |  42
        |else
        |  123
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |if condition
        |  42
        |else
        |  123
        |end
      """)
  }

  @Test
  def replacesAnUnlessStatementWithAFalsePredicateWithItsBody(): Unit = {
    loadRubyFileWith(
      """
        |unless<caret> false
        |  42
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |42
      """)
  }

  @Test
  def canBeInvokedWhenTheCaretIsInsideTheCondition(): Unit = {
    loadRubyFileWith(
      """
        |if fal<caret>se
        |  42
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |
      """)
  }
}
