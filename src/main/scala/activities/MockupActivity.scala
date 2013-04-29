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

import org.scaloid.common._

class MockupActivity extends SActivity with TypedActivity {
  import MockupActivity._
  import ActivityHelpers._
  import SCursor.Implicits._

  // Image cache
  lazy implicit val lruImages = new SLruCache[String, Bitmap](15)

  // Default database
  lazy implicit val db = new MockupOpenHelper

  // Retrieve the mockup ID and title
  def mockup_id = getIntent.getLongExtra(MOCKUP_ID, 0)
  def mockup_title = getIntent.getStringExtra(MOCKUP_TITLE)

  // Currently displayed image
  var current_image: Option[MockupImage] = None

  // Current state
  var current_state = STATE_EDIT

  // List view holding the mockup images
  lazy val listView = findView(TR.screens)
  lazy val screenView = findView(TR.screen)
  lazy val flipper = findView(TR.flipper)
  lazy val titleTextView = new EditText(implicitly[Context])

  override def onBackPressed() {
    if (flipper.getDisplayedChild > 0) {
      getActionBar.show
      flipper.showPrevious
      current_image = None
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
      (x: Float, y: Float) => {
        current_state match {
          case STATE_EDIT => addTransition(0, x, y, 50.f)
          case STATE_SHOW => {
          }
          case _ => ()
        }
      }
    }

    // Set the list adapter
    listView.addHeaderView(titleTextView, null, false)
    listView setAdapter adapter
    listView onItemClick {
      (parent: AdapterView[_], view: View, position: Int, id: Long) => {
        user_showImage(parent.get[MockupImage](position))
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

      case R.id.screen => {
        menu setHeaderTitle "Select transition"
        menu.add (1, 0, 0, "Transition 1")
        menu.add (1, 1, 1, "Transition 2")
      }
    }
  }

  // Context menu item click
  override def onContextItemSelected(item: MenuItem): Boolean = {
    item.getGroupId match {
      case 0 => {
        // Retrieve information about the selected list item
        val info = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]

        // Execute the right action for the selected menu item
        item.getItemId match {
          case MENU_REMOVE => reloadAfter { removeImage(info.position) }
          case MENU_EDIT_TITLE => {
            // Currently selected mockup
            val mi = listView.get[MockupImage](info.position)

            for (t <- mi.image_title) InputDialog.show("Edit title", t) {
              (s: String) => {
                mi.image_title := s
                reloadAfter { future { mi.save } }
              }
            }
          }
        }

        // We did something, so return true
        return true
      }

      case _ => return false
    }
  }

  override def onPause = {
    for (_ <- saveTitle) db.close
    super.onPause
  }

  override def onResume = {
    reload
    super.onResume
  }

  override def onDestroy = {
    for (_ <-saveTitle) db.close
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
      case R.id.ui_new => user_selectImage
      case R.id.ui_play => user_toggleState
      case android.R.id.home => finish
    }

    return true
  }

  /*********************
   * Available actions *
   *********************/

  def user_selectImage {
    // Create an intent showing the gallery
    val intent = new Intent
    intent setType "image/*"
    intent setAction Intent.ACTION_GET_CONTENT

    // Start the intent
    startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE)
  }

  // Show a mockup image
  def user_showImage(m: MockupImage) = {
    for (fimg <- m.image; img <- fimg;
         id <- m.id.value) runOnUiThread {

      // Set the current image
      screenView setImageBitmap img
      current_image = Some(m)

      // Flip to the image viewer if necessary
      if (flipper.getDisplayedChild == 0) {
        flipper.showNext
        getActionBar.hide
      }
    }
  }

  // Show a mockup image from the adapter ID
  def user_showImageId(i: Int) = {
    user_showImage(listView.get[MockupImage](i))
  }

  // Toggle the state between Edition and Slideshow
  def user_toggleState = {
    current_state = if (current_state == STATE_EDIT) STATE_SHOW else STATE_EDIT
  }

  def addTransition(to: Long, x: Float, y: Float, size: Float) = {
    // Inform the user
    info(s"Saving transition for $mockup_id (at $x, $y)")

    for (i <- current_image;
         id <- i.id) {

      // Create the transition
      val v = new MockupTransition
      v.mockup_id := mockup_id
      v.image_from := id
      v.image_to := to
      v.x := x
      v.y := y
      v.size := size

      // Saave the transition
      future { v.save }
    }
  }

  def addImage(uri: String) = {
    // Log what's happening
    info(s"Creating new mockup image with URI $uri")

    // Create a new mockup image
    val mockupimage = new MockupImage
    mockupimage.mockup_id := mockup_id
    mockupimage.image_uri := uri
    mockupimage.image_title := "New screen"

    // Save the mockup
    future { mockupimage.save }
  }

  def removeImage(position: Int) = {
    future { listView.get[MockupImage](position).remove }
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


  // Reload the mockups
  def reload = {
    // Start the loading spinner
    runOnUiThread {
      titleTextView setText mockup_title
      startLoading
    }

    // Reload things
    for (_ <- adapter.reload) runOnUiThread { stopLoading }
  }

  // Start and stop the loading Window spinner
  def startLoading = setProgressBarIndeterminateVisibility(true)
  def stopLoading = setProgressBarIndeterminateVisibility(false)

  // List view adapter for MockupImage objects
  object adapter
  extends SModelAdapter[MockupImage](R.layout.listitem_mockupimage) {

    override def query =
      db.findBy[MockupImage, Long]("mockup_id", mockup_id)

    def update(v: View, context: Context, mi: MockupImage) {

      // Find the text view
      val vTitle = v.findViewById(R.id.title).asInstanceOf[TextView]
      val vImage = v.findViewById(R.id.image).asInstanceOf[AsyncImageView]

      // Set the title and image
      for (title <- mi.image_title.value) vTitle setText title
      for (uri <- mi.image_uri) vImage setImageUri uri
    }
  }
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
