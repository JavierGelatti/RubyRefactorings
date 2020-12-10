package com.refactorings.ruby

import org.junit.{Before, Test}

class TestExtractMethodObject extends RefactoringTestRunningInIde {

  @Before
  def activateIntention(): Unit = activateIntention(new ExtractMethodObject)

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
  def givesTheUserTheChoiceToRenameTheMethodObjectClassAndTheInvocationMessage(): Unit = {
    enableTemplates()
    loadRubyFileWith(
      """
        |def <caret>m1(other)
        |  self + other
        |end
      """)

    applyRefactor(ExtractMethodObject)
    simulateTyping(
      "NewMethodObjectClassName\tinvocation_message"
    )

    expectResultingCodeToBe(
      """
        |def m1(other)
        |  NewMethodObjectClassName.new(other, self).invocation_message
        |end
        |
        |class NewMethodObjectClassName
        |  def initialize(other, original_receiver)
        |    @other = other
        |    @original_receiver = original_receiver
        |  end
        |
        |  def invocation_message
        |    @original_receiver + @other
        |  end
        |end
      """)
  }
}
