package com.github.fxthomas.mocka

import android.app.ProgressDialog
import android.content.Context
import android.widget.ListView

import scala.concurrent._
import ExecutionContext.Implicits.global

import org.scaloid.common._

object ActivityHelpers {
  // Run a spinner while a future is computing
  def indeterminate[T](title: String, message: String, work: Future[T])(implicit ctx: Context) {
    // Create a progress dialog
    val spinner = new ProgressDialog(ctx)
    spinner setTitle title
    spinner setMessage message
    spinner setIndeterminate true

    // Show it
    runOnUiThread { spinner.show }

    // Dismiss the spinner on completion
    work onComplete { case _ => runOnUiThread { spinner.dismiss } }
  }

  class RichListView[V <: ListView](val basis: V) extends TraitAdapterView[V]
  @inline implicit def listView2RichListView[V <: ListView](lv: V) = new RichListView[V](lv)
}
