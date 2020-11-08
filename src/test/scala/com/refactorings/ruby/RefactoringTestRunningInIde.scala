package com.refactorings.ruby

import java.util.Collections

import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.junit.Assert.assertEquals
import org.junit.{After, Before}

abstract class RefactoringTestRunningInIde {
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

  protected def loadFileWith(codeToLoad: String) = {
    insightFixture.configureByText(
      RubyFileType.RUBY,
      codeToLoad.trim.stripMargin + "\n"
    )
  }

  protected type RefactoringDefinition = RefactoringIntentionCompanionObject

  protected def applyRefactor(refactorToApply: RefactoringDefinition): Unit = {
    insightFixture.launchAction(
      insightFixture.findSingleIntention(refactorToApply.optionDescription)
    )
  }

  protected def assertRefactorNotAvailable(refactorToApply: RefactoringDefinition): Unit = {
    assertEquals(
      Collections.emptyList,
      insightFixture.filterAvailableIntentions(refactorToApply.optionDescription)
    )
  }

  protected def expectResultingCodeToBe(expectedCode: String): Unit = {
    insightFixture.checkResult(
      expectedCode.trim.stripMargin + "\n"
    )
  }
}
