package com.bapidas.camerax.ui.animation

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.*

class AnimationHelper {

    fun buttonScaleAnimation(
        scaleX: Float,
        scaleY: Float,
        view: View,
        onAnimationStart: (() -> Unit)? = null,
        onAnimationEnd: (() -> Unit)? = null
    ) {
        val scaleDownX = ObjectAnimator.ofFloat(
            view, "scaleX", scaleX
        )
        val scaleDownY = ObjectAnimator.ofFloat(
            view, "scaleY", scaleY
        )
        scaleDownX.duration = 500
        scaleDownY.duration = 500
        val scaleDown2 = AnimatorSet()
        scaleDown2.play(scaleDownX).with(scaleDownY)
        scaleDown2.start()
        scaleDown2.addListener(object : CustomAnimationListener() {
            override fun onAnimationEnd(animation: Animator?) {
                onAnimationEnd?.invoke()
            }

            override fun onAnimationStart(animation: Animator?) {
                onAnimationStart?.invoke()
            }
        })
    }

    fun startBlinkAnimation(view: View) {
        val anim = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 500
        anim.startOffset = 20
        anim.repeatMode = Animation.REVERSE
        anim.repeatCount = Animation.INFINITE
        view.startAnimation(anim)
    }
}