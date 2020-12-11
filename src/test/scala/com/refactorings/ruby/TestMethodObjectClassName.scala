package com.refactorings.ruby

import com.refactorings.ruby.ExtractMethodObject.initialMethodObjectClassNameFrom
import org.junit.Assert.assertEquals
import org.junit.Test

class TestMethodObjectClassName {

  @Test
  def forRegularMethodNames(): Unit = {
    assertEquals(
      "MethodNameMethodObject",
      initialMethodObjectClassNameFrom("method_name")
    )
  }

  @Test
  def replacesMultiplicationOperator(): Unit = {
    assertEquals(
      "MultiplyMethodObject",
      initialMethodObjectClassNameFrom("*")
    )
  }

  @Test
  def replacesPowerOperator(): Unit = {
    assertEquals(
      "PowerMethodObject",
      initialMethodObjectClassNameFrom("**")
    )
  }

  @Test
  def replacesEqualsOperator(): Unit = {
    assertEquals(
      "EqualMethodObject",
      initialMethodObjectClassNameFrom("==")
    )
  }

  @Test
  def replacesNotEqualOperator(): Unit = {
    assertEquals(
      "NotEqualMethodObject",
      initialMethodObjectClassNameFrom("!=")
    )
  }

  @Test
  def replacesUnaryInvertOperator(): Unit = {
    assertEquals(
      "InvertMethodObject",
      initialMethodObjectClassNameFrom("-@")
    )
  }

  @Test
  def ignoresQuestionMarks(): Unit = {
    assertEquals(
      "IsEmptyMethodObject",
      initialMethodObjectClassNameFrom("is_empty?")
    )
  }

  @Test
  def replacesBangsWithBang(): Unit = {
    assertEquals(
      "SaveBangMethodObject",
      initialMethodObjectClassNameFrom("save!")
    )
  }

  @Test
  def prependsWriteWhenUsingAssingmentNotationAndIdentifierStartsWithLetter(): Unit = {
    assertEquals(
      "WriteM1MethodObject",
      initialMethodObjectClassNameFrom("m1=")
    )
  }

  @Test
  def prependsWriteWhenUsingAssingmentNotationAndIdentifierStartsWithUnderscore(): Unit = {
    assertEquals(
      "WriteM1MethodObject",
      initialMethodObjectClassNameFrom("_m1=")
    )
  }

  @Test
  def prependsWriteWhenUsingAssingmentNotationAndIdentifierStartsWithUppercase(): Unit = {
    assertEquals(
      "WriteM1MethodObject",
      initialMethodObjectClassNameFrom("M1=")
    )
  }

  @Test
  def replacesElementWriteOperator(): Unit = {
    assertEquals(
      "WriteElementMethodObject",
      initialMethodObjectClassNameFrom("[]=")
    )
  }

  @Test
  def replacesElementReadOperator(): Unit = {
    assertEquals(
      "ReadElementMethodObject",
      initialMethodObjectClassNameFrom("[]")
    )
  }

  @Test
  def regularNamesWorkWithSpecialCharacters(): Unit = {
    assertEquals(
      "こんにちはMethodObject",
      initialMethodObjectClassNameFrom("こんにちは")
    )
  }

  @Test
  def assignmentNotationWorkWithSpecialCharacters(): Unit = {
    assertEquals(
      "WriteこんにちはMethodObject",
      initialMethodObjectClassNameFrom("こんにちは=")
    )
  }
}
