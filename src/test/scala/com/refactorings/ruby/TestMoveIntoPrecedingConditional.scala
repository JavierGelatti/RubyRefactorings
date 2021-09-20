package com.refactorings.ruby

import org.junit.{Ignore, Test}

@Ignore
class TestMoveIntoPrecedingConditional extends RefactoringTestRunningInIde {
  @Test
  def movesAStatementIntoTheThenAndElseBranchesOfAConditionalBeforeIt(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> condition
        |  a
        |else
        |  b
        |end
        |
        |<caret>thing
        |another_thing
      """)

    applyRefactor(MoveIntoPrecedingConditional)

    expectResultingCodeToBe(
      """
        |if condition
        |  a
        |  thing
        |else
        |  b
        |  thing
        |end
        |
        |another_thing
      """)
  }

  @Test
  def cannotBeAppliedIfTheConditionalHasNoElseBranch(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> condition
        |  a
        |end
        |
        |<caret>thing
      """)

    assertRefactorNotAvailable(MoveIntoPrecedingConditional)
  }

  @Test
  def movesAStatementIntoAllElsifBlocksOfAConditionalBeforeIt(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> condition
        |  a
        |elsif another_condition
        |  b
        |else
        |  c
        |end
        |
        |<caret>thing
        |another_thing
      """)

    applyRefactor(MoveIntoPrecedingConditional)

    expectResultingCodeToBe(
      """
        |if condition
        |  a
        |  thing
        |elsif another_condition
        |  b
        |  thing
        |else
        |  c
        |  thing
        |end
        |
        |another_thing
      """)
  }
}
