package com.bapidas.camerax.ui.camera

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class CameraViewModel : ViewModel() {
    val toolsTipText = MutableLiveData<String>()
    val recorderTimeText = MutableLiveData<String>()
    val showGalleryIcon = MutableLiveData<Boolean>()
}