package com.github.fxthomas.mocka

import android.database.Cursor
import android.content.ContentValues
import scala.reflect.ClassTag

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
  def <<(cv: ContentValues) = {
    if (inContentValues(cv)) this := fromContentValues(cv, sqlName)
    this
  }

  // Store something from a cursor
  def <<(c: Cursor) = {
    for (cid <- inCursor(c)) this := fromCursor(c, cid)
    this
  }

  // Store the field value inside the ContentValues object, if exists
  def >>(cv: ContentValues) = for (v <- value) toContentValues(cv, sqlName, v)

  // Map function to be able to use `for`
  def map[B](f: T => B) = value map f
  def flatMap[B](f: T => Option[B]) = value flatMap f
  def filter(f: T => Boolean) = value filter f
  def foreach = value.foreach _
}
