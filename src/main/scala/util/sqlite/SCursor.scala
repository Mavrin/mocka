package com.github.fxthomas.mocka

import android.database.Cursor
import android.widget.AdapterView
import android.widget.CursorAdapter

import scala.collection.GenTraversableOnce
import scala.collection.mutable.ListBuffer
import scala.reflect.{ClassTag,classTag}

class SCursor[M <: BaseModel : ClassTag](val cursor: Cursor) {
  import SCursor.Implicits._

  var __list = List[M]()

  def get: M = Model.create[M] << cursor

  def toList: List[M] = {
    synchronized {
      if (__list.length != cursor.getCount) {
        // Clear the list
        val buffer = new ListBuffer[M]

        // Move the cursor to the first position
        cursor.moveToFirst

        // Fill the list
        while (!cursor.isAfterLast && !cursor.isBeforeFirst)
          buffer += get

        // Store the list
        __list = buffer.toList
      }
    }

    // Return the list
    return __list
  }

  // For semantics
  def map[B](f: M => B) = toList map f
  def flatMap[B](f: M => GenTraversableOnce[B]) = toList flatMap f
  def filter(f: M => Boolean) = toList filter f
  def foreach = toList.foreach _
}

object SCursor {
  object Implicits {
    @inline implicit def scursor2cursor(c: SCursor[_]) = c.cursor
    @inline implicit def scursor2model[M <: BaseModel : ClassTag]
      (c: SCursor[M]): M = c.get

    implicit def scursor2list[M <: BaseModel : ClassTag]
      (c: SCursor[M]): List[M] = c.toList

    implicit class AsCursor(val c: Cursor) extends AnyVal {
      def as[M <: BaseModel : ClassTag] = Model.create[M] << c
    }

    implicit class SCursorAdapterView(val l: AdapterView[_]) extends AnyVal {
      def get[M <: BaseModel : ClassTag](position: Int): SCursor[M] =
        return SCursor[M]((l getItemAtPosition position).asInstanceOf[Cursor])
    }
  }

  def apply[M <: BaseModel : ClassTag](cursor: Cursor) = new SCursor[M](cursor)
}
