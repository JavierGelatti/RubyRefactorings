package com.refactorings.ruby

import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.Test

import scala.language.{implicitConversions, reflectiveCalls}

class FeatureFlagsTest extends BaseTest {
  private val featureFlag = FeatureFlag.values().head

  @Test
  def isInactiveByDefault(): Unit = {
    assertFalse(featureFlag.isActive)
  }

  @Test
  def isActiveOnlyInsideActivationBlock(): Unit = {
    var activated = false;

    featureFlag.activateIn(() => {
      activated = featureFlag.isActive
    })

    assertTrue(activated)
    assertFalse(featureFlag.isActive)
  }

  @Test
  def remainsActiveInsideNestedActivationBlocks(): Unit = {
    var activated = false;

    featureFlag.activateIn(() => {
      featureFlag.activateIn(() => {})
      activated = featureFlag.isActive
    })

    assertTrue(activated)
    assertFalse(featureFlag.isActive)
  }

  @Test
  @ForFeature(FeatureFlag.MergeSingletonClasses)
  def isActivatedIfTestHasAnnotation(): Unit = {
    assertTrue(featureFlag.isActive)
  }
}

