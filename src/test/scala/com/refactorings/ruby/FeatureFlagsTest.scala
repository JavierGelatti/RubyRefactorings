package com.refactorings.ruby

import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.Test

import scala.language.reflectiveCalls

class FeatureFlagsTest {
  private val featureFlag = FeatureFlag.FeatureFlag("example feature")

  @Test
  def isInactiveByDefault(): Unit = {
    assertFalse(featureFlag.isActive)
  }

  @Test
  def isActiveOnlyInsideActivationBlock(): Unit = {
    var activated = false;

    featureFlag.activateIn {
      activated = featureFlag.isActive
    }

    assertTrue(activated)
    assertFalse(featureFlag.isActive)
  }

  @Test
  def remainsActiveInsideNestedActivationBlocks(): Unit = {
    var activated = false;

    featureFlag.activateIn {
      featureFlag.activateIn {}
      activated = featureFlag.isActive
    }

    assertTrue(activated)
    assertFalse(featureFlag.isActive)
  }
}
