package io.viper.android.mpv.core

import io.viper.android.mpv.NativeLibrary
import kotlin.reflect.KProperty

class TrackDelegate(private val name: String) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
        val v = NativeLibrary.getPropertyString(name)
        // we can get null here for "no" or other invalid value
        return v?.toIntOrNull() ?: -1
    }

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        if (value == -1)
            NativeLibrary.setPropertyString(name, "no")
        else
            NativeLibrary.setPropertyInt(name, value)
    }
}