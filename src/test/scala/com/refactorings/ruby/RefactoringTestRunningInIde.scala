package com.refactorings.ruby

import com.intellij.codeInsight.intention.{IntentionActionDelegate, IntentionManager}
import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl
import com.refactorings.ruby.plugin.RubyRefactorings
import com.refactorings.ruby.ui.{SelectionOption, UI}
import org.jetbrains.plugins.ruby.ruby.lang.RubyFileType
import org.junit.Assert.{assertEquals, assertTrue}
import org.junit.{After, Before}

import java.util.Collections
import scala.collection.mutable.ListBuffer
import scala.jdk.CollectionConverters.SeqHasAsJava

abstract class RefactoringTestRunningInIde {
  private val insightFixture = {
    val fixtureFactory = IdeaTestFixtureFactory.getFixtureFactory
    val fixture = fixtureFactory.createLightFixtureBuilder.getFixture
    val tempDirTestFixture = new LightTempDirTestFixtureImpl(true)
    fixtureFactory.createCodeInsightFixture(fixture, tempDirTestFixture)
  }

  @Before
  def setupInsightFixture(): Unit = {
    insightFixture.setUp()
    assertTrue(
      s"""The plugin was not enabled!
         |${PluginManagerCore.getShortLoadingErrorMessage(RubyRefactorings.pluginDescriptor)}
      """.stripMargin,
      RubyRefactorings.isEnabled
    )
  }

  @After
  def tearDownInsightFixture(): Unit = insightFixture.tearDown()

  type Hint = (TextRange, String)
  private val errorHints = new ListBuffer[Hint]

  private case class OptionChooser[T <: SelectionOption](title: String, options: Seq[T], callback: T => Unit) {
    def optionsTextAndRanges: List[(String, TextRange)] =
      options
        .map { option => (option.optionText, option.textRange) }
        .toList

    def chooseOptionTitled(optionTitle: String): Unit = {
      val optionToChoose = options.find(_.optionText == optionTitle)

      assertTrue("Couldn't find the option '" + optionTitle + "'", optionToChoose.isDefined)

      callback(optionToChoose.get)
    }
  }

  private val optionChoosers = new ListBuffer[OptionChooser[_ <: SelectionOption]]

  @Before
  def setupFakeUI(): Unit = UI.setImplementation(
    new UI {
      override def showErrorHint(textRange: TextRange, editor: Editor, messageText: String): Unit = {
        require(editor != null)
        errorHints.addOne((textRange, messageText))
      }

      override def showOptionsMenuWith[ConcreteOption <: SelectionOption]
      (title: String, options: Seq[ConcreteOption], editor: Editor, callback: ConcreteOption => Unit): Unit = {
        require(editor != null)
        require(options.nonEmpty)

        optionChoosers.addOne(OptionChooser(title, options, callback))
      }
    }
  )

  protected def expectErrorHint(textRange: TextRange, messageText: String): Unit = {
    assertEquals(
      List((textRange, messageText)),
      errorHints.toList
    )
  }

  protected def expectOptions(expectedTitle: String, expectedOptions: List[(String, TextRange)]): Unit = {
    assertEquals(1, optionChoosers.size)

    val optionChooser = optionChoosers.head
    assertEquals(expectedTitle, optionChooser.title)
    assertEquals(expectedOptions, optionChooser.optionsTextAndRanges)
  }

  protected def chooseOptionNamed(optionTitle: String): Unit = {
    assertEquals(1, optionChoosers.size)

    val optionChooser = optionChoosers.head
    optionChooser.chooseOptionTitled(optionTitle)
    optionChoosers.subtractOne(optionChooser)
  }

  protected def ensureIntentionIsRegistered(intentionToRegister: RefactoringIntention): Unit = {
    val intentionManager = IntentionManager.getInstance()
    val availableIntentions = intentionManager.getAvailableIntentions
    if (availableIntentions.forall(_.getClass != intentionToRegister.getClass)) {
      intentionManager.addAction(intentionToRegister)
    }
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
    val refactoringClassName = refactorToApply.getClass.getSimpleName
    assertTrue(
      s"The refactoring $refactoringClassName was not available!",
      intentionActions.nonEmpty
    )
    assertEquals(
      s"There was more than one refactoring available for $refactoringClassName!",
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

  protected def assertRefactorIsAvailable(refactorToApply: RefactoringDefinition): Unit = {
    assertEquals(1, intentionActionsFor(refactorToApply).size)
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
