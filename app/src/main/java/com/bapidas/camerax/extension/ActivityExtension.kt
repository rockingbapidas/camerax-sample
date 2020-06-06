package com.bapidas.camerax.extension

import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout

fun AppCompatActivity.showToast(message: String) {
    Toast.makeText(
        this,
        message,
        Toast.LENGTH_SHORT
    ).show()
}

fun AppCompatActivity.setTranslucentStatusWithTopMargin(
    view: View,
    params: ConstraintLayout.LayoutParams
) {
    val resourceId = this.resources.getIdentifier(
        "status_bar_height",
        "dimen",
        "android"
    )
    val topMargin = if (resourceId > 0) {
        this.resources.getDimensionPixelSize(resourceId) + params.topMargin
    } else {
        0 + params.topMargin
    }
    window.setFlags(
        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
        WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
    )
    params.setMargins(
        params.marginStart,
        topMargin,
        params.marginEnd,
        params.bottomMargin
    )
    view.layoutParams = params
}