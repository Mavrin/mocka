package com.github.fxthomas.mocka

import android.util.LruCache
import scala.concurrent._

class SLruCache[A, B](max_size: Int) extends LruCache[A, B](max_size) {
  def apply(key: A, value: => B)
    (implicit ec: ExecutionContext) = {

    future {
      synchronized {
        Option(this get key) getOrElse {
          // Compute the value
          val result = value

          // Put the new value on the cache
          put(key, result)

          // Return it
          result
        }
      }
    }
  }
}
