package com.github.fxthomas.mocka

import android.content.ContentValues
import android.database.Cursor
import android.util.Log

import scala.collection.mutable.MutableList
import scala.reflect.{ClassTag, classTag}

trait BaseModel {
  def <<(c: Cursor): this.type
  def >>(cv: ContentValues)
}

trait Model extends BaseModel {
  abstract class Field[T](val sqlName: String, val sqlType: String) {
    // Field value, if exists
    var value: Option[T] = None

    // Long SQL name (for disambiguation)
    val sqlFullName = Model.this.tableName + "_" + sqlName

    // Update model
    Model.this.fields += this

    // Store something inside the field
    def apply(v: T): Model.this.type = { value = Some(v); Model.this }

    def clear = { value = None }

    // Custom inclusion check for cursors
    protected def inCursor (c: Cursor) = {
      try { Some(c.getColumnIndexOrThrow(sqlFullName)) }
      catch { case _: IllegalArgumentException => {
        try { Some(c.getColumnIndexOrThrow(sqlName)) }
        catch { case _: IllegalArgumentException => None }
      }}
    }

    // Check for custom field name
    protected def inCursor (c: Cursor, fieldName: String) = {
      try { Some(c.getColumnIndexOrThrow(fieldName)) }
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

    // Store something from a cursor
    def <<(c: Cursor) = {
      for (cid <- inCursor(c)) this(fromCursor(c, cid))
      Model.this
    }

    // Store the field value inside the ContentValues object, if exists
    def >>(cv: ContentValues) = for (v <- value) toContentValues(cv, sqlName, v)

    // Map function to be able to use `for`
    def map[B](f: T => B) = value map f
    def flatMap[B](f: T => Option[B]) = value flatMap f
    def filter(f: T => Boolean) = value filter f
    def foreach = value.foreach _
  }
  case class StringField(override val sqlName: String, override val sqlType: String = "TEXT")
  extends Field[String](sqlName, sqlType) {
    override def fromCursor(c: Cursor, cid: Int) = c getString cid
    override def fromContentValues(c: ContentValues, cid: String) = c getAsString cid
    override def toContentValues(c: ContentValues, cid: String, v: String) = c.put(cid, v)
  }

  case class IntField(override val sqlName: String, override val sqlType: String = "INTEGER")
  extends Field[java.lang.Integer](sqlName, sqlType) {
    override def fromCursor(c: Cursor, cid: Int): java.lang.Integer = c getInt cid
    override def fromContentValues(c: ContentValues, cid: String) = c getAsInteger cid
    override def toContentValues(c: ContentValues, cid: String, v: java.lang.Integer) = c.put(cid, v: java.lang.Integer)
  }

  case class FloatField(override val sqlName: String, override val sqlType: String = "INTEGER")
  extends Field[java.lang.Float](sqlName, sqlType) {
    override def fromCursor(c: Cursor, cid: Int) = c getFloat cid
    override def fromContentValues(c: ContentValues, cid: String) = c getAsFloat cid
    override def toContentValues(c: ContentValues, cid: String, v: java.lang.Float) = c.put(cid, v: java.lang.Float)
  }

  case class LongField(override val sqlName: String, override val sqlType: String = "INTEGER")
  extends Field[java.lang.Long](sqlName, sqlType) {
    override def fromCursor(c: Cursor, cid: Int) = c getLong cid
    override def fromContentValues(c: ContentValues, cid: String) = c getAsLong cid
    override def toContentValues(c: ContentValues, cid: String, v: java.lang.Long) = c.put(cid, v: java.lang.Long)
  }

  case class ForeignField[M <: Model : ClassTag](override val sqlName: String, override val sqlType: String = "INTEGER")
  extends Field[java.lang.Long](sqlName, sqlType) {
    import SCursor.Implicits._

    override def fromCursor(c: Cursor, cid: Int) = c getLong cid
    override def fromContentValues(c: ContentValues, cid: String) = c getAsLong cid
    override def toContentValues(c: ContentValues, cid: String, v: java.lang.Long) = c.put(cid, v: java.lang.Long)

    protected var __underlying: Option[M] = None
    protected val __underlyingTableName = Model.create[M].tableName

    def get(implicit db: SSQLiteOpenHelper): Option[M] = {
      __underlying match {
        case Some(m) => Some(m)
        case None => {
          __underlying = value flatMap { v => Option[M](db.findById[M](v)) }
          __underlying
        }
      }
    }

    def set(m: M) = __underlying = Some(m)

    // Store something from a cursor
    override def <<(c: Cursor) = {
      for (cid <- inCursor(c)) this(fromCursor(c, cid))
      for (cid <- inCursor(c, __underlyingTableName + "__id"))
        __underlying = Some((Model.create[M] << c).id(fromCursor(c, cid)))

      Model.this
    }

    // Map function to be able to use `for`
    def map[B](f: M => B)(implicit db: SSQLiteOpenHelper) =
      get map f
    def flatMap[B](f: M => Option[B])(implicit db: SSQLiteOpenHelper) =
      get flatMap f
    def filter(f: M => Boolean)(implicit db: SSQLiteOpenHelper) =
      get filter f
    def foreach(implicit db: SSQLiteOpenHelper) =
      get.foreach _
  }

  // Unable to save
  class DBIOError(s: String) extends Exception(s)

  // List of fields
  val fields = MutableList[Field[_]]()

  // Table name
  val tableName = this.getClass.getName.replace(".","_").replace("$","__")

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
        db.rw.update(tableName, cv, s"$tableName._id = ?", Array(i.toString))

        // Return the ID
        return i
      }
      case None => {
        // Try to insert the element
        val newid = db.rw.insert(tableName, "$tableName._id", cv)

        // If it fails, say something, else update the ID
        if (newid >= 0) id(newid)
        else throw new DBIOError("Unable to save model")

        // Return the ID
        return newid
      }
    }
  }

  def remove(implicit db: SSQLiteOpenHelper) = {
    for (i <- id)
      db.rw.delete(tableName, s"$tableName._id = ?", Array(i.toString))
  }

  // Fill a model from a Cursor
  override def <<(c: Cursor): this.type = {
    for (f <- fields) f << c
    this
  }

  // Fill a ContentValues object with the model
  override def >>(cv: ContentValues) = for (f <- fields) f >> cv
}

object Model {
  def create[T <: BaseModel : ClassTag]: T =
    classTag[T].runtimeClass.newInstance.asInstanceOf[T]

  def tableName[M <: Model : ClassTag] =
    create[M].tableName

  def fields[M <: Model : ClassTag] =
    create[M].fields
}
