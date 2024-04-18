package io.viper.android.mpv.hud

import android.content.SharedPreferences
import android.content.res.Resources
import android.view.MotionEvent

class GestureDelegate {


    fun syncSettings(prefs: SharedPreferences, resources: Resources) {
    }

    fun onTouchEvent(event: MotionEvent): Boolean {
        return false
    }
}