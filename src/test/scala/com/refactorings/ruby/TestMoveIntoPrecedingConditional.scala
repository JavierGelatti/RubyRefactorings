package com.refactorings.ruby

import org.junit.Test

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

  @Test
  def preservesCommentsInsideTheConditionalAndAfterTheStatementToMove(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> condition
        |  # Comment 1
        |elsif another_condition
        |  # Comment 2
        |else
        |  # Comment 3
        |end
        |
        |<caret>thing
        |# Comment 4
        |another_thing
      """)

    applyRefactor(MoveIntoPrecedingConditional)

    expectResultingCodeToBe(
      """
        |if condition
        |  # Comment 1
        |  thing
        |elsif another_condition
        |  # Comment 2
        |  thing
        |else
        |  # Comment 3
        |  thing
        |end
        |
        |# Comment 4
        |another_thing
      """)
  }

  @Test
  def movesCommentsBetweenTheConditionalAndTheStatementToMove(): Unit = {
    loadRubyFileWith(
      """
        |if<caret> condition
        |elsif another_condition
        |else
        |end
        |
        |# Comment about thing
        |<caret>thing
        |another_thing
      """)

    applyRefactor(MoveIntoPrecedingConditional)

    expectResultingCodeToBe(
      """
        |if condition
        |  # Comment about thing
        |  thing
        |elsif another_condition
        |  # Comment about thing
        |  thing
        |else
        |  # Comment about thing
        |  thing
        |end
        |
        |another_thing
      """)
  }
}
