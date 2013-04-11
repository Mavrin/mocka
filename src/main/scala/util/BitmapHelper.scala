package com.github.fxthomas.mocka

import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import android.content.Context

object BitmapHelpers {
  def calculateInSampleSize(opt: BitmapFactory.Options, reqWidth: Int, reqHeight: Int) = {
    val height = opt.outHeight
    val width = opt.outWidth

    if (height > reqHeight || width > reqWidth) {

        // Calculate ratios of height and width to requested height and width
        val heightRatio = Math.round(height.toFloat / reqHeight.toFloat)
        val widthRatio = Math.round(width.toFloat / reqWidth.toFloat)

        // Choose the smallest ratio as inSampleSize value, this will guarantee
        // a final image with both dimensions larger than or equal to the
        // requested height and width.
        if (heightRatio < widthRatio) heightRatio else widthRatio

    } else 1
  }

  // Load a bitmap
  def loadBitmap(t: String)(implicit ctx: Context) = {
    Option(Uri.parse(t)) map { uri =>
      val opt = new BitmapFactory.Options
      opt.inJustDecodeBounds = true
      val is1 = ctx.getContentResolver.openInputStream(uri)
      BitmapFactory.decodeStream(is1, null, opt)
      is1.close

      opt.inSampleSize = calculateInSampleSize(opt, 512, 512)
      opt.inJustDecodeBounds = false
      val is2 = ctx.getContentResolver.openInputStream(uri)
      val bmp = BitmapFactory.decodeStream(is2, null, opt)
      is2.close

      bmp
    }
  }
}
