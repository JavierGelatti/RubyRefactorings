package com.refactorings.ruby

import java.util.Collections

import com.intellij.codeInsight.intention.IntentionActionDelegate
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.junit.Assert.{assertEquals, assertNotEquals}
import org.junit.{After, Before}

import scala.collection.JavaConverters.bufferAsJavaListConverter

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

  protected def loadRubyFileWith(codeToLoad: String): PsiFile = {
    insightFixture.configureByText(
      RubyFileType.RUBY,
      codeToLoad.trim.stripMargin + "\n"
    )
  }

  protected type RefactoringDefinition = RefactoringIntentionCompanionObject

  protected def applyRefactor(refactorToApply: RefactoringDefinition): Unit = {
    val intentionActions = intentionActionsFor(refactorToApply)
    assertEquals("The refactoring was not available!", 1, intentionActions.length)

    insightFixture.launchAction(intentionActions.head)
  }

  protected def assertRefactorNotAvailable(refactorToApply: RefactoringDefinition): Unit = {
    assertEquals(Collections.emptyList, intentionActionsFor(refactorToApply).asJava)
  }

  private def intentionActionsFor(refactorToApply: RefactoringDefinition) = {
    insightFixture
      .filterAvailableIntentions(refactorToApply.optionDescription)
      .map(IntentionActionDelegate.unwrap)
      .filter(intentionAction => classOf[RefactoringIntention].isAssignableFrom(intentionAction.getClass))
  }

  protected def expectResultingCodeToBe(expectedCode: String): Unit = {
    insightFixture.checkResult(
      expectedCode.trim.stripMargin + "\n"
    )
  }
}
