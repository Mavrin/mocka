package com.github.fxthomas.mocka

import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import android.content.Context
import android.database.Cursor
import android.widget.CursorAdapter

import scala.concurrent._
import scala.reflect.ClassTag
import scala.util.{Failure, Success}

import SSQLiteOpenHelper.Implicits._

import org.scaloid.common._

abstract class SModelAdapter[T <: Model : ClassTag]
  (res: Int)
  (implicit db: SSQLiteOpenHelper, ctx: Context, exc: ExecutionContext)
extends CursorAdapter(ctx, null, 0) {

  // Implicit conversion from Cursor to model objects
  implicit def cursor2model(c: Cursor): T = c.as[T]

  // Reload the model
  def reload: Future[Cursor] = {
    // Do the work in a future
    val fut = future { query }

    // Change the cursor on completion, or print a stack trace
    fut onComplete {
      case Failure(f) => f.printStackTrace
      case Success(c) => runOnUiThread {
        this.changeCursor(c)
        this.notifyDataSetChanged
      }
    }

    // Return the future
    return fut
  }

  // Query to be run against the database
  protected def query = db.all[T]

  // How to update the current view with the model?
  protected def update(view: View, context: Context, model: T)

  // UI Inflater
  private val inflater = LayoutInflater from ctx

  // Bind the view's data
  override def bindView(v: View, lctx: Context, c: Cursor) =
    runOnUiThread { update(v, lctx, c) }

  // Create a new view
  override def newView(lctx: Context, c: Cursor, parent: ViewGroup) =
    runOnUiThread { inflater.inflate(res, parent, false) }
}
