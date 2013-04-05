package com.github.fxthomas.mocka

import android.app.Activity
import android.os.Bundle
import android.view.{Menu, MenuInflater, MenuItem}

import org.scaloid.common._

class MockupActivity extends SActivity with TypedActivity {
  import MockupActivity._

  override def onCreate(bundle: Bundle) = {
    // Load UI
    super.onCreate(bundle)
    setContentView(R.layout.ui_mockup)

    // Load current mockup
    //val mockup_id = getIntent.getString(INTENT_MOCKUP_ID)
  }

  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    getMenuInflater.inflate(R.menu.ui_mockuplist, menu)
    return true
  }

  override def onOptionsItemSelected (item: MenuItem): Boolean = item.getItemId match {
    case R.id.ui_new => toast("New image"); true
  }
}

object MockupActivity {
  val MOCKUP_ID = "mockup_id"
}
