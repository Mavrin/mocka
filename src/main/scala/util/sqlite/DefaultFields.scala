package com.github.fxthomas.mocka

import android.database.Cursor
import android.content.ContentValues
import scala.reflect.ClassTag

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

case class FloatField(override val sqlName: String, override val sqlType: String = "INTEGER")(implicit override val model: Model)
extends Field[java.lang.Float](sqlName, sqlType) {
  override def fromCursor(c: Cursor, cid: Int) = c getFloat cid
  override def fromContentValues(c: ContentValues, cid: String) = c getAsFloat cid
  override def toContentValues(c: ContentValues, cid: String, v: java.lang.Float) = c.put(cid, v: java.lang.Float)
}

case class LongField(override val sqlName: String, override val sqlType: String = "INTEGER")(implicit override val model: Model)
extends Field[java.lang.Long](sqlName, sqlType) {
  override def fromCursor(c: Cursor, cid: Int) = c getLong cid
  override def fromContentValues(c: ContentValues, cid: String) = c getAsLong cid
  override def toContentValues(c: ContentValues, cid: String, v: java.lang.Long) = c.put(cid, v: java.lang.Long)
}

case class ForeignField[M <: Model : ClassTag](override val sqlName: String, override val sqlType: String = "INTEGER")(implicit override val model: Model)
extends Field[java.lang.Long](sqlName, sqlType) {
  import SSQLiteOpenHelper.Implicits._

  override def fromCursor(c: Cursor, cid: Int) = c getLong cid
  override def fromContentValues(c: ContentValues, cid: String) = c getAsLong cid
  override def toContentValues(c: ContentValues, cid: String, v: java.lang.Long) = c.put(cid, v: java.lang.Long)
}
