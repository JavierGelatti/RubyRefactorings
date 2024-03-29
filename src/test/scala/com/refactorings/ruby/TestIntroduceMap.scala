package com.refactorings.ruby

import com.intellij.openapi.util.TextRange
import org.junit.Test

class TestIntroduceMap extends RefactoringTestRunningInIde {
  @Test
  def offersOptionsToChooseTheScopeOfTheExtraction(): Unit = {
    loadRubyFileWith(
      """
        |[1,2,3].<caret>map do |n|
        |  x = n + 1
        |  y = x + 1
        |  z = y + 1
        |end
      """)

    applyRefactor(IntroduceMap)

    expectOptions(
      "Select statements to include in map block",
      List(
        ("x = n + 1", new TextRange(21, 30)),
        ("y = x + 1", new TextRange(21, 42)),
      )
    )
  }

  @Test
  def splitsTheMapWhenThePartitionsOnlyShareOneVariable(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  x = n + 1
        |  y = x + 1
        |  z = y + 1
        |end
      """)

    applySplitRefactor(splitPoint = "y = x + 1")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  x = n + 1
        |  y = x + 1
        |  y
        |end.map do |y|
        |  z = y + 1
        |end
      """)
  }

  @Test
  def splitsTheMapWhenThePartitionsShareNoVariables(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  x = n + 1
        |  42
        |end
      """)

    applySplitRefactor(splitPoint = "x = n + 1")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  x = n + 1
        |end.map do
        |  42
        |end
      """)
  }

  @Test
  def splitsTheMapWhenThePartitionsOnlyShareMoreThanOneVariable(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  x = n + 1
        |  y = n + 2
        |  z = x + y
        |end
      """)

    applySplitRefactor(splitPoint = "y = n + 2")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  x = n + 1
        |  y = n + 2
        |  [x, y]
        |end.map do |x, y|
        |  z = x + y
        |end
      """)
  }

  @Test
  def doesNotParameterizeVariablesDefinedOutsideTheScopeOfTheBlock(): Unit = {
    loadRubyFileWith(
      """
        |p = 1
        |
        |[1, 2, 3].<caret>map do |n|
        |  x = p
        |  z = x + p
        |end
      """)

    applySplitRefactor(splitPoint = "x = p")

    expectResultingCodeToBe(
      """
        |p = 1
        |
        |[1, 2, 3].map do |n|
        |  x = p
        |  x
        |end.map do |x|
        |  z = x + p
        |end
      """)
  }

  @Test
  def doesParameterizeTheOriginalBlockParameter(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  x = n + 1
        |  n * x
        |end
      """)

    applySplitRefactor(splitPoint = "x = n + 1")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  x = n + 1
        |  [x, n]
        |end.map do |x, n|
        |  n * x
        |end
      """)
  }

  @Test
  def isNotAvailableIfTheMessageIsNotMap(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>lala do |n|
        |  x = n + 1
        |  z = x + 1
        |end
      """)

    assertRefactorNotAvailable(IntroduceMap)
  }

  @Test
  def isNotAvailableIfThereIsOnlyOneExpressionInsideTheBlock(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  n + 1
        |end
      """)

    assertRefactorNotAvailable(IntroduceMap)
  }

  @Test
  def isNotAvailableIfThereAreNoExpressionsInsideTheBlock(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |end
      """)

    assertRefactorNotAvailable(IntroduceMap)
  }

  @Test
  def preservesTheOriginalDelimiters(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map { |n|
        |  x = n + 1
        |  y = x + 1
        |  z = y + 1
        |}
      """)

    applySplitRefactor(splitPoint = "y = x + 1")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map { |n|
        |  x = n + 1
        |  y = x + 1
        |  y
        |}.map { |y|
        |  z = y + 1
        |}
      """)
  }

  @Test
  def copiesRescueBlocks(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  x = n + 1
        |  2 * x
        |rescue SomeExceptionClass => some_variable
        |  # rescue 1
        |  "rescue"
        |rescue
        |  # rescue 2
        |  "rescue"
        |else
        |  # no exceptions
        |  "no exceptions"
        |ensure
        |  # finally
        |  "finally"
        |end
      """)

    applySplitRefactor(splitPoint = "x = n + 1")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].<caret>map do |n|
        |  x = n + 1
        |  x
        |rescue SomeExceptionClass => some_variable
        |  # rescue 1
        |  "rescue"
        |rescue
        |  # rescue 2
        |  "rescue"
        |else
        |  # no exceptions
        |  "no exceptions"
        |ensure
        |  # finally
        |  "finally"
        |end.map do |x|
        |  2 * x
        |rescue SomeExceptionClass => some_variable
        |  # rescue 1
        |  "rescue"
        |rescue
        |  # rescue 2
        |  "rescue"
        |else
        |  # no exceptions
        |  "no exceptions"
        |ensure
        |  # finally
        |  "finally"
        |end
      """)
  }

  @Test
  def preservesFormatting(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  [1,2,3].<caret>map do
        |    1
        |    2
        |  end
        |end
      """)

    applySplitRefactor(splitPoint = "1")

    expectResultingCodeToBe(
      """
        |def m1
        |  [1,2,3].<caret>map do
        |    1
        |  end.map do
        |    2
        |  end
        |end
      """)
  }

  @Test
  def worksWithCollect(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  [1, 2, 3].<caret>collect do
        |    1
        |    2
        |  end
        |end
      """)

    applySplitRefactor(splitPoint = "1")

    expectResultingCodeToBe(
      """
        |def m1
        |  [1, 2, 3].<caret>collect do
        |    1
        |  end.collect do
        |    2
        |  end
        |end
      """)
  }

  @Test
  def worksWithEach(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  [1, 2, 3].<caret>each do |n|
        |    x = n + 1
        |    puts x
        |  end
        |end
      """)

    applySplitRefactor(splitPoint = "x = n + 1")

    expectResultingCodeToBe(
      """
        |def m1
        |  [1, 2, 3].<caret>map do |n|
        |    x = n + 1
        |    x
        |  end.each do |x|
        |    puts x
        |  end
        |end
      """)
  }

  @Test
  def cannotPerformRefactoringIfThereAreNextCallsInTheFirstPart(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  [1, 2, 3].<caret>map do |n|
        |    next 2
        |    x = n + 1
        |    42
        |  end
        |end
      """)

    applySplitRefactor(splitPoint = "x = n + 1")

    assertCodeDidNotChange()
    expectErrorHint(
      new TextRange(34, 40),
      "Cannot perform refactoring if next is called inside the selection"
    )
  }

  @Test
  def canPerformRefactoringIfThereAreNextCallsInsideNestedBlocksInTheFirstPart(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  [1, 2, 3].<caret>map do |n|
        |    [4].map { next 5 }.map do next 6; end
        |    x = n + 1
        |    42
        |  end
        |end
      """)

    applySplitRefactor(splitPoint = "x = n + 1")

    expectResultingCodeToBe(
      """
        |def m1
        |  [1, 2, 3].map do |n|
        |    [4].map { next 5 }.map do next 6; end
        |    x = n + 1
        |  end.map do
        |    42
        |  end
        |end
      """)
  }

  @Test
  def canPerformRefactoringIfThereAreNextCallsInTheSecondPart(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  [1, 2, 3].<caret>map do |n|
        |    x = n + 1
        |    next 42
        |  end
        |end
      """)

    applySplitRefactor(splitPoint = "x = n + 1")

    expectResultingCodeToBe(
      """
        |def m1
        |  [1, 2, 3].map do |n|
        |    x = n + 1
        |  end.map do
        |    next 42
        |  end
        |end
      """)
  }

  @Test
  def cannotPerformRefactoringIfThereAreBreakCallsInTheFirstPart(): Unit = {
    loadRubyFileWith(
      """
        |def m1
        |  [1, 2, 3].<caret>map do |n|
        |    break 2
        |    x = n + 1
        |    42
        |  end
        |end
      """)

    applySplitRefactor(splitPoint = "x = n + 1")

    assertCodeDidNotChange()
    expectErrorHint(
      new TextRange(34, 41),
      "Cannot perform refactoring if break is called inside the selection"
    )
  }

  @Test
  def removesUnnecessarySemicolonsAtSplitPoint(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>each do |n|
        |  x = f(n); y = x + 1
        |
        |  puts y + 1
        |end
      """)

    applySplitRefactor(splitPoint = "x = f(n)")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  x = f(n)
        |  x
        |end.each do |x|
        |  y = x + 1
        |
        |  puts y + 1
        |end
      """)
  }

  @Test
  def removesUnnecessarySemicolonsEvenIfThereAreNoSpacesBetweenStatements(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>each do |n|
        |  x = f(n);y = x + 1
        |
        |  puts y + 1
        |end
      """)

    applySplitRefactor(splitPoint = "x = f(n)")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  x = f(n)
        |  x
        |end.each do |x|
        |  y = x + 1
        |
        |  puts y + 1
        |end
      """)
  }

  @Test
  def doesNotRepeatTheParameterVariablesEvenIfTheyAreUsedMoreThanOnce(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  x = n + 1
        |  x = x + n
        |  x + n
        |end
      """)

    applySplitRefactor(splitPoint = "x = x + n")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  x = n + 1
        |  x = x + n
        |  [x, n]
        |end.map do |x, n|
        |  x + n
        |end
      """)
  }

  @Test
  def maintainsCommentsOutsideTheSplitPoint(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  # Comment 1
        |  w = n + 1
        |  # Comment 2
        |  x = w + 1
        |  y = x + 1
        |  # Comment 3
        |  z = y + 1
        |  # Comment 4
        |end
      """)

    applySplitRefactor(splitPoint = "x = w + 1")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  # Comment 1
        |  w = n + 1
        |  # Comment 2
        |  x = w + 1
        |  x
        |end.map do |x|
        |  y = x + 1
        |  # Comment 3
        |  z = y + 1
        |  # Comment 4
        |end
      """)
  }

  @Test
  def commentsJustAfterTheSplitPointFollowTheStatementNextToThem(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  w = n + 1
        |  x = w + 1
        |  # This is
        |  # a comment
        |  y = x + 1
        |end
      """)

    applySplitRefactor(splitPoint = "x = w + 1")

    expectResultingCodeToBe(
      """
        |[1, 2, 3].map do |n|
        |  w = n + 1
        |  x = w + 1
        |  x
        |end.map do |x|
        |  # This is
        |  # a comment
        |  y = x + 1
        |end
      """)
  }

  private def applySplitRefactor(splitPoint: String): Unit = {
    applyRefactor(IntroduceMap)
    chooseOptionNamed(splitPoint)
  }
}
