package com.github.fxthomas.mocka

import android.app._
import android.os._
import android.view._
import android.view.inputmethod._
import android.widget._
import android.content._
import android.content.pm._
import android.content.res._
import android.database.Cursor
import android.graphics._
import android.net._

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

  // Currently displayed image
  var current_image_id: Option[Long] = None
  var current_x = 0.f
  var current_y = 0.f

  // Current state
  var current_state = STATE_EDIT

  // Default database
  lazy implicit val db = new MockupOpenHelper

  // List view holding the mockup images
  lazy val listView = findView(TR.screens)
  lazy val screenView = findView(TR.screen)
  lazy val flipper = findView(TR.flipper)
  class RichListView[V <: ListView](val basis: V) extends TraitAdapterView[V]
  @inline implicit def listView2RichListView[V <: ListView](lv: V) = new RichListView[V](lv)

  // Title text editor
  lazy val titleTextView = new EditText(implicitly[Context])

  override def onBackPressed() {
    if (flipper.getDisplayedChild > 0) {
      getActionBar.show
      flipper.showPrevious
      current_image_id = None
      current_state = STATE_EDIT
    } else super.onBackPressed
  }


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
    titleTextView setLongClickable false
    titleTextView onEditorAction {
      (v: TextView, actionId: Int, ev: KeyEvent) =>
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        saveTitle; true
      } else false
    }

    screenView onSelect {
      (x: Float, y: Float) => { addTransition(0, x, y, 50.f); true }
    }

    // Set the list adapter
    listView.addHeaderView(titleTextView, null, false)
    listView setAdapter adapter
    listView onItemClick {
      (parent: AdapterView[_], view: View, position: Int, id: Long) => {
        val cursor = (parent getItemAtPosition position).asInstanceOf[Cursor]
        showImage (db.fromCursor[MockupImage](cursor))
      }
    }

    this registerForContextMenu listView
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockupimage, menu)
    return true
  }

  // Context menu creation
  override def onCreateContextMenu(menu: ContextMenu, v: View, info: ContextMenu.ContextMenuInfo) = {
    v.getId match {
      case R.id.screens => {
        val tinfo = info.asInstanceOf[AdapterView.AdapterContextMenuInfo]
        menu setHeaderTitle "Screen"
        menu.add (0, MENU_EDIT_TITLE, 0, "Edit title")
        menu.add (0, MENU_REMOVE, 1, "Remove")
      }
    }
  }

  // Context menu item click
  override def onContextItemSelected(item: MenuItem): Boolean = {
    // Retrieve information about the selected list item
    val info = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]

    // Execute the right action for the selected menu item
    item.getItemId match {
      case MENU_REMOVE => reloadAfter { removeImage(info.position) }
      case MENU_EDIT_TITLE => {
        new AlertDialogBuilder("Edit title") {
          // Currently selected mockup
          val cursor = (listView getItemAtPosition info.position)
          val mi = db.fromCursor[MockupImage](cursor.asInstanceOf[Cursor])

          // Edit view
          val et = new EditText(implicitly[Context])
          val lp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.FILL_PARENT,
            ViewGroup.LayoutParams.FILL_PARENT
          )
          mi.image_title.value map (et.setText _)
          et setLayoutParams lp
          this setView et

          // Create a "Save" button
          positiveButton("Save", reloadAfter {
            mi.image_title := et.getText.toString
            future { mi.save }
          })

          // Create a "Cancel" button
          negativeButton("Cancel")
        }.show()
      }
    }

    // We did something, so return true
    return true
  }

  override def onPause = {
    saveTitle onComplete { case _ => db.close }
    super.onPause
  }

  override def onResume = {
    reload
    super.onResume
  }

  override def onDestroy = {
    saveTitle onComplete { case _ => db.close }
    super.onDestroy
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent) {
    if (requestCode == PICK_IMAGE && data != null && data.getData != null) {
      reloadAfter {
        addImage(data.getData.asInstanceOf[Uri].toString)
      }
    }
  }

  override def onConfigurationChanged (conf: Configuration) = {
    super.onConfigurationChanged(conf)
  }

  override def onOptionsItemSelected (item: MenuItem): Boolean = {
    item.getItemId match {
      case R.id.ui_new => selectImage
      case R.id.ui_play => startMockup
      case android.R.id.home => finish
    }

    return true
  }

  /*********************
   * Available actions *
   *********************/

  def selectImage {
    // Create an intent showing the gallery
    val intent = new Intent
    intent setType "image/*"
    intent setAction Intent.ACTION_GET_CONTENT

    // Start the intent
    startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE)
  }

  def addTransition(to: Long, x: Float, y: Float, size: Float) = {
    // Inform the user
    info(s"Saving transition for $mockup_id (at $x, $y)")

    // Create the transition
    val v = new MockupTransition
    v.mockup_id := mockup_id
    v.image_from := current_image_id.get
    v.image_to := to
    v.x := x
    v.y := y
    v.size := size

    // Saave the transition
    future { v.save }
  }

  def addImage(uri: String) = {
    // Log what's happening
    info(s"Creating new mockup image with URI $uri")

    // Create a new mockup image
    val mockupimage = new MockupImage
    mockupimage.mockup_id := mockup_id
    mockupimage.uri := uri
    mockupimage.image_order := adapter.getCount
    mockupimage.image_title := "New screen"

    // Save the mockup
    future { mockupimage.save }
  }

  def removeImage(position: Int) = {
    // Currently selected mockup
    val cursor = (listView getItemAtPosition position).asInstanceOf[Cursor]

    // Remove the mockup image if the cursor is not null
    future { db.fromCursor[MockupImage](cursor).remove }
  }

  def saveTitle = {
    // Log what's happening
    info(s"Saving mockup $mockup_id")

    // Create the mockup instance
    val m = new Mockup
    m.id := mockup_id
    m.title := titleTextView.getText.toString

    // Dismiss keyboard
    titleTextView.clearFocus
    inputMethodManager.hideSoftInputFromWindow(
      titleTextView.getWindowToken, 0)

    // Save the mockup
    future { m.save }
  }

  def reloadAfter(f: => Future[_]) {
    f onComplete {
      case Failure(f) => f.printStackTrace; reload
      case Success(_) => reload
    }
  }

  // Show a mockup image
  def showImage(m: MockupImage) = {
    for (uri <- m.uri.value; id <- m.id.value) {
      future { loadBitmap(uri) } onSuccess {
        case Some(b) => runOnUiThread {
          // Set the current image
          screenView setImageBitmap b
          current_image_id = Some(id)

          // Flip to the image viewer if necessary
          if (flipper.getDisplayedChild == 0) {
            flipper.showNext
            getActionBar.hide
          }
        }
      }
    }
  }

  // Start showing off a mockup
  def startMockup = {
    if (adapter.getCount > 0) {
      // Set the state to "show"
      current_state = STATE_SHOW

      // Retrieve the first image
      val img = (adapter getItem 0).asInstanceOf[Cursor]
      val mimg = db.fromCursor[MockupImage](img)

      // Show it
      showImage(mimg)
    }
  }

  // List view adapter
  object adapter
  extends SModelAdapter[MockupImage](R.layout.listitem_mockupimage) {
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
      for (title <- mi.image_title.value)
        titleView setText title

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
    .onComplete { case _ => runOnUiThread { stopLoading }}
  }

  // Start and stop the loading Window spinner
  def startLoading = setProgressBarIndeterminateVisibility(true)
  def stopLoading = setProgressBarIndeterminateVisibility(false)
}

object MockupActivity {
  val MOCKUP_ID = "mockup_id"
  val MOCKUP_TITLE = "mockup_title"

  val MENU_EDIT_TITLE = 1
  val MENU_REMOVE = 2

  val PICK_IMAGE = 1

  val STATE_EDIT = 1
  val STATE_SHOW = 2
}
