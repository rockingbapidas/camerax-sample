package com.bapidas.camerax.ui.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.camera.core.*
import androidx.camera.core.impl.VideoCaptureConfig
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.Navigation
import com.bapidas.camerax.BR
import com.bapidas.camerax.R
import com.bapidas.camerax.animation.AnimationHelper
import com.bapidas.camerax.databinding.CameraFragmentBinding
import com.bapidas.camerax.extension.showToast
import com.bapidas.camerax.model.LuminosityAnalyzer
import com.bapidas.camerax.ui.CameraActivity
import com.bapidas.camerax.ui.permission.PermissionFragment
import kotlinx.android.synthetic.main.camera_fragment.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraFragment : Fragment(),
    CameraNavigator {
    private val mDisplayManager by lazy {
        requireActivity().getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
    }
    private val mViewModel: CameraViewModel by lazy {
        ViewModelProvider(this).get(CameraViewModel::class.java)
    }

    private lateinit var mOutputDirectory: File
    private lateinit var mCameraExecutor: Executor
    private lateinit var mImageAnalyzerExecutor: ExecutorService
    private val mAnimationHelper = AnimationHelper()
    private var isTakingVideo = false
    private var mPreview: Preview? = null
    private var mImageAnalyzer: ImageAnalysis? = null
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
                mImageAnalyzer?.targetRotation = camera_preview.display.rotation
                mImageCapture?.targetRotation = camera_preview.display.rotation
                mVideoCapture?.setTargetRotation(camera_preview.display.rotation)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val binding = DataBindingUtil.inflate<CameraFragmentBinding>(
            inflater, R.layout.camera_fragment, container, false
        ).apply {
            setVariable(BR.viewModel, this@CameraFragment.mViewModel)
            setVariable(BR.callback, this@CameraFragment)
        }.also {
            it.lifecycleOwner = this
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addListeners()
        mOutputDirectory = CameraActivity.getOutputDirectory(requireContext())
        mCameraExecutor = ContextCompat.getMainExecutor(requireActivity())
        mImageAnalyzerExecutor = Executors.newSingleThreadExecutor()
        mDisplayManager.registerDisplayListener(mDisplayListener, null)
        camera_preview.post {
            mDisplayId = camera_preview.display.displayId
            setUpCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        // Make sure that all permissions are still present, since the
        // user could have removed them while the app was in paused state.
        if (!PermissionFragment.hasPermissions(requireContext())) {
            Navigation.findNavController(requireActivity(), R.id.fragment_container).navigate(
                CameraFragmentDirections.actionCameraToPermissions()
            )
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
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
                    mHandler.postDelayed(
                        mLongPressed,
                        DELAY_MILLIS
                    )
                    capture.setImageResource(R.drawable.ic_circle_red_white_24dp)
                    mAnimationHelper.buttonScaleAnimation(
                        SCALE_UP,
                        SCALE_UP, v
                    )
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
                    if ((abs(xDiff) < 5) && (abs(yDiff) < 5)) {
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

    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireActivity())
        cameraProviderFuture.addListener(Runnable {
            // CameraProvider
            mCameraProvider = cameraProviderFuture.get()

            // Select lensFacing depending on the available cameras
            mLensFacing = when {
                hasBackCamera() -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera() -> CameraSelector.LENS_FACING_FRONT
                else -> throw IllegalStateException("Back and front camera are unavailable")
            }

            mFlashMode = when {
                hasFlashMode() -> ImageCapture.FLASH_MODE_OFF
                else -> NO_FLASH
            }

            // Build and bind the camera use cases
            bindCameraUseCases()
            buildUi()
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

        // ImageAnalysis
        mImageAnalyzer = ImageAnalysis.Builder().apply {
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(rotation)
        }.build().also {
            it.setAnalyzer(mImageAnalyzerExecutor, LuminosityAnalyzer { luma ->
                Log.d(TAG, "Average luminosity: $luma")
            })
        }

        // ImageCapture
        mImageCapture = ImageCapture.Builder().apply {
            setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            setTargetAspectRatio(screenAspectRatio)
            setTargetRotation(rotation)
            if (mFlashMode != NO_FLASH)
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

    private fun buildUi() {
        if (!hasFlashMode())
            flashToggle.visibility = View.INVISIBLE
        if (!hasFrontCamera())
            rotateCamera.visibility = View.INVISIBLE
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
                    requireActivity().showToast(msg)
                    Log.e(TAG, msg, exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    requireActivity().showToast(msg)
                    Log.d(TAG, msg)
                    val data = mViewModel.savePhoto(savedUri.toString())
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(
                            CameraFragmentDirections.actionCameraToPreview(
                                data
                            )
                        )
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
                    requireActivity().showToast(msg)
                    Log.d(TAG, msg)
                    val data = mViewModel.saveVideo(savedUri.path.orEmpty())
                    Navigation.findNavController(requireActivity(), R.id.fragment_container)
                        .navigate(
                            CameraFragmentDirections.actionCameraToPreview(
                                data
                            )
                        )
                }

                override fun onError(videoCaptureError: Int, message: String, cause: Throwable?) {
                    val msg = "Video capture failed: ${cause?.message}"
                    requireActivity().showToast(msg)
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
        mAnimationHelper.startBlinkAnimation(dot_text)
    }

    private fun recordingStop() {
        flashToggle.visibility = View.VISIBLE
        capture.visibility = View.VISIBLE
        rotateCamera.visibility = View.VISIBLE
        tipText.visibility = View.VISIBLE
        timer.visibility = View.GONE
        dot_text.clearAnimation()
    }

    companion object {
        private const val TAG = "CameraFragment"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
        private const val DELAY_MILLIS = 2000L
        private const val SCALE_UP = 1.5f
        private const val SCALE_DOWN = 1.0f
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val NO_FLASH = -1111
    }
}