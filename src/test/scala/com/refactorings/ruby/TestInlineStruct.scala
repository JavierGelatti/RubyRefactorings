package com.refactorings.ruby

import org.junit.Test

class TestInlineStruct extends RefactoringTestRunningInIde {
  @Test
  def removesTheSuperclassWhenTheStructToInlineIsEmpty(): Unit = {
    loadRubyFileWith(
      """
        |class X < <caret>Struct.new()
        |end
      """)

    applyRefactor(InlineStruct)

    expectResultingCodeToBe(
      """
        |class X
        |end
      """)
  }

  @Test
  def removesTheSuperclassEvenIfThereAreMultipleSpacesBetweenTheClasses(): Unit = {
    loadRubyFileWith(
      """
        |class X     <
        |
        |  <caret>Struct.new()
        |end
      """)

    applyRefactor(InlineStruct)

    expectResultingCodeToBe(
      """
        |class X
        |end
      """)
  }

  @Test
  def isNotAvailableWhenTheCaretIsOutsideTheClassDefinition(): Unit = {
    loadRubyFileWith(
      """
        |<caret>
        |class X < Struct.new()
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }
}
