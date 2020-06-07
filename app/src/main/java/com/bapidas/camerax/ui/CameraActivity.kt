package com.bapidas.camerax.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.lifecycle.ViewModelProvider
import com.bapidas.camerax.R
import com.bapidas.camerax.extension.setTranslucentStatusWithTopMargin
import com.bapidas.camerax.ui.camera.CameraViewModel
import kotlinx.android.synthetic.main.activity_camera.*
import java.io.File

class CameraActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)
    }

    override fun onResume() {
        super.onResume()
        toolbar.apply {
            setTranslucentStatusWithTopMargin(
                this,
                layoutParams as ConstraintLayout.LayoutParams
            )
            setNavigationIcon(R.drawable.ic_close_white_24dp)
            setSupportActionBar(this)
        }

        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = ""
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        fun getOutputDirectory(context: Context): File {
            val appContext = context.applicationContext
            val mediaDir = context.externalMediaDirs.firstOrNull()?.let {
                File(it, appContext.resources.getString(R.string.app_name)).apply { mkdirs() } }
            return if (mediaDir != null && mediaDir.exists())
                mediaDir else appContext.filesDir
        }

        fun open(activity: Activity) {
            activity.startActivity(Intent(activity, CameraActivity::class.java)).also {
                activity.finish()
            }
        }
    }
}