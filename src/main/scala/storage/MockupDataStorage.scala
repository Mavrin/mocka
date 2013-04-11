package com.github.fxthomas.mocka

import android.content.Context
import android.database.sqlite.SQLiteDatabase

import org.scaloid.common._

trait _Mockup extends Model {
  val title = StringField("title")
}

trait _MockupImage extends Model {
  val mockup_id = LongField("mockup_id")
  val image_order = IntField("image_order")
  val uri = StringField("uri")
}

class Mockup extends _Mockup
class MockupImage extends _MockupImage
class MockupWithImage extends _Mockup with _MockupImage

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
