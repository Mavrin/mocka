package com.github.fxthomas.mocka

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.Cursor

import org.scaloid.common._

import scala.reflect.{ClassTag,classTag}

import scala.collection.mutable.{Map => MutableMap}

abstract class Field[T](val sqlName: String, val sqlType: String)(implicit val model: Model) {

  // Field value, if exists
  var value: Option[T] = None

  // Update model
  model.fields(sqlName) = this

  // Store something inside the field
  def :=(v: T) = { value = Some(v) }
  def clear = { value = None }

  // Custom inclusion check for cursors
  protected def inCursor (c: Cursor) = {
    try { Some(c.getColumnIndexOrThrow(sqlName)) }
    catch { case _: IllegalArgumentException => None }
  }

  // Custom inclusion check for ContentValues objects
  protected def inContentValues (c: ContentValues) =
    c containsKey sqlName

  // Custom cursor getter (to be defined)
  protected def fromCursor (c: Cursor, cid: Int): T

  // Custom ContentValues getter (defaults to converting the result of
  // getAsObject to the required type)
  protected def fromContentValues (c: ContentValues, cid: String): T =
    (c get cid).asInstanceOf[T]

  // Put a value inside the ContentValues object
  protected def toContentValues (c: ContentValues, cid: String, v: T)

  // Store something inside the field from a ContentValues object
  def <<(cv: ContentValues) =
    if (inContentValues(cv)) this := fromContentValues(cv, sqlName)

  // Store something from a cursor
  def <<(c: Cursor) =
    for (cid <- inCursor(c)) this := fromCursor(c, cid)

  // Store the field value inside the ContentValues object, if exists
  def >>(cv: ContentValues) = for (v <- value) toContentValues(cv, sqlName, v)
}

case class StringField(override val sqlName: String, override val sqlType: String = "TEXT")(implicit override val model: Model)
extends Field[String](sqlName, sqlType) {
  override def fromCursor(c: Cursor, cid: Int) = c getString cid
  override def fromContentValues(c: ContentValues, cid: String) = c getAsString cid
  override def toContentValues(c: ContentValues, cid: String, v: String) = c.put(cid, v)
}

case class IntField(override val sqlName: String, override val sqlType: String = "INTEGER")(implicit override val model: Model)
extends Field[java.lang.Integer](sqlName, sqlType) {
  override def fromCursor(c: Cursor, cid: Int): java.lang.Integer = c getInt cid
  override def fromContentValues(c: ContentValues, cid: String) = c getAsInteger cid
  override def toContentValues(c: ContentValues, cid: String, v: java.lang.Integer) = c.put(cid, v: java.lang.Integer)
}

case class LongField(override val sqlName: String, override val sqlType: String = "INTEGER")(implicit override val model: Model)
extends Field[java.lang.Long](sqlName, sqlType) {
  override def fromCursor(c: Cursor, cid: Int) = c getLong cid
  override def fromContentValues(c: ContentValues, cid: String) = c getAsLong cid
  override def toContentValues(c: ContentValues, cid: String, v: java.lang.Long) = c.put(cid, v: java.lang.Long)
}

trait Model {

  // List of fields
  val fields = MutableMap[String, Field[_]]()

  // Table name
  val tableName = this.getClass.getName.replace(".","_").replace("$","__")

  // Logger tag
  implicit val tag = LoggerTag(s"DB/$tableName")

  // Current implicit model
  implicit val model = this

  // Create primary key
  val id = LongField("id", "INTEGER PRIMARY KEY AUTOINCREMENT")

  // Save model object
  def save(implicit db: SOpenHelper): Long = {
    // Create ContentValues
    val cv = new ContentValues

    // Put the fields
    for (f <- fields.values) f >> cv

    // Save into the DB
    id.value match {
      case Some(i) => {
        // Update the element
        db.rw.update(tableName, cv, "id = ?", Array(i.toString))

        // Return the ID
        return i
      }
      case None => {
        // Try to insert the element
        val newid = db.rw.insert(tableName, "id", cv)

        // If it fails, say something
        if (newid == -1) warn(s"Unable to save $tableName object")

        // If it didn't fail, update the ID
        else id := newid

        // Return the ID
        return newid
      }
    }
  }
}

trait SOpenHelper {
  // Restrict use for SQLiteOpenHelper objects
  this: SQLiteOpenHelper =>

  // Table name
  val helperName = this.getClass.getName.replace(".","_").replace("$","__")

  // Logger tag
  implicit val tag = LoggerTag(s"SQL/$helperName")

  // Common shortcuts
  def rw = getWritableDatabase
  def ro = getReadableDatabase

  // Create a new Model object
  def create[M <: Model : ClassTag]: M = { classTag[M].runtimeClass.newInstance.asInstanceOf[M] }

  // Create a new Model object from a Cursor
  def fromCursor[M <: Model : ClassTag](c: Cursor):M = {

    // Create a new model
    val model = create[M]

    // Fill the model
    for (f <- model.fields.values) f << c

    // Return that model
    return model
  }

  def createTable[M <: Model : ClassTag](db: SQLiteDatabase = rw) = {
    // Create a dummy object with empty fields
    val dummy = create[M]

    // Read all the fieldz!
    val schema = dummy.fields.values map
      { f => f.sqlName + " " + f.sqlType }

    // Create the schema string
    val schemaName = dummy.tableName
    val schemaString = schema mkString ", "

    // Create the DB
    db.execSQL(s"CREATE TABLE `$schemaName` ($schemaString);")
  }

  // Retrieve all the elements in DB
  def all[M <: Model : ClassTag] = {
    // Create a dummy object with empty fields
    val dummy = create[M]
    val table = dummy.tableName

    // Execute query
    val q = ro.query(true, table, null, null, null, null, null, null, null)

    // Read data
    (0 to q.getCount-1) map { p =>
      q moveToPosition p
      fromCursor[M](q)
    } toList
  }

  // Find all model objects that satisfy a condition
  def findBy[M <: Model : ClassTag, T](f: String, value: T) = {
    // Create a dummy object with empty fields
    val dummy = create[M]
    val table = dummy.tableName
    val field = dummy.fields(f)

    // Execute query
    val q = ro.query(true, table, null, s"$f = ?", Array(value.toString), null, null, null, null)

    // Read data
    (0 to q.getCount-1) map { p =>
      q moveToPosition p
      fromCursor[M](q)
    } toList
  }
}
