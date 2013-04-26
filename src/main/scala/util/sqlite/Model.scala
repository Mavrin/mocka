package com.github.fxthomas.mocka

import android.content.ContentValues
import android.database.Cursor

import scala.collection.mutable.MutableList

trait Model {

  // Unable to save
  class DBIOError(s: String) extends Exception(s)

  // List of fields
  val fields = MutableList[Field[_]]()

  // Table name
  val tableName = this.getClass.getName.replace(".","_").replace("$","__")

  // Current implicit model
  implicit val model = this

  // Create primary key
  val id = LongField("_id", "INTEGER PRIMARY KEY AUTOINCREMENT")

  // Save model object
  def save(implicit db: SSQLiteOpenHelper): Long = {
    // Create ContentValues
    val cv = new ContentValues

    // Put the fields
    this >> cv

    // Save into the DB
    id.value match {
      case Some(i) => {
        // Update the element
        db.rw.update(tableName, cv, "_id = ?", Array(i.toString))

        // Return the ID
        return i
      }
      case None => {
        // Try to insert the element
        val newid = db.rw.insert(tableName, "_id", cv)

        // If it fails, say something, else update the ID
        if (newid >= 0) id := newid
        else throw new DBIOError("Unable to save model")

        // Return the ID
        return newid
      }
    }
  }

  def remove(implicit db: SSQLiteOpenHelper) = {
    for (i <- id)
      db.rw.delete(tableName, "_id = ?", Array(i.toString))
  }

  // Fill a model from a Cursor
  def <<(c: Cursor): this.type = {
    for (f <- fields) f << c
    this
  }

  // Fill a ContentValues object with the model
  def >>(cv: ContentValues) = for (f <- fields) f >> cv

  // Returns a map of the values inside the fields
  def asMap = fields.flatMap(f =>
    f.value match {
      case Some(v) => Some(f.sqlName, v)
      case None => None
    }
  ).toMap
}

