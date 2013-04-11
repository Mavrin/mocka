package com.github.fxthomas.mocka

import android.app.Activity
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

class MockupListActivity extends SActivity with TypedActivity {
  // Current database
  lazy implicit val db = new MockupOpenHelper

  // The current list view holding mockups
  lazy val listView = findView(TR.mockups)

  // List view adapter for mockup objects
  object adapter
  extends SModelAdapter[Mockup](R.layout.listitem_mockup, showMockup _) {
    def update(v: View, context: Context, m: Mockup) {
      val titleView = v.findViewById(R.id.title).asInstanceOf[TextView]
      for (t <- m.title.value) titleView setText t
    }
  }

  // Start and stop the loading Window spinner
  def startLoading = setProgressBarIndeterminateVisibility(true)
  def stopLoading = setProgressBarIndeterminateVisibility(false)

  // Reload the mockups
  def reload = {
    // Start the loading spinner
    runOnUiThread { startLoading }

    // Reload things
    adapter.reload

    // Stop the loading spinner when it's done
    .onComplete { case _ => runOnUiThread { stopLoading } }
  }

  // Show the mockup activity
  def showMockup(m: Mockup) = {
    // Start the activity
    val intent = SIntent[MockupActivity]
    for (id <- m.id.value) intent.putExtra(MockupActivity.MOCKUP_ID, id)
    for (title <- m.title.value) intent.putExtra(MockupActivity.MOCKUP_TITLE, title)
    startActivity(intent)
  }

  // Activity creation
  override def onCreate(bundle: Bundle) = {
    // Create the view
    super.onCreate(bundle)
    requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS)
    setContentView(R.layout.ui_mockuplist)

    // Set the list adapter
    listView setAdapter adapter
  }

  // Option menu creation
  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockuplist, menu)
    return true
  }

  // Activity pause
  override def onPause = {
    db.close
    super.onPause
  }

  // Activity resume
  override def onResume = {
    reload
    super.onResume
  }

  // Activity destruction
  override def onDestroy = {
    db.close
    super.onDestroy
  }

  // What to do when the user clicks on the option menu?
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
