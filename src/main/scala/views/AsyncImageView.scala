package com.github.fxthomas.mocka

import android.widget._
import android.graphics._
import android.view._
import android.content._
import android.util._

import scala.concurrent._
import org.scaloid.common._

import BitmapHelpers._

class AsyncImageView(ctx: Context, attrs: AttributeSet, defStyle: Int) extends ImageView(ctx, attrs, defStyle) {

  var __bmpTag: Option[String] = None

  def this(ctx: Context) = this(ctx, null, 0)
  def this(ctx: Context, attrs: AttributeSet) = this(ctx, attrs, 0)

  def setImageUri(uri: String)
  (implicit ec: ExecutionContext,
           lru: SLruCache[String, Bitmap],
           ctx: Context) = {

    // Tag the image view with the current URI (prevents the imageview from
    // loading the wrong image if multiple requests occur before any is
    // completed)
    __bmpTag = Some(uri)

    // Set the bitmap when the future completes
    for (bmp <- lru(uri, loadBitmap(uri));
         tag <- __bmpTag if uri == tag)
      runOnUiThread { setImageBitmap(bmp) }
  }
}
