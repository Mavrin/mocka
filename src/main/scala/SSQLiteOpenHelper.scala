package com.github.fxthomas.mocka

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.DatabaseErrorHandler
import android.database.Cursor

import org.scaloid.common._

import scala.reflect.{ClassTag,classTag}

import scala.collection.mutable.MutableList

abstract class Field[T](val sqlName: String, val sqlType: String)(implicit val model: Model) {

  // Field value, if exists
  var value: Option[T] = None

  // Update model
  model.fields += this

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
  val fields = MutableList[Field[_]]()

  // Table name
  val tableName = this.getClass.getName.replace(".","_").replace("$","__")

  // Logger tag
  implicit val tag = LoggerTag(s"DB/$tableName")

  // Current implicit model
  implicit val model = this

  // Create primary key
  val id = LongField("id", "INTEGER PRIMARY KEY AUTOINCREMENT")

  // Save model object
  def save(implicit db: SSQLiteOpenHelper): Long = {
    // Create ContentValues
    val cv = new ContentValues

    // Put the fields
    for (f <- fields) f >> cv

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

  // Fill a model from a Cursor
  def <<(c: Cursor) = for (f <- fields) f << c

  // Returns a map of the values inside the fields
  def asMap = fields. flatMap(f =>
    f.value match {
      case Some(v) => Some(f.sqlName, v)
      case None => None
    }
  ).toMap
}

abstract class SSQLiteOpenHelper(
  name: String,
  version: Int,
  factory: SQLiteDatabase.CursorFactory = null,
  errorHandler: DatabaseErrorHandler = null)
  (implicit ctx: Context)
extends SQLiteOpenHelper(ctx, name, factory, version, errorHandler) {

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
    model << c

    // Return that model
    return model
  }

  def createTable[M <: Model : ClassTag](db: SQLiteDatabase = rw) = {
    // Create a dummy object with empty fields
    val dummy = create[M]

    // Read all the fieldz!
    val schema = dummy.fields map
      { f => f.sqlName + " " + f.sqlType }

    // Create the schema string
    val schemaName = dummy.tableName
    val schemaString = schema mkString ", "

    // Create the DB
    db.execSQL(s"CREATE TABLE `$schemaName` ($schemaString);")
  }

  // Run a query
  def query[M <: Model : ClassTag]
  (fields: Array[Field[_]] = null, selection: String = null, selectionArgs: Array[String] = null, limit: String = null) = {

    // Create a dummy object with empty field
    val dummy = create[M]
    val table = dummy.tableName

    // Execute query
    ro.query(
      true,
      dummy.tableName,
      fields map { _.sqlName},
      selection,
      selectionArgs,
      null,
      null,
      null,
      limit
    )
  }

  // Retrieve all the elements in DB
  def all[M <: Model : ClassTag] = query[M]()

  // Find all model objects that satisfy a condition
  def findBy[M <: Model : ClassTag, T](f: String, value: T) =
    query[M](null, s"$f = ?", Array(value.toString))

  // Retrieve all the elements inside a cursor
  def retrieve[M <: Model : ClassTag](q: Cursor) = {
    // Move the Cursor to the first position
    q.moveToFirst

    // Get all the elements inside the cursor and make a list
    (0 to q.getCount-1).map { p =>
      q moveToPosition p
      fromCursor[M](q)
    }.toList
  }
}
