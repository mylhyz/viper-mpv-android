package io.viper.android.mpv

interface IPlayerHandler {
    fun showToast(msg: String, cancel: Boolean = false)
}