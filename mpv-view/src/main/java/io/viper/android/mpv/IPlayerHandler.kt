package io.viper.android.mpv

import android.net.Uri

typealias ActivityResultCallback = (Uri?) -> Unit

interface IPlayerHandler {
    fun showToast(msg: String, cancel: Boolean = false)
    fun openContentFd(uri: Uri): String?
    fun activityMoveTaskToBack(nonRoot: Boolean)
    fun openFilePickerFor(
        callback: ActivityResultCallback
    )
    fun cycleOrientation()
}