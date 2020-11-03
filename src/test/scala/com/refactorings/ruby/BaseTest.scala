package com.refactorings.ruby

import org.junit.Rule
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

import scala.annotation.meta.getter

class BaseTest {
  @(Rule @getter)
  val withFeatureFlags: TestRule = (base: Statement, description: Description) => {
    Option(description.getAnnotation(classOf[ForFeature]))
      .map(_.value)
      .map(featureFlag => new Statement {
        override def evaluate(): Unit = {
          featureFlag.activateIn(() => base.evaluate())
        }
      })
      .getOrElse(base)
  }
}
