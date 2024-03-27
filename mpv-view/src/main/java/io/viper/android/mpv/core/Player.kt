package io.viper.android.mpv.core

import android.content.Context
import android.os.Build
import android.os.Environment
import android.preference.PreferenceManager
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowManager
import io.viper.android.mpv.NativeLibrary
import io.viper.android.mpv.view.R

class Player {

    private var filePath: String? = null
    private var voInUse: String = ""


    // 暴露给外部
    var vid: Int by TrackDelegate("vid")


    val videoAspect: Double?
        get() = NativeLibrary.getPropertyDouble("video-params/aspect")

    fun surfaceCreated(holder: SurfaceHolder) {
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

    fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        NativeLibrary.setPropertyString("android-surface-size", "${width}x$height")
    }

    fun surfaceDestroyed(holder: SurfaceHolder) {
        NativeLibrary.setPropertyString("vo", "null")
        NativeLibrary.setOptionString("force-window", "no")
        NativeLibrary.detachSurface()
    }

    fun init(
        context: Context,
        configDir: String,
        cacheDir: String,
        holder: SurfaceHolder,
        callback: SurfaceHolder.Callback
    ) {
        NativeLibrary.create(context)
        NativeLibrary.setOptionString("config", "yes")
        NativeLibrary.setOptionString("config-dir", configDir)
        for (opt in arrayOf("gpu-shader-cache-dir", "icc-cache-dir")) NativeLibrary.setOptionString(
            opt, cacheDir
        )
        initOptions(context) // do this before init() so user-supplied config can override our choices
        NativeLibrary.init()/* Hardcoded options: */
        // we need to call write-watch-later manually
        NativeLibrary.setOptionString("save-position-on-quit", "no")
        // would crash before the surface is attached
        NativeLibrary.setOptionString("force-window", "no")
        // "no" wouldn't work and "yes" is not intended by the UI
        NativeLibrary.setOptionString("idle", "once")

        holder.addCallback(callback)
    }

    fun playFile(fp: String) {
        filePath = fp
    }

    private fun initOptions(context: Context) {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        // apply phone-optimized defaults
        NativeLibrary.setOptionString("profile", "fast")

        // vo
        val vo = if (sharedPreferences.getBoolean("gpu_next", false)) "gpu-next"
        else "gpu"
        voInUse = vo

        // hwdec
        val hwdec = if (sharedPreferences.getBoolean("hardware_decoding", true)) "auto"
        else "no"

        // vo: set display fps as reported by android
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val disp = wm.defaultDisplay
            val refreshRate = disp.mode.refreshRate

            Log.v(TAG, "Display ${disp.displayId} reports FPS of $refreshRate")
            NativeLibrary.setOptionString("display-fps-override", refreshRate.toString())
        } else {
            Log.v(
                TAG,
                "Android version too old, disabling refresh rate functionality " + "(${Build.VERSION.SDK_INT} < ${Build.VERSION_CODES.M})"
            )
        }

        // set non-complex options
        data class Property(val preference_name: String, val mpv_option: String)

        val opts = arrayOf(
            Property("default_audio_language", "alang"),
            Property("default_subtitle_language", "slang"),

            // vo-related
            Property("video_scale", "scale"),
            Property("video_scale_param1", "scale-param1"),
            Property("video_scale_param2", "scale-param2"),

            Property("video_downscale", "dscale"),
            Property("video_downscale_param1", "dscale-param1"),
            Property("video_downscale_param2", "dscale-param2"),

            Property("video_tscale", "tscale"),
            Property("video_tscale_param1", "tscale-param1"),
            Property("video_tscale_param2", "tscale-param2")
        )

        for ((preference_name, mpv_option) in opts) {
            val preference = sharedPreferences.getString(preference_name, "")
            if (!preference.isNullOrBlank()) NativeLibrary.setOptionString(mpv_option, preference)
        }

        // set more options

        val debandMode = sharedPreferences.getString("video_debanding", "")
        if (debandMode == "gradfun") {
            // lower the default radius (16) to improve performance
            NativeLibrary.setOptionString("vf", "gradfun=radius=12")
        } else if (debandMode == "gpu") {
            NativeLibrary.setOptionString("deband", "yes")
        }

        val vidsync = sharedPreferences.getString(
            "video_sync",
            context.resources.getString(R.string.pref_video_interpolation_sync_default)
        )
        NativeLibrary.setOptionString("video-sync", vidsync!!)

        if (sharedPreferences.getBoolean(
                "video_interpolation", false
            )
        ) NativeLibrary.setOptionString("interpolation", "yes")

        if (sharedPreferences.getBoolean(
                "gpudebug", false
            )
        ) NativeLibrary.setOptionString("gpu-debug", "yes")

        if (sharedPreferences.getBoolean("video_fastdecode", false)) {
            NativeLibrary.setOptionString("vd-lavc-fast", "yes")
            NativeLibrary.setOptionString("vd-lavc-skiploopfilter", "nonkey")
        }

        NativeLibrary.setOptionString("vo", vo)
        NativeLibrary.setOptionString("gpu-context", "android")
        NativeLibrary.setOptionString("opengl-es", "yes")
        NativeLibrary.setOptionString("hwdec", hwdec)
        NativeLibrary.setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        NativeLibrary.setOptionString("ao", "audiotrack,opensles")
        NativeLibrary.setOptionString("tls-verify", "yes")
        NativeLibrary.setOptionString("tls-ca-file", "${context.filesDir.path}/cacert.pem")
        NativeLibrary.setOptionString("input-default-bindings", "yes")
        // Limit demuxer cache since the defaults are too high for mobile devices
        val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) 64 else 32
        NativeLibrary.setOptionString("demuxer-max-bytes", "${cacheMegs * 1024 * 1024}")
        NativeLibrary.setOptionString("demuxer-max-back-bytes", "${cacheMegs * 1024 * 1024}")
        //
        val screenshotDir =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        NativeLibrary.setOptionString("screenshot-directory", screenshotDir.path)
    }

    companion object {
        const val TAG = "MPV-Player"
    }
}