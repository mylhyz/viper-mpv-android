package io.viper.android.mpv

import android.view.View
import androidx.annotation.StringRes

fun View.getString(@StringRes resId: Int): String {
    return context.getString(resId)
}