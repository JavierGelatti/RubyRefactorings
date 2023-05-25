package com.refactorings.ruby.services

import com.intellij.diagnostic.AbstractMessage
import com.intellij.ide.DataManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.{ApplicationInfo, ApplicationManager}
import com.intellij.openapi.diagnostic.SubmittedReportInfo.SubmissionStatus._
import com.intellij.openapi.diagnostic.{ErrorReportSubmitter, IdeaLoggingEvent, SubmittedReportInfo}
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import com.refactorings.ruby._
import com.refactorings.ruby.plugin.{Environment, RubyRefactorings}
import io.sentry
import io.sentry._
import io.sentry.protocol._
import org.jetbrains.annotations.VisibleForTesting

import java.awt.Component
import java.security.MessageDigest
import java.util.UUID

class ErrorSubmitter extends ErrorReportSubmitter {
  override def getReportActionText: String = "Report To Plugin Author"

  override def submit(
    events: Array[IdeaLoggingEvent],
    additionalInfo: String,
    parentComponent: Component,
    consumer: Consumer[_ >: SubmittedReportInfo]
  ): Boolean = {
    // Assuming there's always going to be a single event is valid, since events are reported one by one. See:
    // https://github.com/JetBrains/intellij-community/blob/ed032a9767471007a491952f6bba0b2cc2234076/platform/platform-impl/src/com/intellij/diagnostic/IdeErrorsDialog.java#L625
    // That implementation might change in the future, but for now this is good enough.
    val event = events.head

    val project = getProjectFrom(parentComponent)
    new Task.Backgroundable(project, "Sending error report") {
      override def run(indicator: ProgressIndicator): Unit = {
        ErrorSubmitter.initialize()

        val result = new ErrorSubmissionTask(event, additionalInfo).run()

        invokeLater {
          if (result.isSuccessful) {
            showSuccessMessage(parentComponent)
          } else {
            showErrorMessage(parentComponent)
          }
          consumer.consume(result)
        }
      }
    }.queue()

    true
  }

  private def showSuccessMessage(parentComponent: Component): Unit = {
    Messages.showInfoMessage(
      parentComponent,
      "Thank you for submitting your report!",
      "Error Report Was Sent"
    )
  }

  private def showErrorMessage(parentComponent: Component): Unit = {
    Messages.showErrorDialog(
      parentComponent,
      "Unfortunately, we were not able to send the error report :(",
      "Error Report Error"
    )
  }

  private def getProjectFrom(parentComponent: Component) = {
    val context = DataManager.getInstance().getDataContext(parentComponent)
    CommonDataKeys.PROJECT.getData(context)
  }

  private def invokeLater(toRun: => Unit): Unit = {
    ApplicationManager.getApplication.invokeLater(() => toRun)
  }

  implicit class SubmittedReportInfoExtension(reportInfo: SubmittedReportInfo) {
    def isSuccessful: Boolean = reportInfo.getStatus == NEW_ISSUE
  }
}

class ErrorSubmissionTask(
  event: IdeaLoggingEvent,
  additionalInfo: String
) {

  def run(): SubmittedReportInfo = {
    val eventWasCaptured = captureEvent(event)

    new SubmittedReportInfo(
      if (eventWasCaptured) NEW_ISSUE else FAILED
    )
  }

  private def captureEvent(ideEvent: IdeaLoggingEvent): Boolean = {
    var eventId = SentryId.EMPTY_ID

    Sentry.withScope(scope => {
      attachmentsFrom(ideEvent).foreach(scope.addAttachment)

      eventId = Sentry.captureEvent(eventFor(thrownExceptionFrom(ideEvent)))
    })

    !SentryId.EMPTY_ID.equals(eventId)
  }

  private def attachmentsFrom(ideEvent: IdeaLoggingEvent): List[sentry.Attachment] = ideEvent.getData match {
    case message: AbstractMessage => message.getIncludedAttachments.toList.map { attachment =>
      new sentry.Attachment(attachment.getBytes, attachment.getPath)
    }
    case _ => List()
  }

  private def thrownExceptionFrom(ideEvent: IdeaLoggingEvent) = ideEvent.getData match {
    case message: AbstractMessage => message.getThrowable
    case _ => new RuntimeException(
      s"Could not obtain throwable from ${ideEvent.getClass}.\n\nThrowable text was: \"${ideEvent.getThrowableText}\""
    )
  }

  private def eventFor(throwable: Throwable) = {
    val event = new SentryEvent()

    event.setLevel(SentryLevel.ERROR)
    event.setRelease(RubyRefactorings.pluginVersion)
    event.setEnvironment(Environment.current.name)
    event.setThrowable(throwable)

    if (additionalInfo.isNotEmptyOrSpaces) {
      event.setMessage(messageWith(additionalInfo))
      event.setTag("with_description", "true")
    }

    event
  }

  private def messageWith(info: String) = {
    val message = new Message()
    message.setMessage(info)
    message
  }
}

