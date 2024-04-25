package io.viper.android.mpv

import android.content.Context
import android.media.AudioManager
import android.provider.Settings
import android.util.Log
import android.view.Window
import androidx.core.content.getSystemService
import io.viper.android.mpv.core.Player
import kotlin.math.roundToInt

class PlayerAdapter(val context: Context, val window: Window) {

    var mPLayer: Player? = null

    var volume: Float = 0.toFloat()
    var audioMax: Int = 0
    fun initAudioVolume() {
        val audioManager = context.applicationContext.getSystemService<AudioManager>()!!
        audioMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        Log.i(TAG, "initAudioVolume $audioMax $volume")
    }

    fun setAudioVolume(volume: Int) {
        // TODO 设置视频音量
        Log.i(TAG, "setAudioVolume $volume")
    }

    private var isFistGetBrightness = true
    fun initBrightness() {
        if (!isFistGetBrightness) return
        val lp = window.attributes
        // 这里需要注意的是 screenBrightness 的值范围是 0-1
        val brightnessTemp = if (lp.screenBrightness != -1f)
            lp.screenBrightness
        else {
            //Check if the device is in auto mode
            val contentResolver = context.applicationContext.contentResolver
            if (Settings.System.getInt(
                    contentResolver,
                    Settings.System.SCREEN_BRIGHTNESS_MODE,
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                ) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
            ) {
                //cannot retrieve a value -> 0.5
                0.5f
            } else Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                .toFloat() / 255
        }

        lp.screenBrightness = brightnessTemp
        window.attributes = lp
    }

    fun setBrightness(delta: Float) {
        val lp = window.attributes
        // screenBrightness 的值范围是 0-1
        var brightness = (lp.screenBrightness + delta).coerceIn(0.01f, 1f)
        setWindowBrightness(brightness)
        // TODO 控制界面上亮度进度条的显示
        brightness = (brightness * 100).roundToInt().toFloat()
    }

    private fun setWindowBrightness(brightness: Float) {
        val lp = window.attributes
        lp.screenBrightness = brightness
        // Set Brightness
        window.attributes = lp
    }

    companion object {
        private const val TAG = "mpv.PlayerAdapter"
    }
}