package com.refactorings.ruby

import org.junit.Test

import scala.language.reflectiveCalls

class TestRemoveUnnecessaryHashBraces extends RefactoringTestRunningInIde {
  @Test
  def removesHashBracesFromOneArgumentMessageSendsWithOneKeyHashArgument(): Unit = {
    loadFileWith(
      """
        |m1<caret>({ a: 1 })
      """)

    applyRefactor(RemoveUnnecessaryHashBraces)

    expectResultingCodeToBe(
      """
        |m1(a: 1)
      """)
  }
}
