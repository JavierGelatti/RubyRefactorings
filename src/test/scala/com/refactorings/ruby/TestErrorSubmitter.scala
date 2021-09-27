package com.refactorings.ruby

import com.intellij.diagnostic.{AbstractMessage, IdeaReportingEvent}
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.diagnostic.SubmittedReportInfo
import com.intellij.openapi.diagnostic.SubmittedReportInfo.SubmissionStatus
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.SystemInfo
import io.sentry.SentryEnvelope
import io.sentry.protocol.SentryId
import io.sentry.transport.ITransport
import org.json4s.native.JsonParser
import org.json4s.{JArray, JObject, JValue}
import org.junit.Assert.{assertEquals, assertNotEquals, assertTrue}
import org.junit.{Before, Test}

class TestErrorSubmitter extends RefactoringTestRunningInIde {
  private val transport = new InMemoryTransport
  private lazy val pluginDescriptor = PluginManagerCore.getPlugin(PluginId.findId("com.refactorings.ruby.RubyRefactorings"))

  private var shownMessage: String = _
  private var submittedReportInfo: SubmittedReportInfo = _

  @Before
  def installFakeTransportImplementation(): Unit = {
    ErrorSubmitter.setTransportFactory((_, _) => transport)
    ErrorSubmitter.sentryAlreadyInitialized = false
  }

  @Test
  def successfullyReportExceptionWithoutUserMessage(): Unit = {
    val exceptionToReport = new RuntimeException
    val userMessage = null

    submitError(exceptionToReport, userMessage)

    assertEquals("Success", shownMessage)
    assertEquals(SubmissionStatus.NEW_ISSUE, submittedReportInfo.getStatus)

    val List(reportedError) = transport.sentData

    assertErrorMetadataIsReportedAsPartOf(reportedError)
    assertExceptionIsReportedAsPartOf(exceptionToReport, reportedError)
    assertUserMessageIsNotReportedAsPartOf(reportedError)
  }

  @Test
  def successfullyReportExceptionWithEmptyUserMessage(): Unit = {
    val exceptionToReport = new RuntimeException
    val userMessage = "   "

    submitError(exceptionToReport, userMessage)

    assertEquals("Success", shownMessage)
    assertEquals(SubmissionStatus.NEW_ISSUE, submittedReportInfo.getStatus)

    val List(reportedError) = transport.sentData

    assertErrorMetadataIsReportedAsPartOf(reportedError)
    assertExceptionIsReportedAsPartOf(exceptionToReport, reportedError)
    assertUserMessageIsNotReportedAsPartOf(reportedError)
  }

  @Test
  def successfullyReportExceptionWithUserMessage(): Unit = {
    val exceptionToReport = new RuntimeException
    val userMessage = "A message from a kind user"

    submitError(exceptionToReport, userMessage)

    assertEquals("Success", shownMessage)
    assertEquals(SubmissionStatus.NEW_ISSUE, submittedReportInfo.getStatus)

    val List(reportedError) = transport.sentData

    assertErrorMetadataIsReportedAsPartOf(reportedError)
    assertExceptionIsReportedAsPartOf(exceptionToReport, reportedError)
    assertUserMessageIsReportedAsPartOf(userMessage, reportedError)
  }

  private def submitError(exceptionToReport: RuntimeException, userMessage: String): Unit = {
    new ErrorSubmissionTask(
      event = reportingEventFor(exceptionToReport),
      additionalInfo = userMessage,
      parentComponent = null,
      project = project,
      pluginDescriptor = pluginDescriptor,
      consumer = submittedReportInfo = _,
    ) {
      override def invokeLater(toRun: => Unit): Unit = toRun
      override def showSuccessMessage(): Unit = shownMessage = "Success"
      override def showFailureMessage(): Unit = shownMessage = "Error"
    }.run()
  }

  private def reportingEventFor(exceptionToReport: RuntimeException) = {
    val messageObject = new AbstractMessage {
      override def getThrowable: Throwable = exceptionToReport
      override def getThrowableText: String = "stack trace text from exceptionToReport"
      override def getMessage: String = ""
    }

    new IdeaReportingEvent(messageObject, null, messageObject.getThrowableText, pluginDescriptor)
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

class InMemoryTransport extends ITransport {
  var sentData : List[JObject] = List()

  override def send(envelope: SentryEnvelope, hint: Any): Unit = {
    envelope.getItems.forEach(item => {
      sentData :+= JsonParser.parse(new String(item.getData)).asInstanceOf[JObject]
    })
  }

  override def flush(timeoutMillis: Long): Unit = ()

  override def close(): Unit = ()
}
