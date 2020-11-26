package com.refactorings.ruby

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import org.junit.Test

class TestExtractMethodObject extends RefactoringTestRunningInIde {
  @Test
  def extractsAMethodObjectIfTheMethodHasNoParameters(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  1 + 1
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1
        |  M1MethodObject.new.invoke
        |end
        |
        |class M1MethodObject
        |  def invoke
        |    1 + 1
        |    42
        |  end
        |end
      """)
  }

  @Test
  def extractsAMethodObjectConvertingTheMethodParametersToInstanceVariables(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(a, b)
        |  a + b
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(a, b)
        |  M1MethodObject.new(a, b).invoke
        |end
        |
        |class M1MethodObject
        |  def initialize(a, b)
        |    @a = a
        |    @b = b
        |  end
        |
        |  def invoke
        |    @a + @b
        |  end
        |end
      """)
  }

  @Test
  def preservesFormattingOfOriginalMethodBody(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(a, b)
        |  a.object_id
        |  b.object_id
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(a, b)
        |  M1MethodObject.new(a, b).invoke
        |end
        |
        |class M1MethodObject
        |  def initialize(a, b)
        |    @a = a
        |    @b = b
        |  end
        |
        |  def invoke
        |    @a.object_id
        |    @b.object_id
        |  end
        |end
      """)
  }

  @Test
  def isNotAvailableIfCaretIsNotInsideMethodName(): Unit = {
    loadRubyFileWith(
      """
        |def m1(a, b)
        |  a<caret> + b
        |end
      """)

    assertRefactorNotAvailable(ExtractMethodObject)
  }

  @Test
  def parameterizesSelfIfItWasUsedInTheOriginalMethod(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  self.m2 + self
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1
        |  M1MethodObject.new(self).invoke
        |end
        |
        |class M1MethodObject
        |  def initialize(original_receiver)
        |    @original_receiver = original_receiver
        |  end
        |
        |  def invoke
        |    @original_receiver.m2 + @original_receiver
        |  end
        |end
      """)
  }

  @Test
  def parameterizesSelfAlongWithTheOriginalMethodParameters(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(other)
        |  self + other
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(other)
        |  M1MethodObject.new(other, self).invoke
        |end
        |
        |class M1MethodObject
        |  def initialize(other, original_receiver)
        |    @other = other
        |    @original_receiver = original_receiver
        |  end
        |
        |  def invoke
        |    @original_receiver + @other
        |  end
        |end
      """)
  }

  @Test
  def givesTheUserTheChoiceToRenameTheMethodObjectClass(): Unit = {
    enableTemplates()
    loadRubyFileWith(
      """
        |def <caret>m1(other)
        |  self + other
        |end
      """)

    applyRefactor(ExtractMethodObject)
    simulateTyping("NewMethodObjectClassName")

    expectResultingCodeToBe(
      """
        |def m1(other)
        |  NewMethodObjectClassName<caret>.new(other, self).invoke
        |end
        |
        |class NewMethodObjectClassName
        |  def initialize(other, original_receiver)
        |    @other = other
        |    @original_receiver = original_receiver
        |  end
        |
        |  def invoke
        |    @original_receiver + @other
        |  end
        |end
      """)
  }
}
