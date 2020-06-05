package com.bapidas.camerax.ui.camera

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.view.*
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.bapidas.camerax.ui.animation.AnimationHelper
import com.bapidas.camerax.R
import com.bapidas.camerax.databinding.ActivityCameraCustomBinding
import kotlinx.android.synthetic.main.activity_camera_custom.*
import com.bapidas.camerax.BR
import java.io.File
import java.lang.Math.abs

class CameraActivity : AppCompatActivity(), CameraNavigator {

    private val viewModel: CameraViewModel by lazy {
        ViewModelProvider(this).get(CameraViewModel::class.java)
    }
    private var flashMenuItem: MenuItem? = null
    private val mAnimationHelper = AnimationHelper()
    private var isTakingVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataBindingUtil.setContentView<ActivityCameraCustomBinding>(
            this, R.layout.activity_camera_custom
        ).apply {
            setVariable(BR.viewModel, this@CameraActivity.viewModel)
            setVariable(BR.callback, this@CameraActivity)
        }.also {
            it.lifecycleOwner = this
        }

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
        addListeners()
    }

    private fun setTranslucentStatusWithTopMargin(
        view: View,
        params: ConstraintLayout.LayoutParams
    ) {
        var result = 0
        val resourceId = this.resources.getIdentifier(
            "status_bar_height",
            "dimen", "android"
        )
        if (resourceId > 0) {
            result = this.resources.getDimensionPixelSize(resourceId)
        }
        window.setFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS
        )
        params.setMargins(
            params.marginStart,
            params.topMargin + result,
            params.marginEnd,
            params.bottomMargin
        )
        view.layoutParams = params
    }

    private fun addListeners() {
        var initialTouchX = 0f
        var initialTouchY = 0f
        val mHandler = Handler()
        val mLongPressed = Runnable {
            capture.performHapticFeedback(capture.id)
            takeVideo()
        }
        capture.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mHandler.postDelayed(mLongPressed, 2000)
                    capture.setImageResource(R.drawable.ic_circle_red_white_24dp)
                    mAnimationHelper.buttonScaleAnimation(1.5f, 1.5f, v)
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    mHandler.removeCallbacks(mLongPressed)
                    mAnimationHelper.buttonScaleAnimation(1f, 1f, v,
                        onAnimationEnd = {
                            capture.setImageResource(R.drawable.ic_circle_line_white_24dp)
                        })
                    val xDiff = initialTouchX - event.rawX
                    val yDiff = initialTouchY - event.rawY
                    if ((kotlin.math.abs(xDiff) < 5) && (kotlin.math.abs(yDiff) < 5)) {
                        if (isTakingVideo) {
                            stopVideo()
                        } else {
                            takePhoto()
                        }
                    } else {
                        stopVideo()
                    }
                    v.performClick()
                    return@setOnTouchListener true
                }
            }
            return@setOnTouchListener false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_camera, menu)
        flashMenuItem = menu?.findItem(R.id.ic_flash)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.ic_flash) {
            toggleFlash()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun openGallery() {
        TODO("Not yet implemented")
    }

    override fun toggleCamera() {
        TODO("Not yet implemented")
    }

    override fun toggleFlash() {
        TODO("Not yet implemented")
    }

    private fun startCamera() {
        TODO("Not yet implemented")
    }

    private fun takePhoto() {
        TODO("Not yet implemented")
    }

    private fun takeVideo() {
        TODO("Not yet implemented")
    }

    private fun stopVideo() {
        TODO("Not yet implemented")
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() }
        }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

        fun open(activity: Activity) {
            activity.startActivity(Intent(activity, CameraActivity::class.java)).also {
                activity.finish()
            }
        }
    }
}