package com.refactorings.ruby

import org.junit.Assert.assertEquals
import org.junit.Test

class TestSnakeToPascalCase {

  @Test
  def doesNothingIfTheIdentifierDoesNotHaveUnderscores(): Unit = {
    assertEquals("Identifier", "identifier".snakeToPascalCase)
  }

  @Test
  def replacesUnderscoresByUpcasingTheNextLetter(): Unit = {
    assertEquals("AnIdentifier", "an_identifier".snakeToPascalCase)
  }

  @Test
  def worksWithIdentifiersThatContainNumbers(): Unit = {
    assertEquals("IdentifierWith1Number", "identifier_with_1_number".snakeToPascalCase)
  }

  @Test
  def ignoresMultipleUnderscoresInTheMiddleOfTheIdentifier(): Unit = {
    assertEquals("AnIdentifier", "an__identifier".snakeToPascalCase)
  }

  @Test
  def ignoresMultipleUnderscoresAtTheEndOfTheIdentifier(): Unit = {
    assertEquals("AnIdentifier", "an_identifier_".snakeToPascalCase)
  }

  @Test
  def ignoresMultipleUnderscoresAtTheStartOfTheIdentifier(): Unit = {
    assertEquals("AnIdentifier", "_an_identifier".snakeToPascalCase)
  }

}
