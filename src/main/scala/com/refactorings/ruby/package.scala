package com.refactorings

import java.util

import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala

package object ruby {
  implicit def list2Scala[T]: util.List[T] => mutable.Buffer[T] = list => list.asScala

  sealed class DefaultsTo[Provided, Default]
  object DefaultsTo {
    implicit def useDefault[Default]: DefaultsTo[Default, Default] = new DefaultsTo

    implicit def useProvided[Provided, Default]: DefaultsTo[Provided, Default] = new DefaultsTo
  }
}
