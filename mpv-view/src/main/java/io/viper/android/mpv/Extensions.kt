package io.viper.android.mpv

import android.net.Uri
import android.view.View
import androidx.annotation.StringRes

fun View.getString(@StringRes resId: Int): String {
    return context.getString(resId)
}

fun View.getString(@StringRes resId: Int, vararg formatArgs: Any?): String {
    return context.getString(resId, formatArgs)
}

fun fileBasename(str: String): String {
    val isURL = str.indexOf("://") != -1
    val last = str.replaceBeforeLast('/', "").trimStart('/')
    return if (isURL)
        Uri.decode(last.replaceAfter('?', "").trimEnd('?'))
    else
        last
}