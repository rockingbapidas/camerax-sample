package com.bapidas.camerax.ui.camera

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.core.impl.VideoCaptureConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.bapidas.camerax.animation.AnimationHelper
import com.bapidas.camerax.R
import com.bapidas.camerax.databinding.ActivityCameraCustomBinding
import kotlinx.android.synthetic.main.activity_camera_custom.*
import com.bapidas.camerax.BR
import com.bapidas.camerax.extension.setTranslucentStatusWithTopMargin
import com.bapidas.camerax.extension.showToast
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraActivity : AppCompatActivity(), CameraNavigator {
    private val mDisplayManager by lazy {
        getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    private val mViewModel: CameraViewModel by lazy {
        ViewModelProvider(this).get(CameraViewModel::class.java)
    }

    private lateinit var mOutputDirectory: File
    private lateinit var mCameraExecutor: Executor
    private val mAnimationHelper = AnimationHelper()
    private var isTakingVideo = false
    private var mPreview: Preview? = null
    private var mImageCapture: ImageCapture? = null
    private var mVideoCapture: VideoCapture? = null
    private var mCamera: Camera? = null
    private var mCameraProvider: ProcessCameraProvider? = null
    private var mDisplayId: Int = -1
    private var mLensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var mFlashMode: Int = ImageCapture.FLASH_MODE_OFF

    @SuppressLint("RestrictedApi")
    private val mDisplayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit
        override fun onDisplayRemoved(displayId: Int) = Unit
        override fun onDisplayChanged(displayId: Int) {
            if (displayId == mDisplayId) {
                mImageCapture?.targetRotation = camera_preview.display.rotation
                mVideoCapture?.setTargetRotation(camera_preview.display.rotation)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        DataBindingUtil.setContentView<ActivityCameraCustomBinding>(
            this, R.layout.activity_camera_custom
        ).apply {
            setVariable(BR.viewModel, this@CameraActivity.mViewModel)
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

        mOutputDirectory = getOutputDirectory()
        mCameraExecutor = ContextCompat.getMainExecutor(this)
        mDisplayManager.registerDisplayListener(mDisplayListener, null)

        if (allPermissionsGranted()) {
            camera_preview.post {
                mDisplayId = camera_preview.display.displayId
                setUpCamera()
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                camera_preview.post {
                    mDisplayId = camera_preview.display.displayId
                    setUpCamera()
                }
            } else {
                showToast("Permissions not granted by the user.").also {
                    finish()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the listeners
        mDisplayManager.unregisterDisplayListener(mDisplayListener)
    }

    override fun toggleCamera() {
        if (isTakingVideo) return
        mLensFacing = if (CameraSelector.LENS_FACING_FRONT == mLensFacing) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        bindCameraUseCases()
    }

    override fun toggleFlash() {
        if (isTakingVideo) return
        when (mFlashMode) {
            ImageCapture.FLASH_MODE_OFF -> {
                mFlashMode = ImageCapture.FLASH_MODE_ON
                flashToggle.setImageResource(R.drawable.ic_flash_on_white_20dp)
            }
            ImageCapture.FLASH_MODE_ON -> {
                mFlashMode = ImageCapture.FLASH_MODE_AUTO
                flashToggle.setImageResource(R.drawable.ic_flash_auto_white_20dp)
            }
            ImageCapture.FLASH_MODE_AUTO -> {
                mFlashMode = ImageCapture.FLASH_MODE_OFF
                flashToggle.setImageResource(R.drawable.ic_flash_off_white_20dp)
            }
        }
        // Re-bind use cases to include changes
        bindCameraUseCases()
    }

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener(Runnable {
            // CameraProvider
            mCameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            mLensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, mCameraExecutor)
    }

    @SuppressLint("RestrictedApi")
    private fun bindCameraUseCases() {
        val metrics = DisplayMetrics().also { camera_preview.display.getRealMetrics(it) }
        Log.d(TAG, "Screen metrics: ${metrics.widthPixels} x ${metrics.heightPixels}")

        val screenAspectRatio = aspectRatio(metrics.widthPixels, metrics.heightPixels)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = camera_preview.display.rotation
        Log.d(TAG, "Rotation: $rotation")

        // CameraProvider
        val cameraProvider =
            mCameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().apply {
            requireLensFacing(mLensFacing)
        }.build()

        // Preview
        mPreview = Preview.Builder().apply {
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(rotation)
        }.build()

        // ImageCapture
        mImageCapture = ImageCapture.Builder().apply {
            setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(rotation)
            setFlashMode(mFlashMode)
        }.build()

        //Video Capture
        mVideoCapture = VideoCaptureConfig.Builder().apply {
            setTargetRotation(camera_preview.display.rotation)
            setTargetAspectRatio(screenAspectRatio)
        }.build()

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            mCamera = cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                mPreview,
                mImageCapture,
                mVideoCapture
            )
            // Attach the viewfinder's surface provider to preview use case
            mPreview?.setSurfaceProvider(camera_preview.createSurfaceProvider())
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    private fun addListeners() {
        var initialTouchX = 0f
        var initialTouchY = 0f
        val mHandler = Handler()
        val mLongPressed = Runnable {
            capture.performHapticFeedback(capture.id)
            startVideo()
        }
        capture.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mHandler.postDelayed(mLongPressed,
                        DELAY_MILLIS
                    )
                    capture.setImageResource(R.drawable.ic_circle_red_white_24dp)
                    mAnimationHelper.buttonScaleAnimation(
                        SCALE_UP,
                        SCALE_UP, v)
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    return@setOnTouchListener true
                }
                MotionEvent.ACTION_UP -> {
                    mHandler.removeCallbacks(mLongPressed)
                    mAnimationHelper.buttonScaleAnimation(
                        SCALE_DOWN,
                        SCALE_DOWN, v,
                        onAnimationEnd = {
                            capture.setImageResource(R.drawable.ic_circle_line_white_24dp)
                        }
                    )
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
                else -> {
                    return@setOnTouchListener false
                }
            }
        }
    }

    private fun takePhoto() {
        if (isTakingVideo) return
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = mImageCapture ?: return

        // Create timestamped output file to hold the image
        val fileName = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis()) + ".jpg"
        val photoFile = File(mOutputDirectory, fileName)

        // Setup image capture metadata
        val metadata = ImageCapture.Metadata().apply {
            // Mirror image when using the front camera
            isReversedHorizontal = mLensFacing == CameraSelector.LENS_FACING_FRONT
        }

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile)
            .apply {
                setMetadata(metadata)
            }.build()

        // Setup image capture listener which is triggered after photo has been taken
        imageCapture.takePicture(
            outputOptions,
            mCameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    val msg = "Photo capture failed: ${exc.message}"
                    showToast(msg)
                    Log.e(TAG, msg, exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    showToast(msg)
                    Log.d(TAG, msg)
                }
            })
    }

    @SuppressLint("RestrictedApi")
    private fun startVideo() {
        if (isTakingVideo) return
        // Get a stable reference of the modifiable video capture use case
        val videoCapture = mVideoCapture ?: return

        // Create timestamped output file to hold the image
        val fileName = SimpleDateFormat(FILENAME_FORMAT, Locale.US)
            .format(System.currentTimeMillis()) + ".mp4"
        val videoFile = File(mOutputDirectory, fileName)

        videoCapture.startRecording(
            videoFile,
            mCameraExecutor,
            object : VideoCapture.OnVideoSavedCallback {
                override fun onVideoSaved(file: File) {
                    val savedUri = Uri.fromFile(file)
                    val msg = "Video capture succeeded: $savedUri"
                    showToast(msg)
                    Log.d(TAG, msg)
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    val msg = "Video capture failed: ${cause?.message}"
                    showToast(msg)
                    Log.e(TAG, msg, cause)
                }
            })
        isTakingVideo = true
        recordingStarted()
        mViewModel.startTimer()
    }

    @SuppressLint("RestrictedApi")
    private fun stopVideo() {
        if (!isTakingVideo) return
        // Get a stable reference of the modifiable video capture use case
        val videoCapture = mVideoCapture ?: return
        videoCapture.stopRecording()
        recordingStop()
        mViewModel.stopTimer()
        isTakingVideo = false
    }

    private fun recordingStarted() {
        mViewModel.resetTimer()
        timer.visibility = View.VISIBLE
        flashToggle.visibility = View.INVISIBLE
        rotateCamera.visibility = View.INVISIBLE
        tipText.visibility = View.INVISIBLE
        enableToolbarIcon(false)
        mAnimationHelper.startBlinkAnimation(dot_text)
    }

    private fun recordingStop() {
        enableToolbarIcon(true)
        flashToggle.visibility = View.VISIBLE
        capture.visibility = View.VISIBLE
        rotateCamera.visibility = View.VISIBLE
        tipText.visibility = View.VISIBLE
        timer.visibility = View.GONE
        dot_text.clearAnimation()
    }

    private fun enableToolbarIcon(enable: Boolean) {
        supportActionBar?.setDisplayHomeAsUpEnabled(enable)
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

    private fun hasBackCamera(): Boolean {
        return mCameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    private fun hasFrontCamera(): Boolean {
        return mCameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    private fun hasFlashMode(): Boolean {
        return mCamera?.cameraInfo?.hasFlashUnit() ?: false
    }

    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }
        return AspectRatio.RATIO_16_9
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private const val DELAY_MILLIS = 2000L
        private const val SCALE_UP = 1.5f
        private const val SCALE_DOWN = 1.0f
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )

        fun open(activity: Activity) {
            activity.startActivity(Intent(activity, CameraActivity::class.java)).also {
                activity.finish()
            }
        }
    }
}