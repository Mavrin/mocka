package com.github.fxthomas.mocka

import android.util.LruCache

import scala.concurrent._
import scala.util.{Failure, Success}

class SLruCache[A, B](max_size: Int) extends LruCache[A, B](max_size) {
  def getOpt(key: A): Option[B] =
    Option(this get key)

  def apply(key: A)(run: (A, Option[B]) => Unit, elseRun: A => Option[B])
    (implicit ec: ExecutionContext) = {

    // Try to get the value
    this getOpt key match {
      // Return the value if it is in the cache
      case Some(b) => run(key, Some(b))

      // Load it first if it isn't
      case None => {
        // Retrieve the value
        future { elseRun(key) } onComplete {

          // Run the method if successfully retrieved
          case Success(Some(value)) => {
            synchronized { put(key, value) }
            run(key, Some(value))
          }

          // Remove key if the result if None
          case Success(None) => {
            synchronized { remove(key) }
            run(key, None)
          }

          // Print stacktrace if not
          case Failure(f) => {
            f.printStackTrace
            run(key, None)
          }
        }
      }
    }
  }
}
