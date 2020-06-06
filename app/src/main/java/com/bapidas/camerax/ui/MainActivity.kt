package com.bapidas.camerax.ui

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.bapidas.camerax.R
import com.bapidas.camerax.ui.camera.CameraActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        CameraActivity.open(this)
    }
}