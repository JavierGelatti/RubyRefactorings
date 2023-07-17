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
  def replacesAnIfStatementWithANumberLiteralPredicateWithItsBody(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> 1
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
  def replacesAnIfStatementWithASymbolLiteralPredicateWithItsBody(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> :true
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
  def replacesAnIfStatementWithAStringLiteralPredicateWithItsBody(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> "true"
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
  def cannotBeAppliedOnAnIfStatementWithAStringLiteralPredicateThatHasInterpolations(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> "something#{throws_an_exception!}"
        |  41
        |  42
        |end
      """)

    assertRefactorNotAvailable(RemoveUselessConditionalStatement)
  }

  @Test
  def cannotBeAppliedOnAnIfStatementWithASymbolLiteralPredicateThatHasInterpolations(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> :"something#{throws_an_exception!}"
        |  41
        |  42
        |end
      """)

    assertRefactorNotAvailable(RemoveUselessConditionalStatement)
  }

  @Test
  def replacesAnIfStatementWithAQuotedSymbolLiteralPredicateWithItsBody(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> :"true"
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
  def cannotBeAppliedOnAnIfStatementWithASelfValuedPredicate(): Unit = {
    // Because self can potentially be true, false or nil!
    loadRubyFileWith(
      """
        |if<caret> self
        |  42
        |end
      """)

    assertRefactorNotAvailable(RemoveUselessConditionalStatement)
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

  @Test
  def preservesCommentsWhenReplacingAnIfWithStatements(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> true
        |  # Start comment
        |  m1
        |  # Middle comment
        |  m2
        |  # End comment
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |# Start comment
        |m1
        |# Middle comment
        |m2
        |# End comment
      """)
  }

  @Test
  def preservesCommentsWhenReplacingAnIfWithoutStatements(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> true
        |  # Comment
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |# Comment
      """)
  }

  @Test
  def deletesIfWithTrueConditionAndEmptyThenBranch(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> true
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |
      """)
  }

  @Test
  def preservesIndentationOfAllChildNodesWhenReplacingAnIfWithItsThenBranch(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> true
        |  m1
        |  if inner_condition
        |    inner_m1
        |  end
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |m1
        |if inner_condition
        |  inner_m1
        |end
      """)
  }

  @Test
  def preservesIndentationOfCommentNodes(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> true
        |  m1
        |  # A comment
        |  if inner_condition
        |    # Inner comment
        |  end
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |m1
        |# A comment
        |if inner_condition
        |  # Inner comment
        |end
      """)
  }

  @Test
  def replacesFalseConditionalStatementWithNilIfThereIsNoElseBlock(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> false
        |          m1
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = nil
      """)
  }

  @Test
  def replacesFalseConditionalStatementWithElseBlockIfTheElseBlockHasOneExpressionIsEmpty(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> false
        |          m1
        |        else
        |          m2
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = m2
      """)
  }

  @Test
  def replacesFalseConditionalStatementWithElseBlockIfTheElseBlockHasManyExpressions(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> false
        |          m1
        |        else
        |          m2
        |          m3
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = begin
        |          m2
        |          m3
        |        end
      """)
  }

  @Test
  def replacesTrueConditionalStatementWithElseBlockIfTheElseBlockHasManyExpressions(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> true
        |          m1
        |          m2
        |        else
        |          m3
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = begin
        |          m1
        |          m2
        |        end
      """)
  }

  @Test
  def doesNotAddAdditionalBeginEndBlockIfTheConditionalStatementIsAlreadyInsideABeginEndBlock(): Unit = {
    loadRubyFileWith(
      """
        |value = begin
        |          if<caret> true
        |            m1
        |            m2
        |          else
        |            m3
        |          end
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = begin
        |          m1
        |          m2
        |        end
      """)
  }

  @Test
  def doesNotAddAdditionalBeginEndBlockIfTheConditionalStatementIsAlreadyInsideAMethodBlock(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> true
        |    m1
        |    m2
        |  end
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |def m1
        |  m1
        |  m2
        |end
      """)
  }

  @Test
  def doesNotAddAnAdditionalBeginEndBlockIfTheConditionalStatementIsEmptyAndInsideABlockButItHasComments(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  42
        |  if<caret> true
        |    # A comment
        |  end
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |def m1
        |  42
        |  # A comment
        |  nil
        |end
      """)
  }

  @Test
  def replacesFalseConditionalStatementWithNilIfTheElseBlockIsEmpty(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> false
        |          m1
        |        else
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = nil
      """)
  }

  @Test
  def replacesTheConditionalStatementWithNilIfItHasNoElseAndItIsTheLastExpressionInAMethod(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  42
        |  if<caret> false
        |    m1
        |  end
        |end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |def m1
        |  42
        |  nil
        |end
      """)
  }

  @Test
  def replacesConditionalWithMultipleStatementsInThenBlockWithLiteralBlockIfItWasUsedAsAnExpression(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> true
        |          m1
        |          m2
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = begin
        |          m1
        |          m2
        |        end
      """)
  }

  @Test
  def replacesConditionalWithSingleStatementInThenBlockIfItWasUsedAsAnExpression(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> true
        |          m1
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = m1
      """)
  }

  @Test
  def replacesConditionalWithEmptyThenBlockWithNilIfItWasUsedAsAnExpression(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> true
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = nil
      """)
  }

  @Test
  def preservesCommentsWhenReplacingAnIfUsedAsAnExpressionWithManyStatements(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> true
        |          # Start comment
        |          m1
        |          # Middle comment
        |          m2
        |          # End comment
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = begin
        |          # Start comment
        |          m1
        |          # Middle comment
        |          m2
        |          # End comment
        |        end
      """)
  }

  @Test
  def preservesCommentsWhenReplacingAnIfUsedAsAnExpressionWithOneStatement(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> true
        |          # Start comment
        |          m1
        |          # End comment
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = begin
        |          # Start comment
        |          m1
        |          # End comment
        |        end
      """)
  }

  @Test
  def preservesCommentsWhenReplacingAnIfUsedAsAnExpressionWithNoStatements(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> true
        |          # Start comment
        |          # End comment
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = begin
        |          # Start comment
        |          # End comment
        |        end
      """)
  }

  @Test
  def replacesIfsWithFalseConditionAndElsifsUsedAsExpression(): Unit = {
    loadRubyFileWith(
      """
        |value = if<caret> false
        |          dead_code
        |        elsif condition
        |          something
        |        else
        |          something_else
        |        end
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |value = if condition
        |          something
        |        else
        |          something_else
        |        end
      """)
  }

  @Test
  def correctlyRemovesConditionalInsideParentheses(): Unit = {
    loadRubyFileWith(
      """
        |(
        |  if<caret> true
        |    do_something
        |  else
        |    do_something_else
        |  end
        |)
      """)

    applyRefactor(RemoveUselessConditionalStatement)

    expectResultingCodeToBe(
      """
        |(
        |  do_something
        |)
      """)
  }
}
