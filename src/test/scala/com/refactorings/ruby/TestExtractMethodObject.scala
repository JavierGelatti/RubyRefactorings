package com.refactorings.ruby

import com.intellij.openapi.util.TextRange
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
        |  M1MethodObject.new(self, other).call
        |end
        |
        |class M1MethodObject
        |  def initialize(original_receiver, other)
        |    @original_receiver = original_receiver
        |    @other = other
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
        |  m0
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
      new TextRange(14, 16),
      "Cannot perform refactoring if a private/protected method is being called"
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
      "Cannot perform refactoring if a private/protected method is being called"
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
      "Cannot perform refactoring if a private/protected method is being called"
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
        |  other
        |end
      """)

    applyRefactor(ExtractMethodObject)
    simulateTyping(
      "NewMethodObjectClassName\tinvocation_message\n"
    )

    expectResultingCodeToBe(
      """
        |def m1(other)
        |  NewMethodObjectClassName.new(other).invocation_message
        |end
        |
        |class NewMethodObjectClassName
        |  def initialize(other)
        |    @other = other
        |  end
        |
        |  def invocation_message
        |    @other
        |  end
        |end
      """)
  }

  @Test
  def givesTheUserTheChoiceToRenameTheMethodObjectClassTheInvocationMessageAndTheOriginalReceiverVariables(): Unit = {
    enableTemplates()
    loadRubyFileWith(
      """
        |def <caret>m1(other)
        |  self + other
        |end
      """)

    applyRefactor(ExtractMethodObject)
    simulateTyping(
      "NewMethodObjectClassName\tinvocation_message\tmyself\n"
    )

    expectResultingCodeToBe(
      """
        |def m1(other)
        |  NewMethodObjectClassName.new(self, other).invocation_message
        |end
        |
        |class NewMethodObjectClassName
        |  def initialize(myself, other)
        |    @myself = myself
        |    @other = other
        |  end
        |
        |  def invocation_message
        |    @myself + @other
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
  def doesNotPerformTheExtractionIfThereAreClassVariableReferences(): Unit = {
    enableTemplates()
    loadRubyFileWith(
      """
        |def <caret>m1(other)
        |  m2
        |  @@var
        |end
      """)

    applyRefactor(ExtractMethodObject)

    assertCodeDidNotChange()
    expectErrorHint(
      new TextRange(21, 26),
      "Cannot perform refactoring if there are references to class variables"
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

  @Test
  def passesTheExistingBlockParameterIfTheMethodYields(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(&closure)
        |  yield
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(&closure)
        |  M1MethodObject.new(closure).call(&closure)
        |end
        |
        |class M1MethodObject
        |  def initialize(closure)
        |    @closure = closure
        |  end
        |
        |  def call
        |    yield
        |  end
        |end
      """)
  }

  @Test
  def passesABlockParameterIfTheMethodChecksForABlock(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  block_given?
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
        |    block_given?
        |  end
        |end
      """)
  }

  @Test
  def passesABlockParameterIfTheMethodChecksIfItsAnIterator(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  iterator?
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
        |    iterator?
        |  end
        |end
      """)
  }

  @Test
  def doesNotPassABlockParameterIfTheMethodSendsTheSameMessageToCheckForABlockButNotToSelf(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  v1 = nil
        |  v1.block_given?
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
        |    v1 = nil
        |    v1.block_given?
        |  end
        |end
      """)
  }

  @Test
  def makesSelfReferencesExplicitForFunnyIdentifiers(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  m2?
        |end
        |
        |public def m2?
        |  true
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
        |    @original_receiver.m2?
        |  end
        |end
        |
        |public def m2?
        |  true
        |end
      """)
  }

  @Test
  def leavesTheReceiverAsItIsWhenItIsNotAnImplicitSelfForFunnyIdentifiers(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  Object.nil?
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
        |    Object.nil?
        |  end
        |end
      """)
  }

  @Test
  def changesSelfReferencesToPointToInstanceVariableForFunnyIdentifiers(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  self.m2?
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
        |    @original_receiver.m2?
        |  end
        |end
      """)
  }

  @Test
  def ignoresQuestionMarkFromOriginalMethodNameForTheMethodObjectClassName(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>is_empty?
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def is_empty?
        |  IsEmptyMethodObject.new.call
        |end
        |
        |class IsEmptyMethodObject
        |  def call
        |    42
        |  end
        |end
      """)
  }

  @Test
  def replacesEqualsSignFromOriginalMethodNameByWriteForTheMethodObjectClassName(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1= value
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1= value
        |  WriteM1MethodObject.new(value).call
        |end
        |
        |class WriteM1MethodObject
        |  def initialize(value)
        |    @value = value
        |  end
        |
        |  def call
        |    42
        |  end
        |end
      """)
  }

  @Test
  def replacesWellKnownBinaryOperatorsByNames(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>* other
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def * other
        |  MultiplyMethodObject.new(other).call
        |end
        |
        |class MultiplyMethodObject
        |  def initialize(other)
        |    @other = other
        |  end
        |
        |  def call
        |    42
        |  end
        |end
      """)
  }

  @Test
  def replacesWellUnaryOperatorsByNames(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>-@
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def -@
        |  InvertMethodObject.new.call
        |end
        |
        |class InvertMethodObject
        |  def call
        |    42
        |  end
        |end
      """)
  }

  @Test
  def doesNotPerformTheExtractionIfSuperIsUsed(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  super
        |end
      """)

    applyRefactor(ExtractMethodObject)

    assertCodeDidNotChange()
    expectErrorHint(
      new TextRange(9, 14),
      "Cannot perform refactoring if super is called"
    )
  }

  @Test
  def preservesRescueElseAndEnsureBlocks(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  "body"
        |rescue SomeExceptionClass => some_variable
        |  # rescue 1
        |  "rescue"
        |rescue
        |  # rescue 2
        |  "rescue"
        |else
        |  # no exceptions
        |  "no exceptions"
        |ensure
        |  # finally
        |  "finally"
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
        |    "body"
        |  rescue SomeExceptionClass => some_variable
        |    # rescue 1
        |    "rescue"
        |  rescue
        |    # rescue 2
        |    "rescue"
        |  else
        |    # no exceptions
        |    "no exceptions"
        |  ensure
        |    # finally
        |    "finally"
        |  end
        |end
      """)
  }

  @Test
  def preservesRescueBlockWhenTheMethodIsDefinedInsideAClass(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1
        |    "body"
        |  rescue
        |    "rescue"
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
        |  def m1
        |    M1MethodObject.new.call
        |  end
        |
        |  class M1MethodObject
        |    def call
        |      "body"
        |    rescue
        |      "rescue"
        |    end
        |  end
        |end
      """)
  }

  @Test
  def doesNotAddAnExplicitReceiverToObjectPrivateMethods(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  Array(1)
        |  puts "lala"
        |  binding
        |  exit
        |  exit!
        |  abort
        |  raise "error"
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
        |    Array(1)
        |    puts "lala"
        |    binding
        |    exit
        |    exit!
        |    abort
        |    raise "error"
        |  end
        |end
      """)
  }

  @Test
  def choosesNewNameForOriginalReceiverIfTheDefaultIsTaken(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(original_receiver)
        |  m2
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(original_receiver)
        |  M1MethodObject.new(self, original_receiver).call
        |end
        |
        |class M1MethodObject
        |  def initialize(original_receiver_1, original_receiver)
        |    @original_receiver_1 = original_receiver_1
        |    @original_receiver = original_receiver
        |  end
        |
        |  def call
        |    @original_receiver_1.m2
        |  end
        |end
      """)
  }

  @Test
  def keepsChoosingNewNameForOriginalReceiverIfTheDefaultIsTaken(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(original_receiver_1, original_receiver)
        |  m2
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(original_receiver_1, original_receiver)
        |  M1MethodObject.new(self, original_receiver_1, original_receiver).call
        |end
        |
        |class M1MethodObject
        |  def initialize(original_receiver_2, original_receiver_1, original_receiver)
        |    @original_receiver_2 = original_receiver_2
        |    @original_receiver_1 = original_receiver_1
        |    @original_receiver = original_receiver
        |  end
        |
        |  def call
        |    @original_receiver_2.m2
        |  end
        |end
      """)
  }

  @Test
  def choosesNewNameForBlockIfTheDefaultIsTaken(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(block)
        |  yield
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(block, &block_1)
        |  M1MethodObject.new(block).call(&block_1)
        |end
        |
        |class M1MethodObject
        |  def initialize(block)
        |    @block = block
        |  end
        |
        |  def call
        |    yield
        |  end
        |end
      """)
  }

  @Test
  def keepsChoosingNewNameForBlockIfTheDefaultIsTaken(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(block, block_1)
        |  yield
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(block, block_1, &block_2)
        |  M1MethodObject.new(block, block_1).call(&block_2)
        |end
        |
        |class M1MethodObject
        |  def initialize(block, block_1)
        |    @block = block
        |    @block_1 = block_1
        |  end
        |
        |  def call
        |    yield
        |  end
        |end
      """)
  }

  @Test
  def preservesParameterTypes(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1(normal, *list, **hash, &block)
        |  block.call(list << hash)
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def m1(normal, *list, **hash, &block)
        |  M1MethodObject.new(normal, list, hash, block).call
        |end
        |
        |class M1MethodObject
        |  def initialize(normal, list, hash, block)
        |    @normal = normal
        |    @list = list
        |    @hash = hash
        |    @block = block
        |  end
        |
        |  def call
        |    @block.call(@list << @hash)
        |  end
        |end
      """)
  }

  @Test
  def worksForSingletonMethodsDefinedOnSelf(): Unit = {
    loadRubyFileWith(
      """
        |def self.<caret>m1
        |  1 + 1
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def self.m1
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
  def worksForSingletonMethodsDefinedOnObject(): Unit = {
    loadRubyFileWith(
      """
        |def object.<caret>m1
        |  1 + 1
        |  42
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |def object.m1
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
  def detectsAnImplicitReceiverEvenIfItIsInAMessageChain(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1
        |    m2.m3
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
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
        |      @original_receiver.m2.m3
        |    end
        |  end
        |end
      """)
  }

  @Test
  def doesNotChangeSelfReferencesInsideTheParameterList(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1(a = self, b: self)
        |    a
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
        |  def m1(a = self, b: self)
        |    M1MethodObject.new(a, b).call
        |  end
        |
        |  class M1MethodObject
        |    def initialize(a, b)
        |      @a = a
        |      @b = b
        |    end
        |
        |    def call
        |      @a
        |    end
        |  end
        |end
      """)
  }

  @Test
  def doesNotChangeMessageSendsInsideTheParameterList(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1(a = m1, b: m2)
        |    a
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
        |  def m1(a = m1, b: m2)
        |    M1MethodObject.new(a, b).call
        |  end
        |
        |  class M1MethodObject
        |    def initialize(a, b)
        |      @a = a
        |      @b = b
        |    end
        |
        |    def call
        |      @a
        |    end
        |  end
        |end
      """)
  }

  @Test
  def replacesSelfReferencesInsideRescueElseAndEnsureBlocks(): Unit = {
    loadRubyFileWith(
      """
        |def <caret>m1
        |  42
        |rescue
        |  self
        |else
        |  self
        |ensure
        |  self
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
        |    42
        |  rescue
        |    @original_receiver
        |  else
        |    @original_receiver
        |  ensure
        |    @original_receiver
        |  end
        |end
      """)
  }

  @Test
  def worksForEmptyMethodsWithoutParentheses(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
        |  def m1
        |    M1MethodObject.new.call
        |  end
        |
        |  class M1MethodObject
        |    def call
        |
        |    end
        |  end
        |end
      """)
  }

  @Test
  def worksForEmptyMethodsWithParentheses(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1() end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
        |  def m1()
        |    M1MethodObject.new.call
        |  end
        |
        |  class M1MethodObject
        |    def call
        |
        |    end
        |  end
        |end
      """)
  }

  @Test
  def worksForWhitespaceMethodsWithoutParentheses(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
        |  def m1
        |    M1MethodObject.new.call
        |  end
        |
        |  class M1MethodObject
        |    def call
        |
        |    end
        |  end
        |end
      """)
  }

  @Test
  def worksForWhitespaceMethodsWithParentheses(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1()
        |
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    // TODO: Improve formatting - it's strange in this case because of the way the method is parsed:
    //  def m1()[
    //    whitespace
    //  ][methodBody]end
    expectResultingCodeToBe(
      """
        |class X
        |  def m1()
        |
        |    M1MethodObject.new.call
        |  end
        |
        |  class M1MethodObject
        |    def call
        |
        |    end
        |  end
        |end
      """)
  }

  @Test
  def worksForEmptyMethodsWithParenthesesUsingSemicolon(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  def <caret>m1();end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
        |  def m1()
        |    M1MethodObject.new.call
        |  end
        |
        |  class M1MethodObject
        |    def call
        |      ;
        |    end
        |  end
        |end
      """)
  }

  @Test
  def worksForMethodsDefinedInsideACollaboration(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  public def <caret>m1
        |    42
        |  end
        |end
      """)

    applyRefactor(ExtractMethodObject)

    expectResultingCodeToBe(
      """
        |class X
        |  public def m1
        |    M1MethodObject.new.call
        |  end
        |
        |  class M1MethodObject
        |    def call
        |      42
        |    end
        |  end
        |end
      """)
  }

  /*
  @Test
  def asdf(): Unit = {
    loadRubyFileWith(
      """
        |def method_name
        |  object.m1
        |end
      """)
    //loadRubyFileWith("def \nobject.m1\nend")

    val project = insightFixture.getProject
    val file = insightFixture.getFile
    val document = insightFixture.getDocument(file)

    WriteCommandAction
      .writeCommandAction(project)
      .run(() => {
        document.deleteString(4, 15) // remove method_name
        document.replaceString(7, 13, "o") // change object to o
        PsiDocumentManager.getInstance(project).commitDocument(document)
        document.replaceString(4, 4, "method_name") // put method_name again
        PsiDocumentManager.getInstance(project).commitDocument(document)
      })

    loadRubyFileWith(
      """
        |def method_name
        |  o.m1
        |end
      """)
  }
   */
}
