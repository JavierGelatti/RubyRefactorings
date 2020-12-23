package com.refactorings.ruby

import com.intellij.codeInsight.intention.{IntentionAction, IntentionActionDelegate, IntentionManager}
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.refactorings.ruby.ui.UI
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.junit.Assert.assertEquals
import org.junit.{After, Before}

import java.util.Collections
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.BufferHasAsJava

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

  type Hint = (TextRange, String)
  private val errorHints = new ListBuffer[Hint]

  @Before
  def setupFakeUI(): Unit = UI.setImplementation(
    (textRange: TextRange, _: Editor, messageText: String) => errorHints.addOne((textRange, messageText))
  )

  def expectErrorHint(textRange: TextRange, messageText: String) = {
    assertEquals(
      List((textRange, messageText)),
      errorHints.toList
    )
  }

  protected def activateIntention(intentionToActivate: IntentionAction): Unit = {
    IntentionManager.getInstance().addAction(intentionToActivate)
  }

  protected var loadedCode: String = _

  protected def loadRubyFileWith(codeToLoad: String): PsiFile = {
    loadedCode = codeToLoad
    insightFixture.configureByText(
      RubyFileType.RUBY,
      codeToLoad.trim.stripMargin + "\n"
    )
  }

  protected type RefactoringDefinition = RefactoringIntentionCompanionObject

  protected def applyRefactor(refactorToApply: RefactoringDefinition): Unit = {
    val intentionActions = intentionActionsFor(refactorToApply)
    assertEquals(
      s"The refactoring ${refactorToApply.getClass.getSimpleName} was not available!",
      1, intentionActions.length
    )

    insightFixture.launchAction(intentionActions.head)
  }

  protected def enableTemplates(): Unit =
    TemplateManagerImpl.setTemplateTesting(insightFixture.getTestRootDisposable)

  protected def simulateTyping(text: String): Unit =
    insightFixture.`type`(text)

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

  protected def assertCodeDidNotChange(): Unit = {
    expectResultingCodeToBe(loadedCode)
  }
}
