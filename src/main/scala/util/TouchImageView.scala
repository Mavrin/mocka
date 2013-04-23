package com.github.fxthomas.mocka

import android.widget._
import android.graphics._
import android.view._
import android.content._
import android.util._

import org.scaloid.common._


class TouchImageView(ctx: Context, attrs: AttributeSet, defStyle: Int) extends ImageView(ctx, attrs, defStyle) {

  case class Point(x: Float, y: Float) {
    lazy val normalized = {
      // Compute the current image<->imageview transformation matrix
      val d = getDrawable
      val m = new Matrix
      val drawableRect = new RectF(0, 0, d.getIntrinsicWidth, d.getIntrinsicHeight)
      val viewRect = new RectF(0, 0, getWidth, getHeight)
      m set getImageMatrix
      m.setRectToRect(drawableRect, viewRect, Matrix.ScaleToFit.CENTER)
      m invert m

      // Transform the points
      val a = Array(x,y)
      m mapPoints a

      // Return the normalized Point
      Point(
        a(0) / d.getIntrinsicWidth,
        a(1) / d.getIntrinsicHeight
      )
    }

    lazy val inside = normalized.x >= 0 && normalized.x <= 1 &&
                      normalized.y >= 0 && normalized.y <= 1
  }

  def this(ctx: Context) = this(ctx, null, 0)
  def this(ctx: Context, attrs: AttributeSet) = this(ctx, attrs, 0)

  // Long touch handler
  var onSelectHandler: Option[(Float, Float) => Boolean] = None

  // Current coordinates
  var last_point = Point(0, 0)
  var finger_down = false

  // Long press on the screen adds a new interaction at that position
  this onTouch {
    (v: View, ev: MotionEvent) => {
      // True if the finger is down
      finger_down  = !(ev.getActionMasked != MotionEvent.ACTION_DOWN &&
                       ev.getActionMasked != MotionEvent.ACTION_MOVE)

      // Last touched point
      last_point = Point(ev.getX, ev.getY)

      // Invalidate the view
      invalidate

      // Only continue if we touched inside the image
      !finger_down || !last_point.inside
    }
  }

  this onLongClick {
    (v: View) => {
      onSelectHandler match {
        case Some(f) => {
          val n = last_point.normalized
          f(n.x, n.y)
        }
        case None => false
      }
    }
  }

  override def onDraw(canvas: Canvas) = {
    // Draw the canvas
    super.onDraw(canvas)

    // If we have a touch, then use it to draw
    if (finger_down) {
      val p = new Paint
      p.setColor(Color.parseColor("#4433B5E5"))
      p.setStyle(Paint.Style.FILL)
      canvas.drawCircle(last_point.x, last_point.y, 50, p)

      p.setColor(Color.parseColor("#FF33B5E5"))
      p.setStyle(Paint.Style.STROKE)
      canvas.drawCircle(last_point.x, last_point.y, 50, p)
    }
  }

  def onSelect(f: (Float, Float) => Boolean) {
    onSelectHandler = Some(f)
  }
}
