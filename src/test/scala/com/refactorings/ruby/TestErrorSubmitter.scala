package com.refactorings.ruby

import com.intellij.diagnostic.{AbstractMessage, IdeaReportingEvent}
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.SubmittedReportInfo.SubmissionStatus
import com.intellij.openapi.diagnostic.{Attachment, IdeaLoggingEvent, SubmittedReportInfo}
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.ExceptionUtil
import io.sentry.protocol.SentryId
import io.sentry.transport.ITransport
import io.sentry.{Sentry, SentryEnvelope}
import org.json4s.native.JsonParser
import org.json4s.{JArray, JObject, JValue}
import org.junit.Assert.{assertEquals, assertNotEquals, assertTrue}
import org.junit.{Before, Test}

import java.util
import scala.jdk.CollectionConverters.SeqHasAsJava

class TestErrorSubmitter extends RefactoringTestRunningInIde {
  private val transport = new InMemorySentryTransport
  private lazy val pluginDescriptor = PluginManagerCore.getPlugin(PluginId.findId("com.refactorings.ruby.RubyRefactorings"))

  @Before
  def installFakeTransportImplementation(): Unit = {
    ErrorSubmitter.setTransportFactory((_, _) => transport)
    ErrorSubmitter.reset()
  }

  @Test
  def successfullyReportExceptionWithoutUserMessage(): Unit = {
    val exceptionToReport = new RuntimeException
    val userMessage = null

    val submittedReportInfo = submitError(userMessage, reportingEventFor(exceptionToReport))

    assertEquals(SubmissionStatus.NEW_ISSUE, submittedReportInfo.getStatus)

    val List(reportedError: JObject) = transport.sentData

    assertErrorMetadataIsReportedAsPartOf(reportedError)
    assertExceptionIsReportedAsPartOf(exceptionToReport, reportedError)
    assertUserMessageIsNotReportedAsPartOf(reportedError)
  }

  @Test
  def successfullyReportExceptionWithEmptyUserMessage(): Unit = {
    val exceptionToReport = new RuntimeException
    val userMessage = "   "

    val submittedReportInfo = submitError(userMessage, reportingEventFor(exceptionToReport))

    assertEquals(SubmissionStatus.NEW_ISSUE, submittedReportInfo.getStatus)

    val List(reportedError: JObject) = transport.sentData

    assertErrorMetadataIsReportedAsPartOf(reportedError)
    assertExceptionIsReportedAsPartOf(exceptionToReport, reportedError)
    assertUserMessageIsNotReportedAsPartOf(reportedError)
  }

  @Test
  def successfullyReportExceptionWithUserMessage(): Unit = {
    val exceptionToReport = new RuntimeException
    val userMessage = "A message from a kind user"

    val submittedReportInfo = submitError(userMessage, reportingEventFor(exceptionToReport))

    assertEquals(SubmissionStatus.NEW_ISSUE, submittedReportInfo.getStatus)

    val List(reportedError: JObject) = transport.sentData

    assertErrorMetadataIsReportedAsPartOf(reportedError)
    assertExceptionIsReportedAsPartOf(exceptionToReport, reportedError)
    assertUserMessageIsReportedAsPartOf(userMessage, reportedError)
  }

  @Test
  def successfullySendsReportEvenWhenTheEventDataIsGenericOrNotPresent(): Unit = {
    val userMessage = "A message from a kind user"

    val submittedReportInfo = submitError(
      userMessage,
      new IdeaLoggingEvent(userMessage, null)
    )

    assertEquals(SubmissionStatus.NEW_ISSUE, submittedReportInfo.getStatus)

    val List(reportedError: JObject) = transport.sentData

    assertErrorMetadataIsReportedAsPartOf(reportedError)
    val reportedException = (reportedError \ "exception" \ "values").apply(0)
    assertValueEquals(
      "Could not obtain throwable from class com.intellij.openapi.diagnostic.IdeaLoggingEvent.\n\nThrowable text was: \"\"",
      reportedException \ "value"
    )
    assertUserMessageIsReportedAsPartOf(userMessage, reportedError)
  }

  @Test
  def failsToSendReportIfSentryIsDisabled(): Unit = {
    Sentry.close()

    val submittedReportInfo = submitError("A message from a kind user", reportingEventFor(new RuntimeException))

    assertEquals(SubmissionStatus.FAILED, submittedReportInfo.getStatus)
    assertTrue(transport.sentData.isEmpty)
  }

  @Test
  def reportsErrorsWithAttachments(): Unit = {
    val exceptionToReport = new RuntimeException
    val userMessage = "A message from a kind user"
    val attachments = List(new Attachment("error", exceptionToReport))

    val submittedReportInfo = submitError(
      userMessage,
      reportingEventFor(exceptionToReport, attachments)
    )

    assertEquals(SubmissionStatus.NEW_ISSUE, submittedReportInfo.getStatus)

    val List(reportedError: JObject, attachment: String) = transport.sentData

    assertTrue(attachment.startsWith(exceptionToReport.getClass.getName))
    assertErrorMetadataIsReportedAsPartOf(reportedError)
    assertExceptionIsReportedAsPartOf(exceptionToReport, reportedError)
    assertUserMessageIsReportedAsPartOf(userMessage, reportedError)
  }

