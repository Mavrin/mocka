package com.github.fxthomas.mocka

import android.app.Activity
import android.os.Bundle
import android.view.{Menu, MenuInflater, MenuItem}

import org.scaloid.common._

class MockupListActivity extends SActivity with TypedActivity {
  lazy val storage = new MockupDataStorage

  override def onCreate(bundle: Bundle) = {
    super.onCreate(bundle)
    setContentView(R.layout.ui_mockuplist)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockuplist, menu)
    return true
  }

  override def onPause = {
    storage.release
    super.onPause
  }

  override def onOptionsItemSelected (item: MenuItem): Boolean = item.getItemId match {
    case R.id.ui_new => {
      // Create a new mockup
      val mockup = new Mockup
      mockup.title << "Hello, world"

      // Store it in the DB
      storage.save("mockup", mockup)

      // Create an intent
      val intent = SIntent[MockupActivity]
      //intent.putExtra(MockupActivity.MOCKUP_ID, new_mockup.id)

      // Start the activity
      startActivity(intent)
      return true
    }
  }
}
