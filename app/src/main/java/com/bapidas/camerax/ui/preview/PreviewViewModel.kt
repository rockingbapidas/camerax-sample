package com.bapidas.camerax.ui.preview

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.bapidas.camerax.model.MediaData

class PreviewViewModel : ViewModel() {
    val currentTakenMedia = MutableLiveData<MediaData>()

    internal fun setArguments(args: Any) {
        currentTakenMedia.value = args as MediaData
    }

    internal fun isVideo() = currentTakenMedia.value?.mediaType == "VIDEO"
}