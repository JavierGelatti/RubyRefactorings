package com.refactorings.ruby

import org.junit.Test

class TestMoveIntoPrecedingConditional extends RefactoringTestRunningInIde {
  @Test
  def movesAStatementIntoTheThenAndElseBranchesOfAConditionalBeforeIt(): Unit = {
    loadRubyFileWith(
      """
        |if condition
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
        |if condition
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
        |if condition
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
        |if condition
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
        |if condition
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

  @Test
  def movesCommentsThatWereInTheSameLineAsTheStatementToMove(): Unit = {
    loadRubyFileWith(
      """
        |if condition
        |elsif another_condition
        |else
        |end
        |
        |<caret>thing # Comment about thing
        |another_thing
      """)

    applyRefactor(MoveIntoPrecedingConditional)

    expectResultingCodeToBe(
      """
        |if condition
        |  thing # Comment about thing
        |elsif another_condition
        |  thing # Comment about thing
        |else
        |  thing # Comment about thing
        |end
        |
        |another_thing
      """)
  }

  @Test
  def worksIfCaretIsAtEndOfStatementToMove(): Unit = {
    loadRubyFileWith(
      """
        |if condition
        |else
        |end
        |
        |thing<caret>
      """)

    applyRefactor(MoveIntoPrecedingConditional)

    expectResultingCodeToBe(
      """
        |if condition
        |  thing
        |else
        |  thing
        |end
        |
      """)
  }
}
