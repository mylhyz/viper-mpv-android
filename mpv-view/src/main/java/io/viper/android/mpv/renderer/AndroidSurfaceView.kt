package io.viper.android.mpv.renderer

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import io.viper.android.mpv.NativeLibrary

class AndroidSurfaceView(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs),
    SurfaceHolder.Callback {

    var filePath: String? = null
    var voInUse: String = ""

    override fun surfaceCreated(holder: SurfaceHolder) {
        NativeLibrary.attachSurface(holder.surface)
        // This forces mpv to render subs/osd/whatever into our surface even if it would ordinarily not
        NativeLibrary.setOptionString("force-window", "yes")
        if (filePath != null) {
            NativeLibrary.command(arrayOf("loadfile", filePath as String))
            filePath = null
        } else {
            // We disable video output when the context disappears, enable it back
            NativeLibrary.setPropertyString("vo", voInUse)
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        NativeLibrary.setPropertyString("android-surface-size", "${width}x$height")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        NativeLibrary.setPropertyString("vo", "null")
        NativeLibrary.setOptionString("force-window", "no")
        NativeLibrary.detachSurface()
    }


    companion object {
        private const val TAG = "AndroidSurfaceView"
    }
}