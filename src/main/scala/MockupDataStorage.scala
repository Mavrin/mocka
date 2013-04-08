package com.github.fxthomas.mocka

import android.os.Bundle
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

import org.scaloid.common._

class Mockup extends Model {
  val title = StringField("title")
}

class MockupImage extends Model {
  val mockup_id = IntField("mockup_id")
  val image_order = IntField("image_order")
  val uri = StringField("uri")
}

class MockupOpenHelper(implicit ctx: Context)
extends SSQLiteOpenHelper("mockups", 1) {

  // Create the database
  override def onCreate(db: SQLiteDatabase) {
    createTable[Mockup](db)
    createTable[MockupImage](db)
  }

  // Upgrade the database (TODO)
  override def onUpgrade(db: SQLiteDatabase, version_old: Int, version_new: Int) {
    // No upgrade implemented for now
    warn(s"Uwaah, tried to upgrade DB!")
  }
}
