package io.viper.android.mpv

import android.content.Context
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.addTextChangedListener
import io.viper.android.mpv.view.R
import kotlin.math.abs

// This is used to filter files in the file picker, so it contains just about everything
// FFmpeg/mpv could possibly read
val MEDIA_EXTENSIONS = setOf(
    /* Playlist */
    "cue", "m3u", "m3u8", "pls", "vlc",

    /* Audio */
    "3ga", "3ga2", "a52", "aac", "ac3", "adt", "adts", "aif", "aifc", "aiff", "alac",
    "amr", "ape", "au", "awb", "dsf", "dts", "dts-hd", "dtshd", "eac3", "f4a", "flac",
    "lpcm", "m1a", "m2a", "m4a", "mk3d", "mka", "mlp", "mp+", "mp1", "mp2", "mp3", "mpa",
    "mpc", "mpga", "mpp", "oga", "ogg", "opus", "pcm", "ra", "ram", "rax", "shn", "snd",
    "spx", "tak", "thd", "thd+ac3", "true-hd", "truehd", "tta", "wav", "weba", "wma", "wv",
    "wvp",

    /* Video / Container */
    "264", "265", "3g2", "3ga", "3gp", "3gp2", "3gpp", "3gpp2", "3iv", "amr", "asf",
    "asx", "av1", "avc", "avf", "avi", "bdm", "bdmv", "clpi", "cpi", "divx", "dv", "evo",
    "evob", "f4v", "flc", "fli", "flic", "flv", "gxf", "h264", "h265", "hdmov", "hdv",
    "hevc", "lrv", "m1u", "m1v", "m2t", "m2ts", "m2v", "m4u", "m4v", "mkv", "mod", "moov",
    "mov", "mp2", "mp2v", "mp4", "mp4v", "mpe", "mpeg", "mpeg2", "mpeg4", "mpg", "mpg4",
    "mpl", "mpls", "mpv", "mpv2", "mts", "mtv", "mxf", "mxu", "nsv", "nut", "ogg", "ogm",
    "ogv", "ogx", "qt", "qtvr", "rm", "rmj", "rmm", "rms", "rmvb", "rmx", "rv", "rvx",
    "sdp", "tod", "trp", "ts", "tsa", "tsv", "tts", "vc1", "vfw", "vob", "vro", "webm",
    "wm", "wmv", "wmx", "x264", "x265", "xvid", "y4m", "yuv",

    /* Picture */
    "apng", "bmp", "exr", "gif", "j2c", "j2k", "jfif", "jp2", "jpc", "jpe", "jpeg", "jpg",
    "jpg2", "png", "tga", "tif", "tiff", "webp",
)

// cf. AndroidManifest.xml and MPVActivity.resolveUri()
val PROTOCOLS = setOf(
    "file", "content", "http", "https",
    "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf"
)

fun View.getString(@StringRes resId: Int): String {
    return context.getString(resId)
}

fun View.getString(@StringRes resId: Int, vararg formatArgs: Any?): String {
    // 这里需要注意一点，在kotlin中声明的可变参数不能直接传递给java层，而是需要使用*来展开
    return context.getString(resId, *formatArgs)
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

fun viewGroupMove(from: ViewGroup, id: Int, to: ViewGroup, toIndex: Int) {
    val view: View? = (0 until from.childCount)
        .map { from.getChildAt(it) }.firstOrNull { it.id == id }
    if (view == null)
        error("$from does not have child with id=$id")
    from.removeView(view)
    to.addView(view, if (toIndex >= 0) toIndex else (to.childCount + 1 + toIndex))
}

fun viewGroupReorder(group: ViewGroup, idOrder: Array<Int>) {
    val m = mutableMapOf<Int, View>()
    for (i in 0 until group.childCount) {
        val c = group.getChildAt(i)
        m[c.id] = c
    }
    group.removeAllViews()
    // Readd children in specified order and unhide
    for (id in idOrder) {
        val c = m.remove(id) ?: error("$group did not have child with id=$id")
        c.visibility = View.VISIBLE
        group.addView(c)
    }
    // Keep unspecified children but hide them
    for (c in m.values) {
        c.visibility = View.GONE
        group.addView(c)
    }
}

fun Context.convertDp(dp: Float): Int {
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, dp,
        resources.displayMetrics
    ).toInt()
}

class OpenUrlDialog(context: Context) {
    private val editText = EditText(context)
    val builder = AlertDialog.Builder(context)
    private lateinit var dialog: AlertDialog

    init {
        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        editText.addTextChangedListener {
            val positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            if (it.isNullOrEmpty()) {
                editText.error = null
                positiveButton.isEnabled = false
            } else if (validate(it.toString())) {
                editText.error = null
                positiveButton.isEnabled = true
            } else {
                editText.error = context.getString(R.string.uri_invalid_protocol)
                positiveButton.isEnabled = false
            }
        }

        builder.apply {
            setTitle(R.string.action_open_url)
            setView(editText)
        }
    }

    private fun validate(text: String): Boolean {
        val uri = Uri.parse(text)
        return uri.isHierarchical && !uri.isRelative &&
                !(uri.host.isNullOrEmpty() && uri.path.isNullOrEmpty()) &&
                PROTOCOLS.contains(uri.scheme)
    }

    fun create(): AlertDialog {
        dialog = builder.create()
        editText.post { // initial state
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
        }
        return dialog
    }

    val text: String
        get() = editText.text.toString()
}