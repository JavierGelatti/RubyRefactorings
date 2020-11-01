package com.refactorings.ruby

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.junit.{After, Before, Test}

class TestSurroundInParentheses {
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
    insightFixture.configureByText(RubyFileType.RUBY, "x<caret>\n")
    val surround = insightFixture.findSingleIntention("Surround in parentheses")
    insightFixture.launchAction(surround)
    insightFixture.checkResult("(x)<caret>\n")
  }
}