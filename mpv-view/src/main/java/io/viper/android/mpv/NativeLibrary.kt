package io.viper.android.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface

class NativeLibrary {

    companion object {
        init {
            System.loadLibrary("c++_shared")
            System.loadLibrary("avutil")
            System.loadLibrary("swresample")
            System.loadLibrary("avcodec")
            System.loadLibrary("swscale")
            System.loadLibrary("avformat")
            System.loadLibrary("postproc")
            System.loadLibrary("avfilter")
            System.loadLibrary("avdevice")
            System.loadLibrary("mpv")
            System.loadLibrary("mpvjni")
        }

        @JvmStatic
        external fun create(ctx: Context)

        @JvmStatic
        external fun init()

        @JvmStatic
        external fun destroy()

        @JvmStatic
        external fun attachSurface(surface: Surface)

        @JvmStatic
        external fun detachSurface()

        @JvmStatic
        external fun command(cmd: Array<String?>)

        @JvmStatic
        external fun setOptionString(name: String, value: String): Int

        @JvmStatic
        external fun grabThumbnail(dimension: Int): Bitmap?

        @JvmStatic
        external fun getPropertyInt(property: String): Int?

        @JvmStatic
        external fun setPropertyInt(property: String, value: Int)

        @JvmStatic
        external fun getPropertyDouble(property: String): Double?

        @JvmStatic
        external fun setPropertyDouble(property: String, value: Double)

        @JvmStatic
        external fun getPropertyBoolean(property: String): Boolean?

        @JvmStatic
        external fun setPropertyBoolean(property: String, value: Boolean)

        @JvmStatic
        external fun getPropertyString(property: String): String?

        @JvmStatic
        external fun setPropertyString(property: String, value: String)

        @JvmStatic
        external fun observeProperty(property: String, format: Int)

        @JvmStatic
        fun eventProperty(property: String, value: Long) {
        }

        @JvmStatic
        fun eventProperty(property: String, value: Boolean) {
        }

        @JvmStatic
        fun eventProperty(property: String, value: String) {
        }

        @JvmStatic
        fun eventProperty(property: String) {
        }

        @JvmStatic
        fun event(evtId: Int) {
        }

        @JvmStatic
        fun logMessage(prefix: String, level: Int, text: String) {
        }
    }
}