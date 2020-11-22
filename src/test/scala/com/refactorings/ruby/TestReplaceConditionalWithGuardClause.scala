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
  def isAvailableWhenThereIsAnElseClauseButTheIfClauseHasOnlyOneStatement(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    code
        |  else
        |    more_code
        |    even_more_code
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
        |  even_more_code
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
        |    even_more_code
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
        |    more_code
        |  elsif condition3
        |    even_more_code( a  ,  b)
        |    more_code
        |  elsif condition4
        |    even_more_code!( a  ,  b)
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
        |    more_code
        |  elsif condition3
        |    even_more_code( a  ,  b)
        |    more_code
        |  elsif condition4
        |    even_more_code!( a  ,  b)
        |    more_code
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
        |    more_code
        |  else
        |    even_more_code
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
        |    more_code
        |  else
        |    even_more_code
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
        |    more_code
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
        |  more_code
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
        |    even_more_code
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
        |  even_more_code
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

  @Test
  def returnsTheLastExpressionInsideTheIfAndRemovesTheElseIfTheIfStatementContainsMoreThanOneExpression(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    something
        |    return_value
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
        |  if<caret> condition
        |    something
        |    return return_value
        |  end
        |
        |  more_code
        |  more_code
        |end
      """)
  }

  @Test
  def doesNotGenerateExtraReturnsIfTheIfStatementContainsMoreThanOneExpressionAndAlreadyIsReturning(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    something
        |    return return_value
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
        |  if<caret> condition
        |    something
        |    return return_value
        |  end
        |
        |  more_code
        |  more_code
        |end
      """)
  }

  @Test
  def preservesElsifsIfTheIfStatementContainsMoreThanOneExpression(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition1
        |    something
        |    return_value
        |  elsif condition2
        |    more_code1
        |    more_code2
        |  elsif condition3
        |    more_code3
        |    more_code4
        |  else
        |    more_code5
        |    more_code6
        |  end
        |end
      """)

    applyRefactor(ReplaceConditionalWithGuardClause)

    expectResultingCodeToBe(
      """
        |def m1
        |  if<caret> condition1
        |    something
        |    return return_value
        |  end
        |
        |  if condition2
        |    more_code1
        |    more_code2
        |  elsif condition3
        |    more_code3
        |    more_code4
        |  else
        |    more_code5
        |    more_code6
        |  end
        |end
      """)
  }

  @Test
  def isNotAvailableIfTheThenBlockIsEmpty(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition1
        |
        |  end
        |end
      """)

    assertRefactorNotAvailable(ReplaceConditionalWithGuardClause)
  }

  @Test
  def doesNotAddAReturnStatementIfAnExceptionWasBeingRaised(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  if<caret> condition
        |    something
        |    raise "an error"
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
        |  if<caret> condition
        |    something
        |    raise "an error"
        |  end
        |
        |  more_code
        |  more_code
        |end
      """)
  }
}
