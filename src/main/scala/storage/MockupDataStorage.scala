package com.github.fxthomas.mocka

import android.content.Context
import android.database.sqlite.SQLiteDatabase

import scala.concurrent._
import org.scaloid.common._

import android.graphics.Bitmap
import BitmapHelpers._

class Mockup extends Model {
  val title = StringField("title")
}

class MockupImage extends Model {

  val mockup = ForeignField[Mockup]("mockup_id")
  val image_title = StringField("image_title")
  val image_uri = StringField("image_uri")

  def image(implicit ctx: Context,
                     ec: ExecutionContext,
                     lru: SLruCache[String, Bitmap]) =
    for (uri <- image_uri.value)
      yield lru(uri, loadBitmap(uri))
}

class MockupTransition extends Model {
  val mockup = ForeignField[Mockup]("mockup_id")
  val image_from = ForeignField[MockupImage]("image_from")
  val image_to = ForeignField[MockupImage]("image_to")
  val x = FloatField("x")
  val y = FloatField("y")
  val size = FloatField("size")
}

class MockupOpenHelper(implicit ctx: Context)
extends SSQLiteOpenHelper("mockups", 1) {

  // Create the database
  override def onCreate(db: SQLiteDatabase) {
    createTable[Mockup](db)
    createTable[MockupTransition](db)
    createTable[MockupImage](db)
  }

  // Upgrade the database (TODO)
  override def onUpgrade(db: SQLiteDatabase, version_old: Int, version_new: Int) {
    // No upgrade implemented for now
    warn(s"Uwaah, tried to upgrade DB!")
  }
}
