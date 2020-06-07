package com.bapidas.camerax.extension

import android.net.Uri
import android.os.Handler
import android.view.View
import android.widget.ImageView
import android.widget.VideoView
import androidx.databinding.BindingAdapter

@BindingAdapter("visibility")
fun View.setVisibility(show: Boolean) {
    visibility = if (show) View.VISIBLE else View.GONE
}

@BindingAdapter(value = ["visibility", "visibilityDelay"], requireAll = true)
fun View.setVisibilityWithDelay(show: Boolean, delay: Long) {
    Handler().postDelayed({
        visibility = if (show) View.VISIBLE else View.GONE
    }, delay)
}

@BindingAdapter("invisible")
fun View.setInvisibility(invisible: Boolean) {
    visibility = if (invisible) View.INVISIBLE else View.VISIBLE
}

@BindingAdapter("imageSrc")
fun ImageView.setImage(src: String) {
    setImageURI(Uri.parse(src))
}

@BindingAdapter("videoSrc")
fun VideoView.setVideo(src: String) {
    setVideoURI(Uri.parse(src))
}

