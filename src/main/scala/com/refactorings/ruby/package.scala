package com.refactorings

import java.util

import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.language.implicitConversions

package object ruby {
  implicit def list2Scala[T]: util.List[T] => mutable.Buffer[T] = list => list.asScala
  implicit def fun2Runnable(fun: => Unit): Runnable = new Runnable() { def run(): Unit = fun }

  sealed class DefaultsTo[Provided, Default]
  object DefaultsTo {
    implicit def useDefault[Default]: DefaultsTo[Default, Default] = new DefaultsTo

    implicit def useProvided[Provided, Default]: DefaultsTo[Provided, Default] = new DefaultsTo
  }
}