object ErrorSubmitter {
  private val USER_ID_KEY = s"${RubyRefactorings.pluginName}.userId"
  // See https://docs.sentry.io/product/sentry-basics/dsn-explainer/
  private val SENTRY_DSN = "https://61ef3be9b6604fdfa07f009ff0d589cf@o1013534.ingest.sentry.io/5978882"

  @volatile private var sentryAlreadyInitialized = false

  private var customTransportFactory: Option[ITransportFactory] = None

  @VisibleForTesting
  def setTransportFactory(transportFactory: ITransportFactory): Unit = {
    customTransportFactory = Some(transportFactory)
  }

  private def initialize(): Unit = this.synchronized {
    if (sentryAlreadyInitialized) return

    Sentry.init((options: SentryOptions) => {
      options.setDsn(SENTRY_DSN)
      options.setDebug(false)
      options.setEnableUncaughtExceptionHandler(false)
      options.setEnableDeduplication(false)
      options.setBeforeSend((event, _) => {
        // Clear server name to avoid tracking personal data
        event.setServerName(null)

        event.getContexts.setOperatingSystem(currentOperatingSystem)
        event.getContexts.setRuntime(currentRuntime)

        augmentFramesOf(event)

        event
      })
      customTransportFactory.foreach(options.setTransportFactory)
    })

    Sentry.configureScope(scope => {
      scope.setTag("java_vendor", SystemInfo.JAVA_VENDOR)
      scope.setTag("java_version", SystemInfo.JAVA_VERSION)
      scope.setUser(currentUser)
    })

    sentryAlreadyInitialized = true
  }

  def reset(): Unit = this.synchronized {
    sentryAlreadyInitialized = false
    initialize()
  }

  private def augmentFramesOf(event: SentryEvent): Unit = {
    event.getExceptions
      .map(_.getStacktrace)
      .flatMap(_.getFrames)
      .foreach { frame =>
        if (frame.getModule.startsWith("com.refactorings.ruby")) {
          frame.setInApp(true)
          frame.setFilename(githubLinkFor(frame))
        } else {
          frame.setInApp(false)
        }
      }

      def githubLinkFor(frame: SentryStackFrame) = {
        val repoUrl = "https://github.com/JavierGelatti/RubyRefactorings/blob"
        val reference = if (event.getEnvironment == "production") s"v${event.getRelease}" else "main"
        val packageFolder = frame.getModule.split('.').dropRight(1).mkString("/")
        val filename = frame.getFilename
        val lineNumber = frame.getLineno

        s"${repoUrl}/${reference}/src/main/scala/${packageFolder}/${filename}#L${lineNumber}"
      }
  }

  private def currentUser: User = {
    val user = new User()
    user.setId(currentUserId)
    user
  }

  private lazy val currentUserId: String = {
    val properties = PropertiesComponent.getInstance()
    if (!properties.isValueSet(USER_ID_KEY)) {
      properties.setValue(
        USER_ID_KEY,
        sha256(UUID.randomUUID().toString).take(8)
      )
    }
    properties.getValue(USER_ID_KEY)
  }

  private def sha256(valueToHash: String) =
    MessageDigest.getInstance("SHA-256")
      .digest(valueToHash.getBytes("UTF-8"))
      .map("%02x".format(_))
      .mkString

  private lazy val currentRuntime = {
    val applicationInfo = ApplicationInfo.getInstance()
    val build = applicationInfo.getBuild
    val runtime = new SentryRuntime()
    runtime.setName(build.getProductCode)
    runtime.setVersion(
      if (".*(EAP|Beta|Preview|RC).*".r.matches(applicationInfo.getFullVersion)) {
        build.toString.replaceAll("^" + build.getProductCode + "-", "")
      } else {
        applicationInfo.getFullVersion
      }
    )
    runtime
  }

  private lazy val currentOperatingSystem = {
    val os = new OperatingSystem()
    os.setName(SystemInfo.OS_NAME)
    os.setVersion(SystemInfo.OS_VERSION + "-" + SystemInfo.OS_ARCH)
    os
  }
}