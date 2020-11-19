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
  def isNotAvailableIfTheFocusedConditionalHasAnElseClause(): Unit = {
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

    assertRefactorNotAvailable(ReplaceConditionalWithGuardClause)
  }

  @Test
  def isNotAvailableIfTheFocusedConditionalHasAnElsifClause(): Unit = {
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

    assertRefactorNotAvailable(ReplaceConditionalWithGuardClause)
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
}
