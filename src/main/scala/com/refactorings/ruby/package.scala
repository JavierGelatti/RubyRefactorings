package com.refactorings

import java.util

import scala.collection.mutable
import scala.jdk.CollectionConverters.ListHasAsScala
import scala.language.implicitConversions

package object ruby {
  implicit def list2Scala[T]: util.List[T] => mutable.Buffer[T] = list => list.asScala
  implicit def fun2Runnable(fun: => Unit): Runnable = new Runnable() { def run(): Unit = fun }

  implicit class OptionExtensions[T](source: Option[T]) {
    def mapIf[S >: T](f: PartialFunction[T, S]): Option[S] = {
      source.collect(f.orElse(x => x))
    }
  }

  implicit class StringExtensions(source: String) {
    def snakeToPascalCase: String = source.split("_").map(_.capitalize).mkString
  }
}
