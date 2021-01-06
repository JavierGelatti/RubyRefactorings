package com.refactorings.ruby

import org.junit.{Before, Test}

class TestInlineStruct extends RefactoringTestRunningInIde {
  @Before
  def activateRefactoring(): Unit = activateIntention(new InlineStruct)

  @Test
  def isNotAvailableWhenSuperclassIsNotStruct(): Unit = {
    loadRubyFileWith(
      """
        |class X < <caret>MyClass.new(:a)
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }

  @Test
  def isNotAvailableWhenStructIsNotCreatedWithNew(): Unit = {
    loadRubyFileWith(
      """
        |class X < <caret>Struct.something(:a)
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }

  @Test
  def isNotAvailableWhenTheCaretIsOutsideTheClassDefinition(): Unit = {
    loadRubyFileWith(
      """
        |<caret>
        |class X < Struct.new(:a)
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }

  @Test
  def isNotAvailableWhenStructCreationDoesNotHaveArguments(): Unit = {
    loadRubyFileWith(
      """
        |class X < <caret>Struct.new()
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }

  @Test
  def isNotAvailableWhenStructCreationIsInsideAClassWithoutExplicitSuperclass(): Unit = {
    loadRubyFileWith(
      """
        |class X
        |  <caret>Struct.new(:a)
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }

  @Test
  def isNotAvailableWhenCaretIsFocusingAStructCreationThatIsNotInTheSuperclass(): Unit = {
    loadRubyFileWith(
      """
        |class X < Struct.new(:a)
        |  <caret>Struct.new(:a)
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }

  @Test
  def isNotAvailableWhenCaretIsFocusingAStructCreationThatIsInsideButItIsNotTheSuperclass(): Unit = {
    loadRubyFileWith(
      """
        |class X < MyClass.new(<caret>Struct.new(:a))
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }

  @Test
  def isNotAvailableWhenTheStructArgumentsAreNotLiteralSymbols(): Unit = {
    loadRubyFileWith(
      """
        |class X < <caret>Struct.new(:a, "b".to_sym)
        |end
      """)

    assertRefactorNotAvailable(InlineStruct)
  }

  @Test
  def isAvailableWhenCaretIsInsideStructCreationArguments(): Unit = {
    loadRubyFileWith(
      """
        |class X < Struct.new(:a, :<caret>b)
        |end
      """)

    assertRefactorIsAvailable(InlineStruct)
  }

  @Test
  def removesTheSuperclassEvenIfThereAreMultipleSpacesBetweenTheClasses(): Unit = {
    loadRubyFileWith(
      """
        |class X     <
        |
        |  <caret>Struct.new(:a)
        |end
      """)

    applyRefactor(InlineStruct)

    expectResultingCodeToBe(
      """
        |class X
        |  attr_accessor :a
        |
        |  def initialize(a)
        |    @a = a
        |  end
        |end
      """)
  }

  @Test
  def definesAccessorsAndConstructorForSingleStructMembers(): Unit = {
    loadRubyFileWith(
      """
        |class X < <caret>Struct.new(:a)
        |end
      """)

    applyRefactor(InlineStruct)

    expectResultingCodeToBe(
      """
        |class X
        |  attr_accessor :a
        |
        |  def initialize(a)
        |    @a = a
        |  end
        |end
      """)
  }

  @Test
  def definesAccessorsAndConstructorForManyStructMembers(): Unit = {
    loadRubyFileWith(
      """
        |class X < <caret>Struct.new(:a, :b)
        |end
      """)

    applyRefactor(InlineStruct)

    expectResultingCodeToBe(
      """
        |class X
        |  attr_accessor :a, :b
        |
        |  def initialize(a, b)
        |    @a = a
        |    @b = b
        |  end
        |end
      """)
  }

  @Test
  def prependsTheNewContentAtTheBeginningOfTheClassDefinition(): Unit = {
    loadRubyFileWith(
      """
        |class X < <caret>Struct.new(:a, :b)
        |  def m1
        |    42
        |  end
        |end
      """)

    applyRefactor(InlineStruct)

    expectResultingCodeToBe(
      """
        |class X
        |  attr_accessor :a, :b
        |
        |  def initialize(a, b)
        |    @a = a
        |    @b = b
        |  end
        |
        |  def m1
        |    42
        |  end
        |end
      """)
  }
}
