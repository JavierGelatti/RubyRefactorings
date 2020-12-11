package com.refactorings.ruby

import com.intellij.openapi.util.TextRange
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
        |  M1MethodObject.new.call
        |end
        |
        |class M1MethodObject
        |  def call
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
        |  M1MethodObject.new(a, b).call
        |end
        |
        |class M1MethodObject
        |  def initialize(a, b)
        |    @a = a
        |    @b = b
        |  end
        |
        |  def call
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
        |  M1MethodObject.new(a, b).call
        |end
        |
        |class M1MethodObject
        |  def initialize(a, b)
        |    @a = a
        |    @b = b
        |  end
        |
        |  def call
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
        |  M1MethodObject.new(self).call
        |end
        |
        |class M1MethodObject
        |  def initialize(original_receiver)
        |    @original_receiver = original_receiver
        |  end
        |
        |  def call
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
        |  M1MethodObject.new(other, self).call
        |end
        |
        |class M1MethodObject
        |  def initialize(other, original_receiver)
        |    @other = other
        |    @original_receiver = original_receiver
        |  end
        |
        |  def call
        |    @original_receiver + @other
        |  end
        |end
      """)
  }

  @Test
  def addsExplicitReceiverReplacingImplicitSelfReferences(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  m2
        |  Array.new(m2)
        |end
        |
        |public def m2
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1
        |  M1MethodObject.new(self).call
        |end
        |
        |class M1MethodObject
        |  def initialize(original_receiver)
        |    @original_receiver = original_receiver
        |  end
        |
        |  def call
        |    @original_receiver.m2
        |    Array.new(@original_receiver.m2)
        |  end
        |end
        |
        |public def m2
        |  42
        |end
      """)
  }

  @Test
  def doesNotPerformTheExtractionIfAPrivateMethodIsBeingCalled(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  m2
        |end
        |
        |private def m2
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    assertCodeDidNotChange()
    expectErrorHint(
      new TextRange(9, 11),
      "Cannot perform refactoring if a private method is being called"
    )
  }

  @Test
  def doesNotPerformTheExtractionIfAProtectedMethodIsBeingCalled(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  m2
        |end
        |
        |protected def m2
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    assertCodeDidNotChange()
    expectErrorHint(
      new TextRange(9, 11),
      "Cannot perform refactoring if a private method is being called"
    )
  }

  @Test
  def doesNotChangeTheFormattingIfTheRefactoringCannotBePerformed(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |
        |
        |
        |
        |  m2
        |end
        |
        |private def m2
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    assertCodeDidNotChange()
    expectErrorHint(
      new TextRange(13, 15),
      "Cannot perform refactoring if a private method is being called"
    )
  }

  @Test
  def doesPerformTheExtractionIfAnUndefinedMethodIsBeingCalled(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  m2
        |  Array.new(m2)
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1
        |  M1MethodObject.new(self).call
        |end
        |
        |class M1MethodObject
        |  def initialize(original_receiver)
        |    @original_receiver = original_receiver
        |  end
        |
        |  def call
        |    @original_receiver.m2
        |    Array.new(@original_receiver.m2)
        |  end
        |end
      """)
  }

  @Test
  def doesPerformTheExtractionIfTheCodeIsUsingAnAttrReader(): Unit = {
    loadRubyFileWith(
      """
        |class C1
        |  attr_reader :m2
        |
        |  def <caret>m1
        |    m2
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class C1
        |  attr_reader :m2
        |
        |  def m1
        |    M1MethodObject.new(self).call
        |  end
        |
        |  class M1MethodObject
        |    def initialize(original_receiver)
        |      @original_receiver = original_receiver
        |    end
        |
        |    def call
        |      @original_receiver.m2
        |    end
        |  end
        |end
      """)
  }

  @Test
  def doesPerformTheExtractionForPublicMethods(): Unit = {
    loadRubyFileWith(
      """
        |class C1
        |  def m2
        |  end
        |
        |  def <caret>m1
        |    m2
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class C1
        |  def m2
        |  end
        |
        |  def m1
        |    M1MethodObject.new(self).call
        |  end
        |
        |  class M1MethodObject
        |    def initialize(original_receiver)
        |      @original_receiver = original_receiver
        |    end
        |
        |    def call
        |      @original_receiver.m2
        |    end
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

  @Test
  def doesNotPerformTheExtractionIfThereAreInstanceVariableReferences(): Unit = {
    enableTemplates()
    loadRubyFileWith(
      """
        |def <caret>m1(other)
        |  m2
        |  @var
        |end
      """)

    applyRefactor(ExtractMethodObject)

    assertCodeDidNotChange()
    expectErrorHint(
      new TextRange(21, 25),
      "Cannot perform refactoring if there are references to instance variables"
    )
  }

  @Test
  def preservesBlockParameters(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(&block)
        |  block.call
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(&block)
        |  M1MethodObject.new(block).call
        |end
        |
        |class M1MethodObject
        |  def initialize(block)
        |    @block = block
        |  end
        |
        |  def call
        |    @block.call
        |  end
        |end
      """)
  }

  @Test
  def addsABlockParameterIfTheMethodHadNoParametersButYieldsToABlock(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  yield
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(&block)
        |  M1MethodObject.new.call(&block)
        |end
        |
        |class M1MethodObject
        |  def call
        |    yield
        |  end
        |end
      """)
  }

  @Test
  def addsABlockParameterIfTheMethodHadParametersAndYieldsToABlock(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(a)
        |  yield
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(a, &block)
        |  M1MethodObject.new(a).call(&block)
        |end
        |
        |class M1MethodObject
        |  def initialize(a)
        |    @a = a
        |  end
        |
        |  def call
        |    yield
        |  end
        |end
      """)
  }
}
