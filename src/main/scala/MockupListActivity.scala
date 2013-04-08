package com.github.fxthomas.mocka

import android.app.Activity
import android.os.Bundle
import android.view.{View, Menu, MenuInflater, MenuItem, ViewGroup, Gravity}
import android.widget.ArrayAdapter
import android.widget.ProgressBar
import android.widget.ListView
import android.widget.LinearLayout

import scala.concurrent._
import ExecutionContext.Implicits.global

import org.scaloid.common._

class MockupListActivity extends SActivity with TypedActivity {
  lazy implicit val db = new MockupOpenHelper
  lazy val listView = findView(TR.mockups)
  lazy val progressBar = findView(TR.progressbar)

  override def onCreate(bundle: Bundle) = {
    // Create the view
    super.onCreate(bundle)
    setContentView(R.layout.ui_mockuplist)

    // Set the progress bar to visible
    findView(TR.progressbar).setVisibility(View.VISIBLE)

    // Load the things
    val all = future { db.all[Mockup] }

    // Disable the progress bar on success
    all onSuccess {
      case als: List[_] => runOnUiThread {
        progressBar.setVisibility(View.GONE)
      }
    }
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockuplist, menu)
    return true
  }

  override def onPause = {
    db.close
    super.onPause
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
      future { mockup.save } onSuccess {

        case id: Long => runOnUiThread {
          // Set the menu item to enabled again
          item setEnabled true

          // Start the activity
          val intent = SIntent[MockupActivity]
          intent.putExtra(MockupActivity.MOCKUP_ID, id)
          startActivity(intent)
        }
      }

      return true
    }
  }
}