  private def reportingEventFor(exceptionToReport: RuntimeException, attachments: List[Attachment] = List()) = {
    val messageObject = new AbstractMessage {
      override def getThrowable: Throwable = exceptionToReport
      override def getThrowableText: String = ExceptionUtil.getThrowableText(exceptionToReport)
      override def getMessage: String = ""
      override def getAllAttachments: util.List[Attachment] = attachments.asJava
    }

    new IdeaReportingEvent(messageObject, null, messageObject.getThrowableText, pluginDescriptor)
  }

  private def submitError(userMessage: String, reportingEvent: IdeaLoggingEvent): SubmittedReportInfo = {
    new ErrorSubmissionTask(reportingEvent, userMessage, pluginDescriptor).run()
  }

  private def assertErrorMetadataIsReportedAsPartOf(reportedError: JObject): Unit = {
    assertValueEquals("error", reportedError \ "level")

    assertStringValue(reportedError \ "event_id")
    assertValueNotEquals(SentryId.EMPTY_ID.toString, reportedError \ "event_id")

    assertValueEquals(SystemInfo.JAVA_VENDOR, reportedError \ "tags" \ "java_vendor")
    assertValueEquals(SystemInfo.JAVA_VERSION, reportedError \ "tags" \ "java_version")

    assertValueEquals(SystemInfo.OS_NAME, reportedError \ "contexts" \ "os" \ "name")
    assertValueEquals(SystemInfo.OS_VERSION + "-" + SystemInfo.OS_ARCH, reportedError \ "contexts" \ "os" \ "version")

    assertStringValue(reportedError \ "contexts" \ "runtime" \ "name")
    assertStringValue(reportedError \ "contexts" \ "runtime" \ "version")

    assertValueEquals(pluginDescriptor.getVersion, reportedError \ "release")

    assertValueEquals("test", reportedError \ "environment")

    assertValueEquals(None, reportedError \ "server_name")

    assertValueEquals(ErrorSubmitter.currentUserId.get, reportedError \ "user" \ "id")
  }

  private def assertExceptionIsReportedAsPartOf(exceptionToReport: RuntimeException, reportedError: JObject): Unit = {
    val reportedException = (reportedError \ "exception" \ "values").apply(0)

    assertValueEquals(exceptionToReport.getClass.getSimpleName, reportedException \ "type")

    val stackFrames = asArray(reportedException \ "stacktrace" \ "frames")
    assertLastStackFrameCorrespondsToTheExceptionToReport(exceptionToReport, stackFrames)
    assertFirstStackFrameIsNotFromOurCode(stackFrames)
  }

  private def assertLastStackFrameCorrespondsToTheExceptionToReport(exceptionToReport: RuntimeException, stackFrames: List[JValue]): Unit = {
    val exceptionLineNumber = exceptionToReport.getStackTrace.head.getLineNumber
    assertValueEquals(
      s"https://github.com/JavierGelatti/RubyRefactorings/blob/main/src/main/scala/com/refactorings/ruby/TestErrorSubmitter.scala#L${exceptionLineNumber}",
      stackFrames.last \ "filename"
    )
    assertValueEquals(true, stackFrames.last \ "in_app")
  }

  private def assertFirstStackFrameIsNotFromOurCode(stackFrames: List[JValue]): Unit = {
    assertValueEquals(false, stackFrames.head \ "in_app")
  }

  private def assertUserMessageIsReportedAsPartOf(userMessage: String, reportedError: JObject): Unit = {
    assertValueEquals(userMessage, reportedError \ "message" \ "message")
    assertValueEquals("true", reportedError \ "tags" \ "with_description")
  }

  private def assertUserMessageIsNotReportedAsPartOf(reportedError: JObject): Unit = {
    assertValueEquals(None, reportedError \ "message")
    assertValueEquals(None, reportedError \ "tags" \ "with_description")
  }

  private def assertValueEquals(expectedValue: Any, jsonValue: JValue): Unit = {
    assertEquals(expectedValue, jsonValue.values)
  }

  private def assertValueNotEquals(expectedValue: Any, jsonValue: JValue): Unit = {
    assertNotEquals(expectedValue, jsonValue.values)
  }

  private def assertStringValue(value: JValue): Unit = {
    assertTrue(value.values.asInstanceOf[String].isNotEmptyOrSpaces)
  }

  private def asArray(value: JValue) = {
    value.asInstanceOf[JArray].arr
  }
}

class InMemorySentryTransport extends ITransport {
  private var sentText : List[String] = List()

  def sentData: List[Serializable] = sentText.map { text =>
    JsonParser.parseOpt(text).getOrElse(text)
  }

  override def send(envelope: SentryEnvelope, hint: Any): Unit = envelope.getItems.forEach { item =>
    sentText :+= new String(item.getData)
  }

  override def flush(timeoutMillis: Long): Unit = ()

  override def close(): Unit = ()
}
