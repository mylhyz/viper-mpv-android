package io.viper.android.mpv

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.Surface

class NativeLibrary {

    companion object {

        private const val TAG = "mpv.NativeLibrary"

        private val observers = mutableListOf<EventObserver>()

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
            observers.forEach {
                it.eventProperty(property, value)
            }
        }

        @JvmStatic
        fun eventProperty(property: String, value: Boolean) {
            observers.forEach {
                it.eventProperty(property, value)
            }
        }

        @JvmStatic
        fun eventProperty(property: String, value: String) {
            observers.forEach {
                it.eventProperty(property, value)
            }
        }

        @JvmStatic
        fun eventProperty(property: String) {
            observers.forEach {
                it.eventProperty(property)
            }
        }

        @JvmStatic
        fun event(evtId: Int) {
            observers.forEach {
                it.event(evtId)
            }
        }

        @JvmStatic
        fun logMessage(prefix: String, level: Int, text: String) {
            Log.println(level, prefix, text)
        }
    }

    interface EventObserver {
        fun eventProperty(property: String)
        fun eventProperty(property: String, value: Long)
        fun eventProperty(property: String, value: Boolean)
        fun eventProperty(property: String, value: String)
        fun event(evtId: Int)
    }

    object EventId {
        const val MPV_EVENT_NONE: Int = 0
        const val MPV_EVENT_SHUTDOWN: Int = 1
        const val MPV_EVENT_LOG_MESSAGE: Int = 2
        const val MPV_EVENT_GET_PROPERTY_REPLY: Int = 3
        const val MPV_EVENT_SET_PROPERTY_REPLY: Int = 4
        const val MPV_EVENT_COMMAND_REPLY: Int = 5
        const val MPV_EVENT_START_FILE: Int = 6
        const val MPV_EVENT_END_FILE: Int = 7
        const val MPV_EVENT_FILE_LOADED: Int = 8

        @Deprecated("MPV_EVENT_IDLE")
        val MPV_EVENT_IDLE: Int = 11

        @Deprecated("MPV_EVENT_TICK")
        val MPV_EVENT_TICK: Int = 14
        const val MPV_EVENT_CLIENT_MESSAGE: Int = 16
        const val MPV_EVENT_VIDEO_RECONFIG: Int = 17
        const val MPV_EVENT_AUDIO_RECONFIG: Int = 18
        const val MPV_EVENT_SEEK: Int = 20
        const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
        const val MPV_EVENT_PROPERTY_CHANGE: Int = 22
        const val MPV_EVENT_QUEUE_OVERFLOW: Int = 24
        const val MPV_EVENT_HOOK: Int = 25
    }

    object LogLevel {
        const val MPV_LOG_LEVEL_NONE: Int = 0
        const val MPV_LOG_LEVEL_FATAL: Int = 10
        const val MPV_LOG_LEVEL_ERROR: Int = 20
        const val MPV_LOG_LEVEL_WARN: Int = 30
        const val MPV_LOG_LEVEL_INFO: Int = 40
        const val MPV_LOG_LEVEL_V: Int = 50
        const val MPV_LOG_LEVEL_DEBUG: Int = 60
        const val MPV_LOG_LEVEL_TRACE: Int = 70
    }
}