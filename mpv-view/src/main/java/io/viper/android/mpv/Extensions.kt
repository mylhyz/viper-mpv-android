package io.viper.android.mpv

import android.net.Uri
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import kotlin.math.abs

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

fun visibleChildren(view: View): Int {
    if (view is ViewGroup && view.visibility == View.VISIBLE) {
        return (0 until view.childCount).sumOf { visibleChildren(view.getChildAt(it)) }
    }
    return if (view.visibility == View.VISIBLE) 1 else 0
}

fun prettyTime(d: Int, sign: Boolean = false): String {
    if (sign)
        return (if (d >= 0) "+" else "-") + prettyTime(abs(d))

    val hours = d / 3600
    val minutes = d % 3600 / 60
    val seconds = d % 60
    if (hours == 0)
        return "%02d:%02d".format(minutes, seconds)
    return "%d:%02d:%02d".format(hours, minutes, seconds)
}