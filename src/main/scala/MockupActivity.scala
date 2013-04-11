package com.github.fxthomas.mocka

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.{Menu, MenuInflater, MenuItem}
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.FrameLayout
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache

import scala.concurrent._
import scala.util.{Failure, Success}
import ExecutionContext.Implicits.global

import BitmapHelpers._

import org.scaloid.common._

class MockupImagesAdapter(cursor: Cursor)(implicit ctx: Context)
extends CursorAdapter(ctx, cursor, 0) {

  // UI Inflater
  val inflater = LayoutInflater from ctx

  // LRU cache
  val lru = new LruCache[String, Bitmap](15)

  // Load an existing view from a cursor
  override def bindView(v: View, lctx: Context, c: Cursor) = {

    // Create a new mockup object
    val mi = new MockupImage

    // Load it with the contents of the cursor
    mi << c

    // Find the text view
    val titleView = v.findViewById(R.id.title).asInstanceOf[TextView]
    val imageView = v.findViewById(R.id.image).asInstanceOf[ImageView]

    // Set the title
    for (t <- mi.image_order.value) titleView setText s"Slide ${t.toString}"

    // Set the image
    mi.uri.value match {
      case Some(uri) => {
        // Try to retrieve the imag from the LRU cache
        val img = lru get uri

        // If it was retrieved, use it
        if (img != null) imageView setImageBitmap img

        // If not, load it and put it in the cache
        else future { loadBitmap(uri) } collect {
          case Some(b) => {
            // Put the image in the cache
            lru synchronized { lru.put (uri, b) }

            // Put the image in the ImageView
            runOnUiThread { imageView setImageBitmap b }
          }
        }
      }

      case None => imageView setImageBitmap null
    }
  }

  // Load a new view
  override def newView(lctx: Context, c: Cursor, parent: ViewGroup) =
    inflater.inflate (R.layout.listitem_mockupimage, parent, false)
}

class MockupActivity extends SActivity with TypedActivity {
  import MockupActivity._

  def mockup_id = getIntent.getLongExtra(MOCKUP_ID, 0)

  lazy implicit val db = new MockupOpenHelper
  lazy val listView = findView(TR.screens)

  var listViewAdapter: Option[MockupImagesAdapter] = None
  var menuItemNew: Option[MenuItem] = None
  var mockup_title: Option[String] = None

  def reload = {
    // Set the progress bar to visible
    startLoading

    // Load the things
    future { db.findBy[MockupImage, Long]("mockup_id", mockup_id, "image_order") } onComplete {

      // Say something in case of a failure
      case Failure(e) => error(s"Exception occured in future: ${e.toString}")

      // Update the view in case of a success
      case Success(c) => runOnUiThread {

        // Tell the user
        info("Finished loading mockup images")

        // Load the list
        listViewAdapter match {
          case Some(la) => la changeCursor c
          case None => {
            // Create a new list adapter
            val la = new MockupImagesAdapter(c)

            // Update the list
            listView setAdapter la

            // And update the list adapter parameter
            listViewAdapter = Some(la)
          }
        }

        // Hide the progress bar
        stopLoading
      }
    }
  }

  def startLoading = {
    runOnUiThread { setProgressBarIndeterminateVisibility(true) }
  }

  def stopLoading = {
    runOnUiThread { setProgressBarIndeterminateVisibility(false) }
  }

  override def onCreate(bundle: Bundle) = {
    // Load UI
    super.onCreate(bundle)

    // Load intents
    mockup_title = Option(getIntent getStringExtra MOCKUP_TITLE)

    // Setup the UI
    this requestWindowFeature Window.FEATURE_INDETERMINATE_PROGRESS
    this setContentView R.layout.ui_mockup
    this setTitle (mockup_title getOrElse "Mocka")
    getActionBar setDisplayHomeAsUpEnabled true

    // Reload the view
    reload
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockuplist, menu)
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

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    if (requestCode == PICK_IMAGE && data != null && data.getData != null) {
      create(data.getData.asInstanceOf[Uri].toString)
    }
  }

  def imagePath(uri: Uri): String = {
    // Create a cursor from the URI
    val cursor = getContentResolver.query(uri, Array(android.provider.MediaStore.MediaColumns.DATA), null, null, null)

    // Find the path of the first item in the cursor
    cursor.moveToFirst
    val imageFilePath = cursor getString 0
    cursor.close

    // Return that path
    return imageFilePath
  }

  def create(uri: String) = {
    // Create a new mockup
    info(s"Creating new mockup image with URI $uri")
    val mockupimage = new MockupImage
    mockupimage.mockup_id := mockup_id
    mockupimage.uri := uri
    mockupimage.image_order :=
      listViewAdapter.map(_.getCount).getOrElse[Int](0)

    // Save the mockup
    future { mockupimage.save } onComplete {
      case Failure(f) => error(s"Unable to save mockupimage: ${f.toString}")
      case Success(id) => reload
    }
  }

  override def onOptionsItemSelected (item: MenuItem): Boolean = {
    item.getItemId match {
      // Try to find an image
      case R.id.ui_new => {
        val intent = new Intent
        intent setType "image/*"
        intent setAction Intent.ACTION_GET_CONTENT
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE)
      }

      // Return to the previous activity
      case android.R.id.home => finish
    }

    return true
  }
}

object MockupActivity {
  val MOCKUP_ID = "mockup_id"
  val MOCKUP_TITLE = "mockup_title"

  val PICK_IMAGE = 1
}
