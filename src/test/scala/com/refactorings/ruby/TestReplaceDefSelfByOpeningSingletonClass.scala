package com.refactorings.ruby

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.junit.{After, Before, Test}

class TestReplaceDefSelfByOpeningSingletonClass {
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

  @Test
  def test01() = {
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

  def loadFileWith(codeToLoad: String) = {
    insightFixture.configureByText(
      RubyFileType.RUBY,
      codeToLoad.trim.stripMargin + "\n"
    )
  }

  def applyRefactor(refactorToApply: {val optionDescription: String}): Unit = {
    insightFixture.launchAction(
      insightFixture.findSingleIntention(refactorToApply.optionDescription)
    )
  }

  def expectResultingCodeToBe(expectedCode: String): Unit = {
    insightFixture.checkResult(
      expectedCode.trim.stripMargin + "\n"
    )
  }
}
