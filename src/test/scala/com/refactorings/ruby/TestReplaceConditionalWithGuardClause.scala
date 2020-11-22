package com.refactorings.ruby

import org.junit.Test

class TestReplaceConditionalWithGuardClause extends RefactoringTestRunningInIde {
  @Test
  def replacesTheSelectedIfStatementWithAGuardClausePuttingTheThenBlockContentsAfterIt(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    code
        |    more_code
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return unless condition
        |
        |  code
        |  more_code
        |end
      """)
  }

  @Test
  def preservesFormattingOfTheOriginalThenClause(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    code
        |
        |    if another_condition
        |      indented_code
        |    end
        |  end
        |
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return unless condition
        |
        |  code
        |
        |  if another_condition
        |    indented_code
        |  end
        |
        |end
      """)
  }

  @Test
  def preservesTheCodeBeforeTheConditional(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  return if condition1
        |
        |  if<caret> condition2
        |    code
        |    more_code
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return if condition1
        |
        |  return unless condition2
        |
        |  code
        |  more_code
        |end
      """)
  }

  @Test
  def isNotAvailableIfTheFocusedElementIsNotAConditional(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  return if condition1
        |
        |  if condition2
        |    code<caret>
        |    more_code
        |  end
        |end
      """)

    assertRefactorNotAvailable(ReplaceConditionalWithGuardClause)
  }

  @Test
  def isNotAvailableIfTheFocusedConditionalDoesNotSpanTheWholeMethod(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    code
        |    more_code
        |  end
        |
        |  42
        |end
      """)

    assertRefactorNotAvailable(ReplaceConditionalWithGuardClause)
  }

  @Test
  def isNotAvailableWhenThereIsAnElseClauseAndTheIfClauseHasMoreThanOneStatement(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition1
        |    code
        |    more_code
        |  else
        |    even_more_code
        |  end
        |end
      """)

    assertRefactorNotAvailable(ReplaceConditionalWithGuardClause)
  }

  @Test
  def isAvailableWhenThereIsAnElseClauseButTheIfClauseHasOnlyOneStatement(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    code
        |  else
        |    more_code
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return code if condition
        |
        |  more_code
        |end
      """)
  }

  @Test
  def isAvailableIfTheFocusedConditionalHasASingleExpressionInTheThenBlockAndAnElsifClause(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition1
        |    code
        |  elsif condition2
        |    more_code
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return code if condition1
        |
        |  if condition2
        |    more_code
        |  end
        |end
      """)
  }

  @Test
  def maintainsAllOfTheElsifs(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition1
        |    code
        |  elsif condition2
        |    more_code
        |  elsif condition3
        |    even_more_code( a  ,  b)
        |  elsif condition4
        |    even_more_code!( a  ,  b)
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return code if condition1
        |
        |  if condition2
        |    more_code
        |  elsif condition3
        |    even_more_code( a  ,  b)
        |  elsif condition4
        |    even_more_code!( a  ,  b)
        |  end
        |end
      """)
  }

  @Test
  def maintainsTheElseWhenThereAreElsifs(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition1
        |    code
        |  elsif condition2
        |    more_code
        |  else
        |    even_more_code
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return code if condition1
        |
        |  if condition2
        |    more_code
        |  else
        |    even_more_code
        |  end
        |end
      """)
  }

  @Test
  def isNotAvailableIfTheFocusedConditionalIsNotDirectlyInsideAMethod(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if top_level_condition
        |    if<caret> nested_condition
        |      code
        |    end
        |  end
        |end
      """)

    assertRefactorNotAvailable(ReplaceConditionalWithGuardClause)
  }

  @Test
  def isAvailableForUnlessStatementsWithoutElseClause(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  unless<caret> condition
        |    code
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return if condition
        |
        |  code
        |end
      """)
  }

  @Test
  def isAvailableForUnlessStatementsWhenThereIsAnElseClauseButTheThenClauseHasOnlyOneStatement(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  unless<caret> condition
        |    code
        |  else
        |    more_code
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return code unless condition
        |
        |  more_code
        |end
      """)
  }

  @Test
  def reusesExistingReturnsForGuardBody(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    return something
        |  else
        |    more_code
        |    more_code
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  return something if condition
        |
        |  more_code
        |  more_code
        |end
      """)
  }
}
