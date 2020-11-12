package com.refactorings.ruby

import org.junit.Test

import scala.language.reflectiveCalls

class TestIntroduceVariableInsideString extends RefactoringTestRunningInIde {
  @Test
  def introducesVariableInsideStringIfASubstringIsSelected(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>mundo</selection>"
      """)

    applyRefactor(IntroduceVariableInsideString)

    expectResultingCodeToBe(
      """
        |var = "mundo"
        |"hola#{var}"
      """)
  }

  @Test
  def introducesVariableInsideStringIfASubstringWithInterpolationsIsSelected(): Unit = {
    loadRubyFileWith(
      """
        |"hola<selection>#{"mundo"}</selection>"
      """)

    applyRefactor(IntroduceVariableInsideString)

    expectResultingCodeToBe(
      """
        |var = "#{"mundo"}"
        |"hola#{var}"
      """)
  }


  @Test
  def isNotAvailableIfNothingIsSelected(): Unit = {
    loadRubyFileWith(
      """
        |"hola<caret>mundo"
      """)

    assertRefactorNotAvailable(IntroduceVariableInsideString)
  }
}
