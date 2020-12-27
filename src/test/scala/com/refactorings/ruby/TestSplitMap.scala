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

    applyRefactor(SplitMap)
    chooseOptionNamed("y = x + 1")

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
}
