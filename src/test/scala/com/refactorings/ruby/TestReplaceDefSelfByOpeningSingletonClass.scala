package com.refactorings.ruby

import java.util.Collections

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.junit.Assert.assertEquals
import org.junit.{After, Before, Ignore, Test}

import scala.language.reflectiveCalls

class TestReplaceDefSelfByOpeningSingletonClass extends BaseTest {
  private val insightFixture = {
    val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory
    val fixture = fixtureFactory.createLightFixtureBuilder.getFixture
    val tempDirTestFixture = new LightTempDirTestFixtureImpl(true)
    fixtureFactory.createCodeInsightFixture(fixture, tempDirTestFixture)
  }

  @Before
  def setupInsightFixture(): Unit = insightFixture.setUp()

  @After
  def tearDownInsightFixture(): Unit = insightFixture.tearDown()

  type RefactoringDefinition = {
    val optionDescription: String
  }

  @Test
  def opensSingletonClassReplacingSelfDefForMethodsWithNoParameters(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self<caret>.m1
        |    42
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1
        |      42
        |    end
        |  end
        |end
      """)
  }

  @Test
  def opensSingletonClassReplacingSelfDefForMethodsWithParameters(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self<caret>.m1(x, *xs, &proc)
        |    42
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1(x, *xs, &proc)
        |      42
        |    end
        |  end
        |end
      """)
  }

  @Test
  def preservesParenthesesOfOriginalMethodParameters(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self<caret>.m1()
        |    42
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1()
        |      42
        |    end
        |  end
        |end
      """)
  }

  @Test
  def opensSingletonClassReplacingSelfDefForMethodsWithParametersButWithoutParentheses(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self<caret>.m1 x, *xs, &proc
        |    42
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1 x, *xs, &proc
        |      42
        |    end
        |  end
        |end
      """)
  }

  @Test
  def isNotAvailableWhenTheMethodIsDefinedNormally(): Unit = {
    loadFileWith(
      """
        |class X
        |  def <caret>m1()
        |    42
        |  end
        |end
      """)

    assertRefactorNotAvailable(ReplaceDefSelfByOpeningSingletonClass)
  }

  @Test
  def isNotAvailableWhenTheMethodIsDefinedNormallyEvenWhenItIsInsideASingletonMethod(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self.m1
        |    def <caret>m1()
        |      42
        |    end
        |  end
        |end
      """)

    assertRefactorNotAvailable(ReplaceDefSelfByOpeningSingletonClass)
  }

  @Test
  def isAvailableWhenTheCaretIsInsideTheMethodName(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self.m<caret>1
        |    42
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1
        |      42
        |    end
        |  end
        |end
      """)
  }

  @Test
  def isAvailableWhenTheCaretIsInsideTheObjectName(): Unit = {
    loadFileWith(
      """
        |class X
        |  def se<caret>lf.m1
        |    42
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1
        |      42
        |    end
        |  end
        |end
      """)
  }

  @Test
  def reusesTheObjectInWhichTheMethodIsDefined(): Unit = {
    loadFileWith(
      """
        |object = Object.new
        |
        |def object<caret>.m1
        |  42
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |object = Object.new
        |
        |class << object
        |  def m1
        |    42
        |  end
        |end
      """)
  }

  @Test
  def preservesFormattingForMultilineMethods(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self<caret>.m1
        |    puts "hola"
        |     puts "mundo"
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1
        |      puts "hola"
        |       puts "mundo"
        |    end
        |  end
        |end
      """)
  }

  @Test
  def preservesFormattingForMultilineMethodsWithMultilineExpressions(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self<caret>.m1
        |    m2(
        |      "lala"
        |    )
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1
        |      m2(
        |        "lala"
        |      )
        |    end
        |  end
        |end
      """)
  }

  @Test
  def preservesRescueElseAndEnsureBlocks(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self<caret>.m1
        |    puts "body"
        |  rescue SomeExceptionClass => some_variable
        |    # rescue 1
        |    puts "rescue"
        |  rescue
        |    # rescue 2
        |    puts "rescue"
        |  else
        |    # no exceptions
        |    puts "no exceptions"
        |  ensure
        |    # finally
        |    puts "finally"
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def m1
        |      puts "body"
        |    rescue SomeExceptionClass => some_variable
        |      # rescue 1
        |      puts "rescue"
        |    rescue
        |      # rescue 2
        |      puts "rescue"
        |    else
        |      # no exceptions
        |      puts "no exceptions"
        |    ensure
        |      # finally
        |      puts "finally"
        |    end
        |  end
        |end
      """)
  }

  @Test
  def canRefactorMethodsWithSymbolNames(): Unit = {
    loadFileWith(
      """
        |class X
        |  def self<caret>.[](x)
        |    x
        |  end
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |class X
        |  class << self
        |    def [](x)
        |      x
        |    end
        |  end
        |end
      """)
  }


  @Test
  def doesNotMergeSingletonClassesByDefault(): Unit = {
    loadFileWith(
      """
        |object = Object.new
        |
        |class << object
        |  def m0
        |    "lala"
        |  end
        |end
        |
        |def object<caret>.m1
        |  42
        |end
      """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |object = Object.new
        |
        |class << object
        |  def m0
        |    "lala"
        |  end
        |end
        |
        |class << object
        |  def m1
        |    42
        |  end
        |end
      """)
  }

  @Test
  @ForFeature(FeatureFlag.MergeSingletonClasses)
  def mergesTheClassBlockIfTheClassWasOpenedJustBefore(): Unit = {
    loadFileWith(
      """
        |object = Object.new
        |
        |class << object
        |  def m0
        |    "lala"
        |  end
        |end
        |
        |def object<caret>.m1
        |  42
        |end
    """)

    applyRefactor(ReplaceDefSelfByOpeningSingletonClass)

    expectResultingCodeToBe(
      """
        |object = Object.new
        |
        |class << object
        |  def m0
        |    "lala"
        |  end
        |
        |  def m1
        |    42
        |  end
        |end
    """)
  }

  def loadFileWith(codeToLoad: String) = {
    insightFixture.configureByText(
      RubyFileType.RUBY,
      codeToLoad.trim.stripMargin + "\n"
    )
  }

  def applyRefactor(refactorToApply: RefactoringDefinition): Unit = {
    insightFixture.launchAction(
      insightFixture.findSingleIntention(refactorToApply.optionDescription)
    )
  }

  def assertRefactorNotAvailable(refactorToApply: RefactoringDefinition): Unit = {
    assertEquals(
      Collections.emptyList,
      insightFixture.filterAvailableIntentions(refactorToApply.optionDescription)
    )
  }

  def expectResultingCodeToBe(expectedCode: String): Unit = {
    insightFixture.checkResult(
      expectedCode.trim.stripMargin + "\n"
    )
  }
}
