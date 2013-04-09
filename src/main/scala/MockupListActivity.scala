package com.github.fxthomas.mocka

import android.app.Activity
import android.app.ProgressDialog
import android.os.Bundle
import android.view.View
import android.view.Menu
import android.view.MenuInflater
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import android.view.Window
import android.view.Gravity
import android.widget.ProgressBar
import android.widget.ListView
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.CursorAdapter

import android.content.Context
import android.database.Cursor

import scala.concurrent._
import scala.util.{Failure, Success}
import ExecutionContext.Implicits.global

import org.scaloid.common._

class MockupListAdapter(cursor: Cursor)(listener: Mockup => Unit)(implicit ctx: Context)
extends CursorAdapter(ctx, cursor, 0) {

  // UI Inflater
  val inflater = LayoutInflater from ctx

  // Load an existing view from a cursor
  override def bindView(v: View, lctx: Context, c: Cursor) = {

    // Create a new mockup object
    val m = new Mockup

    // Load it with the contents of the cursor
    m << c

    // Find the text view
    val titleView = v.findViewById(R.id.title).asInstanceOf[TextView]

    // Fill the view
    for (t <- m.title.value) titleView setText t

    // Set onClick
    v onClick listener(m)
  }

  // Load a new view
  override def newView(lctx: Context, c: Cursor, parent: ViewGroup) =
    inflater.inflate (R.layout.listitem_mockup, parent, false)
}

class MockupListActivity extends SActivity with TypedActivity {
  lazy implicit val db = new MockupOpenHelper
  lazy val listView = findView(TR.mockups)

  var listViewAdapter: Option[MockupListAdapter] = None
  var menuItemNew: Option[MenuItem] = None

  def runIndeterminate[T](title: String, message: String)(work: =>T)
  (implicit ctx: Context): Future[T] = {

    // Create a progress dialog
    val spinner = new ProgressDialog(ctx)
    spinner setTitle title
    spinner setMessage message
    spinner setIndeterminate true

    // Show it
    runOnUiThread { spinner.show }

    // Push it baby!
    val value = future { work }

    // Dismiss the spinner on completion
    value onComplete { case _ => runOnUiThread { spinner.dismiss } }
    value onFailure { case e => e.printStackTrace }

    // Return what we did
    return value
  }

  def reload = {
    // Load the things
    val f = runIndeterminate("Mocka", "Reading your awesome mockups!")
      { db.all[Mockup] } 

    // Update the view in case of a success
    f onSuccess { case c => runOnUiThread {

        // Tell the user
        info("Finished loading mockups")

        // Load the list
        listViewAdapter match {
          case Some(la) => la changeCursor c
          case None => {
            // Create a new list adapter
            val la = new MockupListAdapter(c)(showMockup _)

            // Update the list
            listView setAdapter la

            // And update the list adapter parameter
            listViewAdapter = Some(la)
          }
        }
      }
    }
  }

  def showMockup(m: Mockup) = {
    // Start the activity
    val intent = SIntent[MockupActivity]
    for (id <- m.id.value) intent.putExtra(MockupActivity.MOCKUP_ID, id)
    for (title <- m.title.value) intent.putExtra(MockupActivity.MOCKUP_TITLE, title)
    startActivity(intent)
  }

  def startLoading = {
    setProgressBarIndeterminateVisibility(true)
  }

  def stopLoading = {
    setProgressBarIndeterminateVisibility(false)
  }

  override def onCreate(bundle: Bundle) = {
    // Create the view
    super.onCreate(bundle)
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    setContentView(R.layout.ui_mockuplist)

    // Reload the view
    reload
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockuplist, menu)
    menuItemNew = Some(menu.findItem(R.id.ui_new))
    return true
  }

  override def onPause = {
    db.close
    super.onPause
  }

  override def onResume = {
    reload
    super.onResume
  }

  override def onDestroy = {
    db.close
    super.onDestroy
  }

  override def onOptionsItemSelected (item: MenuItem): Boolean = item.getItemId match {
    case R.id.ui_new => {
      // Create a new mockup
      val mockup = new Mockup
      mockup.title := "Hello, world"

      // Disable the menu item until we're done
      item setEnabled false

      // Save the mockup
      future { mockup.save } onComplete {
        case Failure(f) => f.printStackTrace
        case Success(id) => runOnUiThread {
          // Log info
          info(s"Created item $id")

          // Set the menu item to enabled again
          item setEnabled true

          // Show mockup
          showMockup(mockup)
        }
      }

      return true
    }
  }
}
