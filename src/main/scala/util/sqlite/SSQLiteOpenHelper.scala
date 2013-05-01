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
  import SCursor.Implicits._

  // Table name
  val helperName = this.getClass.getName.replace(".","_").replace("$","__")

  // Logger tag
  implicit val tag = LoggerTag(s"SQL/$helperName")

  // Common shortcuts
  def rw = getWritableDatabase
  def ro = getReadableDatabase

  def createTable[M <: Model : ClassTag](db: SQLiteDatabase = rw) = {
    // Create a dummy object with empty fields
    val dummy = Model.create[M]

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
  (fields: Array[String] = null, selection: String = null, selectionArgs: Array[String] = null, orderBy: String = null, limit: String = null) = {

    // Create a dummy object with empty field
    val dummy = Model.create[M]
    val table = dummy.tableName

    // Find the fields we want
    val availableFields = dummy.fields map { _.sqlName }
    val usedFields =
      if (fields == null) Array("*", s"_id AS ${table}__id")
      else fields filter { availableFields contains _ }

    // Execute query
    SCursor[M](ro.query(
      true,
      dummy.tableName,
      usedFields,
      selection,
      selectionArgs,
      null,
      null,
      orderBy,
      limit
    ))
  }

  def firstJoin[M1 <: Model : ClassTag, M2 <: Model : ClassTag]
  (joinField: String, selection: String = null, selectionArgs: Array[String] = null, orderBy: String = null, limit: String = null) = {

    // Create a dummy object with empty field
    val d1 = Model.create[M1]
    val d2 = Model.create[M2]
    val t1 = d1.tableName
    val t2 = d2.tableName

    // Find the fields we want
    val f1 = d1.fields map { f => t1 + "." + f.sqlName + " as " + f.sqlFullName }
    val f2 = d2.fields map { f => t2 + "." + f.sqlName + " as " + f.sqlFullName }
    val fields = (f1 ++ f2 ++ List(s"$t2._id AS _id")).toArray

    // Execute query
    SCursor[M1](ro.query(
      true,
      s"$t2 LEFT OUTER JOIN $t1 ON $t2._id = $t1.$joinField",
      fields,
      selection,
      selectionArgs,
      s"${t2}__id",
      null,
      orderBy,
      limit
    ))
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
