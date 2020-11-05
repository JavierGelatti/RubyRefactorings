package com.refactorings

package object ruby {

  sealed class DefaultsTo[Provided, Default]
  object DefaultsTo {
    implicit def useDefault[Default]: DefaultsTo[Default, Default] = new DefaultsTo

    implicit def useProvided[Provided, Default]: DefaultsTo[Provided, Default] = new DefaultsTo
  }

}
