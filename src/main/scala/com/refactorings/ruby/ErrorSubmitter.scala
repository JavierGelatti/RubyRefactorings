package com.refactorings.ruby

import com.intellij.diagnostic.AbstractMessage
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.internal.statistic.DeviceIdManager
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.{ApplicationInfo, ApplicationManager}
import com.intellij.openapi.diagnostic.{ErrorReportSubmitter, IdeaLoggingEvent, SubmittedReportInfo}
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.{ProgressIndicator, Task}
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.Consumer
import io.sentry
import io.sentry.protocol._
import io.sentry.{Sentry, SentryEvent, SentryLevel, SentryOptions}

import java.awt.Component
import java.security.MessageDigest
import scala.util.Try

class ErrorSubmitter extends ErrorReportSubmitter {
  override def getReportActionText: String = "Report to plugin author"

  override def submit(
                       events: Array[IdeaLoggingEvent],
                       additionalInfo: String,
                       parentComponent: Component,
                       consumer: Consumer[_ >: SubmittedReportInfo]
                     ): Boolean
  = {
    // Assuming there's always going to be a single event is valid, since events are reported one by one. See:
    // https://github.com/JetBrains/intellij-community/blob/ed032a9767471007a491952f6bba0b2cc2234076/platform/platform-impl/src/com/intellij/diagnostic/IdeErrorsDialog.java#L625
    // That implementation might change in the future, but for now this is good enough.
    val event = events.head

    new ErrorSubmissionTask(
      event,
      additionalInfo,
      parentComponent,
      getProjectFrom(parentComponent),
      getPluginDescriptor,
      consumer
    ).queue()

    true
  }

  private def getProjectFrom(parentComponent: Component) = {
    val context = DataManager.getInstance().getDataContext(parentComponent)
    CommonDataKeys.PROJECT.getData(context)
  }
}

private class ErrorSubmissionTask(
  event: IdeaLoggingEvent,
  additionalInfo: String,
  parentComponent: Component,
  project: Project,
  pluginDescriptor: PluginDescriptor,
  consumer: Consumer[_ >: SubmittedReportInfo]
) extends Task.Backgroundable(project, "Sending error report") {
  override def run(indicator: ProgressIndicator): Unit = {
    ErrorSubmitter.initialize()

    val eventWasCaptured = captureEvent(event)

    ApplicationManager.getApplication.invokeLater(() =>
      if (eventWasCaptured) {
        showSuccessMessage()
      } else {
        showFailureMessage()
      }
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
    case anythingElse => new RuntimeException(
      s"Could not obtain throwable from ${anythingElse.getClass}.\n\nThrowable text was: ${ideEvent.getThrowableText}"
    )
  }

  private def eventFor(throwable: Throwable) = {
    val event = new SentryEvent()

    event.setLevel(SentryLevel.ERROR)
    event.setRelease(pluginVersion)
    event.setEnvironment(currentEnvironment)
    event.setThrowable(throwable)

    if (additionalInfo.isNotEmptyOrSpaces) {
      event.setMessage(messageWith(additionalInfo))
      event.setTag("with-description", "true")
    }

    event
  }

  private def messageWith(info: String) = {
    val message = new Message()
    message.setMessage(info)
    message
  }

  private def showSuccessMessage(): Unit = {
    Messages.showMessageDialog(parentComponent, "Thank you for submitting your report!", "Error Report Was Sent", AllIcons.General.SuccessDialog)
    consumer.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.NEW_ISSUE))
  }

  private def showFailureMessage(): Unit = {
    Messages.showErrorDialog(parentComponent, "Unfortunately, we were not able to send the error report :(", "Error Report Error")
    consumer.consume(new SubmittedReportInfo(SubmittedReportInfo.SubmissionStatus.FAILED))
  }

  private lazy val currentEnvironment = {
    val inStagingEnvironment = pluginVersion == "0.1" || pluginVersion.split(".").length > 3
    if (inStagingEnvironment) "staging" else "production"
  }

  private lazy val pluginVersion = pluginDescriptor.getVersion
}

object ErrorSubmitter {
  // See https://docs.sentry.io/product/sentry-basics/dsn-explainer/
  private val SENTRY_DSN = "https://61ef3be9b6604fdfa07f009ff0d589cf@o1013534.ingest.sentry.io/5978882"

  @volatile private var sentryAlreadyInitialized = false

  def initialize(): Unit = this.synchronized {
    if (sentryAlreadyInitialized) return

    Sentry.init((options: SentryOptions) => {
      options.setDsn(SENTRY_DSN)
      options.setDebug(false)
      options.setEnableUncaughtExceptionHandler(false)
      options.setBeforeSend((event, _) => {
        // Clear server name to avoid tracking personal data
        event.setServerName(null)

        event.getContexts.setOperatingSystem(currentOperatingSystem)
        event.getContexts.setRuntime(currentRuntime)

        event
      })
    })

    Sentry.configureScope(scope => {
      scope.setTag("java_vendor", SystemInfo.JAVA_VENDOR)
      scope.setTag("java_version", SystemInfo.JAVA_VERSION)
      currentUser.foreach(scope.setUser)
    })

    sentryAlreadyInitialized = true
  }

  private lazy val currentUser: Option[User] = Try({
    val user = new User()
    user.setId(currentUserId)
    user
  }).toOption

  private def currentUserId = MessageDigest.getInstance("SHA-256")
    .digest(deviceId.getBytes("UTF-8"))
    .map("%02x".format(_))
    .mkString
    .take(8)

  // Note: this method uses an unstable API, and can throw InvalidDeviceIdTokenException
  private def deviceId = DeviceIdManager.getOrGenerateId(
    new DeviceIdManager.DeviceIdToken() {},
    "com.refactorings.ruby.ErrorSubmitter"
  )

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