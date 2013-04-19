package com.github.fxthomas.mocka

import android.widget._
import android.graphics._
import android.view._
import android.content._
import android.util._

import org.scaloid.common._

class TouchImageView(ctx: Context, attrs: AttributeSet, defStyle: Int) extends ImageView(ctx, attrs, defStyle) {

  def this(ctx: Context) = this(ctx, null, 0)
  def this(ctx: Context, attrs: AttributeSet) = this(ctx, attrs, 0)

  // Long touch handler
  var onSelectHandler: Option[(Float, Float) => Boolean] = None

  // Current coordinates
  var last_inside = false
  var last_x = 0.f
  var last_y = 0.f
  var current_x = 0.f
  var current_y = 0.f

  // Long press on the screen adds a new interaction at that position
  this onTouch {
    (v: View, ev: MotionEvent) => {
      if (ev.getActionMasked != MotionEvent.ACTION_DOWN &&
          ev.getActionMasked != MotionEvent.ACTION_MOVE) {
        // We're outside now
        last_inside = false

      } else {
        // Store the points
        last_x = ev.getX
        last_y = ev.getY

        // Create the image transformation matrix
        val d = getDrawable
        val m = new Matrix
        val drawableRect = new RectF(0, 0, d.getIntrinsicWidth, d.getIntrinsicHeight)
        val viewRect = new RectF(0, 0, getWidth, getHeight)
        m.set(getImageMatrix)
        m.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER)
        m.invert(m)

        // Convert the points inside the image
        val points = Array(ev.getX, ev.getY)
        m.mapPoints(points)

        // Compute normalized coordinates
        current_x = points(0) / d.getIntrinsicWidth
        current_y = points(1) / d.getIntrinsicHeight

        // Update the rendering if we touched inside
        last_inside = current_x >= 0 && current_x <= 1 &&
                      current_y >= 0 && current_y <= 1
      }

      // Only continue if we touched inside the image
      invalidate
      !last_inside
    }
  }

  this onLongClick {
    (v: View) => {
      if (last_inside) {
        last_inside = false
        invalidate
        onSelectHandler match {
          case Some(f) => f(current_x, current_y)
          case None => false
        }
      } else false
    }
  }

  override def onDraw(canvas: Canvas) = {
    // Draw the canvas
    super.onDraw(canvas)

    // If we have a touch, then use it to draw
    if (last_inside) {
      val p = new Paint
      p.setColor(Color.parseColor("#4433B5E5"))
      p.setStyle(Paint.Style.FILL)
      canvas.drawCircle(last_x, last_y, 50, p)

      p.setColor(Color.parseColor("#FF33B5E5"))
      p.setStyle(Paint.Style.STROKE)
      canvas.drawCircle(last_x, last_y, 50, p)
    }
  }

  def onSelect(f: (Float, Float) => Boolean) {
    onSelectHandler = Some(f)
  }
}
