package com.github.fxthomas.mocka

import android.os.Bundle
import android.content.Context
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

import scala.reflect._
import scala.reflect.runtime.universe.{Type, TypeTag, typeTag, typeOf, runtimeMirror}

import org.scaloid.common._

/**
 * Standard field
 */
class Field[T] {
  var value: Option[T] = None
  def <<(v: T) = { value = Some(v) }
  def clear = { value = None }
}
class PKIntField extends Field[Int]

trait ModelHelper {
  // Restrict use for SQLiteOpenHelper objects
  this: SQLiteOpenHelper =>

  // Writable database
  private var _db: Option[SQLiteDatabase] = None
  private implicit def db: SQLiteDatabase = _db match {
    case Some(d) => d
    case None => {
      val database = getWritableDatabase
      _db = Some(database)
      database
    }
  }

  def release = {
    this.close
    _db = None
  }

  // List fields
  def fields[T: TypeTag] = typeOf[T].members
    .filter { _.typeSignature <:< typeOf[Field[_]] }
    .map { _.asTerm }

  // Describe a field
  def fieldDescription(t: Type) = t match {
    case _ if t <:< typeOf[Field[Int]] => "INTEGER"
    case _ => "TEXT"
  }

  // Describe a field's extras
  def fieldExtras(t: Type) = t match {
    case _ if t <:< typeOf[PKIntField] => "PRIMARY KEY AUTOINCREMENT"
    case _ => ""
  }

  // List fields and their DB descriptions
  def fieldDescriptions[T: TypeTag] = fields[T].map {f =>
      val n = f.name.decoded.trim
      val d = fieldDescription(f.typeSignature)
      val e = fieldExtras(f.typeSignature)
      s"$n $d $e"
    }

  // List field values
  def fieldValues[T: TypeTag](t: T) = {
    // Create a mirror and find the ClassTag for t
    val mirror = runtimeMirror(t.getClass.getClassLoader)
    implicit val ctag = ClassTag[T](mirror.runtimeClass(typeOf[T]))

    // Find all the field values
    fields[T].flatMap { f =>
      val rf = mirror reflect t reflectField f
      val rn = f.name.decoded.trim

      rf.get.asInstanceOf[Field[_]].value match {
        case Some(s) => Some(rn, s)
        case None => None
      }
    }
  }

  // Create a database
  def createTable[T: TypeTag](name: String, db: SQLiteDatabase) = {
    // Find type definitions
    val schemaString = fieldDescriptions[T].mkString(",")

    // Execute the query
    db.execSQL (s"CREATE TABLE `$name` ($schemaString);")
  }

  // Create new item
  def saveWith[T: TypeTag](name: String, item: T, db: SQLiteDatabase) = {
    val values = fieldValues(item).toList
    if (values.length != 0) {
      val valueNames = values map { _._1 } mkString ","
      val valueValues = values map { "\"" + _._2 + "\"" } mkString ","
      db.execSQL (s"INSERT INTO `$name`($valueNames) VALUES($valueValues);")
    }
  }

  // Convenience methods
  def save[T: TypeTag](name: String, item: T) = saveWith(name, item, db)
}

/**
 * My models
 */
class Mockup {
  val id = new PKIntField
  val title = new Field[String]
}

class MockupImage {
  val mockup_id = new Field[Int]
  val image_order = new Field[Int]
  val uri = new Field[String]
}

/**
 * Data storage class
 */
class MockupDataStorage(implicit ctx: Context)
extends SQLiteOpenHelper(ctx, "mockups", null, 1)
with ModelHelper {

  // Set the default logcat tag
  implicit val tag = LoggerTag("MockupDataStorage")

  // Create the database
  override def onCreate(db: SQLiteDatabase) {
    info(s"Creating DB at " + System.nanoTime())
    createTable[Mockup]("mockup", db)
    createTable[MockupImage]("mockup_image", db)
    info(s"Finished creating DB at " + System.nanoTime())
  }

  // Upgrade the database (TODO)
  override def onUpgrade(db: SQLiteDatabase, version_old: Int, version_new: Int) {
    // No upgrade implemented for now
    warn(s"Uwaah, tried to upgrade DB!")
  }
}
