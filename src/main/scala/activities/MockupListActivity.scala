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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.CursorAdapter
import android.graphics.Bitmap

import android.content.Context
import android.database.Cursor

import BitmapHelpers._

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
  extends SModelAdapter[MockupWithImage](R.layout.listitem_mockup, showMockup _) {
    val lru = new SLruCache[String, Bitmap](15)

    def setImageBitmap(iv: ImageView, uri: String, bmp: Option[Bitmap]) =
      // If the view tag is null, return immediately
      if (iv.getTag != null) {

        // Check if the tag's URI is equal to the bitmap's URI
        iv.getTag.asInstanceOf[Option[String]] match {

          // If the tag is right, change the image
          case Some(uriTag) if uriTag == uri => runOnUiThread {
            bmp match {
              case Some(b) => iv setImageBitmap b
              case None => iv setImageBitmap null
            }
          }

          // Do nothing if the tag is not right
          case _ => ()
        }
      }

    override def query = {
      // Find out the table names
      val nMockup = db.tableName[Mockup]
      val nMockupImage = db.tableName[MockupImage]

      // We're doing an inner join to find the first image
      val nJoinedTableName =
        s"$nMockup m LEFT OUTER JOIN $nMockupImage mi ON m._id = mi.mockup_id"

      // Run the query
      db.ro.query(
        true,
        nJoinedTableName,
        Array("*", "m._id as _id"), // Necessary to be able to see _id
        null,
        null,
        "_id",                    // Group by m._id
        null,
        null,
        null
      )
    }

    def update(v: View, context: Context, m: MockupWithImage) {
      val titleView = v.findViewById(R.id.title).asInstanceOf[TextView]
      val imageView = v.findViewById(R.id.preview).asInstanceOf[ImageView]

      imageView setTag m.uri.value
      imageView setImageBitmap null

      for (t <- m.title.value) titleView setText t
      for (uri <- m.uri.value)
        lru(uri)(setImageBitmap(imageView, _, _), loadBitmap _)
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
    .onComplete { case _ => runOnUiThread {
      stopLoading
      adapter.notifyDataSetChanged
    }}
  }

  // Show the mockup activity
  def showMockup(m: _Mockup) = {
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
