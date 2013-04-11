package com.github.fxthomas.mocka

import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.{Menu, MenuInflater, MenuItem}
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.Window
import android.view.KeyEvent
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.widget.CursorAdapter
import android.widget.ImageView
import android.widget.TextView
import android.widget.EditText
import android.widget.FrameLayout
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri

import scala.concurrent._
import scala.util.{Failure, Success}
import ExecutionContext.Implicits.global

import BitmapHelpers._

import org.scaloid.common._

class MockupActivity extends SActivity with TypedActivity {
  import MockupActivity._

  // Retrieve the mockup ID and title
  def mockup_id = getIntent.getLongExtra(MOCKUP_ID, 0)
  def mockup_title = getIntent.getStringExtra(MOCKUP_TITLE)

  // Default database
  lazy implicit val db = new MockupOpenHelper

  // List view holding the mockup images
  lazy val listView = findView(TR.screens)

  // Title text editor
  lazy val titleTextView = new EditText(implicitly[Context])

  // Show a mockup image
  def showImage(m: MockupImage) = ()

  // List view adapter
  object adapter
  extends SModelAdapter[MockupImage](R.layout.listitem_mockupimage, showImage _) {
    val lru = new SLruCache[String, Bitmap](15)

    override def query =
      db.findBy[MockupImage, Long]("mockup_id", mockup_id, "image_order")

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

    def update(v: View, context: Context, mi: MockupImage) {

      // Find the text view
      val titleView = v.findViewById(R.id.title).asInstanceOf[TextView]
      val imageView = v.findViewById(R.id.image).asInstanceOf[ImageView]

      // Tag the view with the current mockup image URI
      imageView setTag mi.uri.value
      imageView setImageBitmap null

      // Set the title
      for (t <- mi.image_order.value) titleView setText s"Slide ${t.toString}"

      // Set the image
      for (uri <- mi.uri.value)
        lru(uri)(setImageBitmap(imageView, _, _), loadBitmap _)
    }
  }

  // Reload the mockups
  def reload = {
    // Start the loading spinner
    runOnUiThread {
      titleTextView setText mockup_title
      startLoading
    }

    // Reload things
    adapter.reload

    // Stop the loading spinner when it's done
    .onComplete { case _ => runOnUiThread {
      stopLoading
      adapter.notifyDataSetChanged
    }}
  }

  // Start and stop the loading Window spinner
  def startLoading = setProgressBarIndeterminateVisibility(true)
  def stopLoading = setProgressBarIndeterminateVisibility(false)

  override def onCreate(bundle: Bundle) = {
    // Load UI
    super.onCreate(bundle)

    // Setup the UI
    this requestWindowFeature Window.FEATURE_INDETERMINATE_PROGRESS
    this setContentView R.layout.ui_mockup
    getActionBar setDisplayHomeAsUpEnabled true

    // Prepare the title text view
    titleTextView setSingleLine true
    titleTextView setTextSize 20
    titleTextView setGravity Gravity.CENTER_HORIZONTAL
    titleTextView setImeOptions EditorInfo.IME_ACTION_DONE
    titleTextView onEditorAction {
      (v: TextView, actionId: Int, ev: KeyEvent) =>
      if (actionId == EditorInfo.IME_ACTION_DONE) {

        // Save mockup
        info(s"Saving mockup")
        val m = new Mockup
        m.id := mockup_id
        m.title := v.getText.toString
        future { m.save } onSuccess { case _ => info(s"Saved mockup") }

        // Dismiss keyboard
        titleTextView.clearFocus
        inputMethodManager.hideSoftInputFromWindow(v.getWindowToken, 0)

        // We did something
        true

      } else false
    }

    // Set the list adapter
    listView addHeaderView titleTextView
    listView setAdapter adapter
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockupimage, menu)
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
      // Create the image
      create(data.getData.asInstanceOf[Uri].toString)

      // Reload the view
      reload
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
    mockupimage.image_order := adapter.getCount

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
