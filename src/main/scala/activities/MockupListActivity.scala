package com.github.fxthomas.mocka

import android.app._
import android.os._
import android.view._
import android.view.inputmethod._
import android.widget._
import android.content._
import android.database.Cursor
import android.graphics._
import android.net._

import scala.concurrent._
import scala.util.{Failure, Success}
import ExecutionContext.Implicits.global

import org.scaloid.common._

class MockupListActivity extends SActivity with TypedActivity {
  import MockupListActivity._
  import ActivityHelpers._
  import SCursor.Implicits._

  // Current database
  lazy implicit val db = new MockupOpenHelper

  // Image cache
  lazy implicit val lruImages = new SLruCache[String, Bitmap](15)

  // The current list view holding mockups
  lazy val listView = findView(TR.mockups)

  // List view adapter for mockup objects
  object adapter
  extends SModelAdapter[MockupImage](R.layout.listitem_mockup) {

    override def query =
      db.firstJoin[MockupImage, Mockup]("mockup_id")

    def update(v: View, context: Context, m: MockupImage) {
      // Find the text view
      val vTitle = v.findViewById(R.id.title).asInstanceOf[TextView]
      val vImage = v.findViewById(R.id.preview).asInstanceOf[AsyncImageView]

      // Set the title and image
      for (mm <- m.mockup.get; title <- mm.title) vTitle setText title
      for (uri <- m.image_uri) vImage setImageUri uri
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
    for (_ <- adapter.reload) runOnUiThread { stopLoading }
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
    listView onItemClick {
      (parent: AdapterView[_], view: View, position: Int, id: Long) => {
        showMockup(parent.get[Mockup](position))
      }
    }

    // Register the context menu for the list
    this registerForContextMenu listView
  }

  // Option menu creation
  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockuplist, menu)
    return true
  }

  // Context menu creation
  override def onCreateContextMenu(menu: ContextMenu, v: View, info: ContextMenu.ContextMenuInfo) = {
    v.getId match {
      case R.id.mockups => {
        val tinfo = info.asInstanceOf[AdapterView.AdapterContextMenuInfo]
        menu setHeaderTitle "Edit Mockup"
        menu.add (0, 0, 0, "Remove")
      }
    }
  }

  // Context menu item click
  override def onContextItemSelected(item: MenuItem) = {
    // Currently selected menu item
    val tinfo = item.getMenuInfo.asInstanceOf[AdapterView.AdapterContextMenuInfo]

    // Currently selected mockup
    val model = listView.get[Mockup](tinfo.position)

    // Run the action
    item.getItemId match {
      case 0 => { model.remove; reload; true }
      case _ => false
    }
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

      // Disable the menu item until we're done
      item setEnabled false

      // Create a new mockup
      val mockup = new Mockup title("Hello, world")

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

object MockupListActivity {
}
