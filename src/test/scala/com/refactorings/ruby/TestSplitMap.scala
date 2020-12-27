package com.refactorings.ruby

import com.intellij.openapi.util.TextRange
import org.junit.Test

class TestSplitMap extends RefactoringTestRunningInIde {
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

    applyRefactor(SplitMap)

    expectOptions(
      "Select statements to include",
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

    assertRefactorNotAvailable(SplitMap)
  }

  @Test
  def isNotAvailableIfThereIsOnlyOneExpressionInsideTheBlock(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |  n + 1
        |end
      """)

    assertRefactorNotAvailable(SplitMap)
  }

  @Test
  def isNotAvailableIfThereAreNoExpressionsInsideTheBlock(): Unit = {
    loadRubyFileWith(
      """
        |[1, 2, 3].<caret>map do |n|
        |end
      """)

    assertRefactorNotAvailable(SplitMap)
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

  private def applySplitRefactor(splitPoint: String): Unit = {
    applyRefactor(SplitMap)
    chooseOptionNamed(splitPoint)
  }
}
