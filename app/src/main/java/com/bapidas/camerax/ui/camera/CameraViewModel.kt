package com.bapidas.camerax.ui.camera

import android.os.Handler
import android.os.SystemClock
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bapidas.camerax.model.MediaData
import java.io.File
import java.util.concurrent.TimeUnit

class CameraViewModel : ViewModel() {
    val recorderTimeText = MutableLiveData<String>()

    private var startHTime = 0L
    private var customHandler = Handler()
    private var timeInMilliseconds = 0L
    private val updateTimerThread = object : Runnable {

        override fun run() {
            timeInMilliseconds = SystemClock.uptimeMillis() - startHTime
            recorderTimeText.value = timeInMilliseconds.calculateDuration()
            customHandler.postDelayed(this, 0)
        }
    }

    private fun Long.calculateDuration(): String {
        return String.format("%02d", TimeUnit.MILLISECONDS.toMinutes(this)).plus(
            ":" + String.format(
                "%02d", TimeUnit.MILLISECONDS.toSeconds(this) - TimeUnit.MINUTES.toSeconds(
                    TimeUnit.MILLISECONDS.toMinutes(this)
                )
            )
        )
    }

    internal fun startTimer() {
        customHandler.postDelayed(updateTimerThread, 0)
    }

    internal fun stopTimer() {
        customHandler.removeCallbacks(updateTimerThread)
    }

    internal fun resetTimer() {
        timeInMilliseconds = 0L
        startHTime = SystemClock.uptimeMillis()
        recorderTimeText.value = String.format("%02d", 0).plus(":" + String.format("%02d", 0))
    }

    internal fun savePhoto(path: String): MediaData {
        val file = File(path)
        return MediaData(
            mediaName = file.name,
            mediaType = "IMAGE",
            mediaSize = file.length(),
            mediaPath = path
        )
    }

    internal fun saveVideo(path: String): MediaData {
        val file = File(path)
        return MediaData(
            mediaName = file.name,
            mediaType = "VIDEO",
            mediaSize = file.length(),
            mediaPath = path,
            mediaDuration = timeInMilliseconds
        )
    }
}