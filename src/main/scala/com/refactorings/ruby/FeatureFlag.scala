package com.refactorings.ruby

object FeatureFlag extends Enumeration {
  val MergeSingletonClasses: FeatureFlag = FeatureFlag("merge singleton classes")

  case class FeatureFlag(name: String) extends Val(nextId, name) {
    private var currentValue = false

    def activateIn[T](closure: => T): T = {
      val oldValue = currentValue
      currentValue = true
      val result = closure
      currentValue = oldValue
      result
    }

    def isActive: Boolean = currentValue
  }
}
