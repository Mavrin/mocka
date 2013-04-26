package com.github.fxthomas.mocka

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.DatabaseErrorHandler
import android.database.Cursor

import org.scaloid.common._

import scala.reflect.{ClassTag,classTag}

abstract class SSQLiteOpenHelper(
  name: String,
  version: Int,
  factory: SQLiteDatabase.CursorFactory = null,
  errorHandler: DatabaseErrorHandler = null)
  (implicit ctx: Context)
extends SQLiteOpenHelper(ctx, name, factory, version, errorHandler) {

  // Import implicits
  import SSQLiteOpenHelper.Implicits._

  // Table name
  val helperName = this.getClass.getName.replace(".","_").replace("$","__")

  // Logger tag
  implicit val tag = LoggerTag(s"SQL/$helperName")

  // Common shortcuts
  def rw = getWritableDatabase
  def ro = getReadableDatabase

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
  (fields: Array[Field[_]] = null, selection: String = null, selectionArgs: Array[String] = null, orderBy: String = null, limit: String = null) = {

    // Create a dummy object with empty field
    val dummy = create[M]
    val table = dummy.tableName
    val fieldOpt = if (fields != null) fields map { _.sqlName } else null

    // Execute query
    ro.query(
      true,
      dummy.tableName,
      fieldOpt,
      selection,
      selectionArgs,
      null,
      null,
      orderBy,
      limit
    )
  }

  // Retrieve all the elements in DB
  def all[M <: Model : ClassTag] = query[M]()

  // Find all model objects that satisfy a condition
  def findBy[M <: Model : ClassTag, T](f: String, value: T, orderBy: String = null) =
    query[M](null, s"$f = ?", Array(value.toString), orderBy)

  // Find a model by ID
  def findById[M <: Model : ClassTag](cid: Long, orderBy: String = null) =
    query[M](null, s"_id = ?", Array(cid.toString), orderBy)
}

// Some useful implicit conversions
object SSQLiteOpenHelper {
  object Implicits {
    def create[T <: Model : ClassTag]: T =
      classTag[T].runtimeClass.newInstance.asInstanceOf[T]

    def tableName[M <: Model : ClassTag] =
      create[M].tableName

    def fields[M <: Model : ClassTag] =
      create[M].fields

    class SCursor(c: Cursor) {
      def get[T <: Model : ClassTag](p: Int): T = {
        c moveToPosition p
        c.as[T]
      }

      def as[T <: Model : ClassTag]: T =
        create[T] << c

      def asList[M <: Model : ClassTag]: List[M] = {
        // Move the Cursor to the first position
        c.moveToFirst

        // Get all the elements inside the cursor and make a list
        (0 to c.getCount-1).map { p =>
          c moveToPosition p
          c.as[M]
        }.toList
      }
    }

    implicit def richCursorConversion(c: Cursor): SCursor = new SCursor(c)
  }
}
