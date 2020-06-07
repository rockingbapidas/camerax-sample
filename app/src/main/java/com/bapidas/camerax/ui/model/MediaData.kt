package com.bapidas.camerax.ui.model

import java.io.Serializable

data class MediaData (
    val mediaName: String = "",
    val mediaType: String = "",
    val mediaSize: Long = 0,
    val mediaPath: String = "",
    val mediaDuration: Long = 0
): Serializable