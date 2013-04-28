package com.github.fxthomas.mocka

import android.view.WindowManager
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.EditText
import android.content.Context

import org.scaloid.common._

object InputDialog {
  def show(title: String, value: String = "")(onResult: String => Unit)
    (implicit ctx: Context) = {

    val dlg = new AlertDialogBuilder(title) {

      // Layout the text view
      val lp = new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.FILL_PARENT,
        ViewGroup.LayoutParams.FILL_PARENT
      )

      // Create the text view
      val et = new EditText(ctx)
      et setText value
      et setLayoutParams lp
      this setView et

      // Create buttons
      positiveButton("Save", onResult(et.getText.toString))
      negativeButton("Cancel")

      // Request focus on the EditText
      et.requestFocus

    // Create the dialog
    }.create

    // Set the keyboard visible
    dlg.getWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

    // Show the dialog
    dlg.show
  }
}
