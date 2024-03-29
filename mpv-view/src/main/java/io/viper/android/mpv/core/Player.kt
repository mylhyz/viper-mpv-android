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

class Player : NativeLibrary.EventObserver {

    private var filePath: String? = null
    private var voInUse: String = ""


    // 暴露给外部
    var vid: Int by TrackDelegate("vid")
    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    data class Track(val mpvId: Int, val name: String)

    var tracks = mapOf<String, MutableList<Track>>(
        "audio" to arrayListOf(), "video" to arrayListOf(), "sub" to arrayListOf()
    )

    data class PlaylistItem(val index: Int, val filename: String, val title: String?)

    fun loadPlaylist(): MutableList<PlaylistItem> {
        val playlist = mutableListOf<PlaylistItem>()
        val count = NativeLibrary.getPropertyInt("playlist-count")!!
        for (i in 0 until count) {
            val filename = NativeLibrary.getPropertyString("playlist/$i/filename")!!
            val title = NativeLibrary.getPropertyString("playlist/$i/title")
            playlist.add(PlaylistItem(index = i, filename = filename, title = title))
        }
        return playlist
    }


    fun cycleAudio() = NativeLibrary.command(arrayOf("cycle", "audio"))
    fun cycleSub() = NativeLibrary.command(arrayOf("cycle", "sub"))
    fun cyclePause() = NativeLibrary.command(arrayOf("cycle", "pause"))
    fun cycleHwdec() = NativeLibrary.command(arrayOf("cycle-values", "hwdec", "auto", "no"))

    val videoAspect: Double?
        get() = NativeLibrary.getPropertyDouble("video-params/aspect")
    var playbackSpeed: Double?
        get() = NativeLibrary.getPropertyDouble("speed")
        set(speed) = NativeLibrary.setPropertyDouble("speed", speed!!)
    val hwdecActive: String
        get() = NativeLibrary.getPropertyString("hwdec-current") ?: "no"
    var paused: Boolean?
        get() = NativeLibrary.getPropertyBoolean("pause")
        set(paused) = NativeLibrary.setPropertyBoolean("pause", paused!!)

    fun getShuffle(): Boolean {
        return NativeLibrary.getPropertyBoolean("shuffle")!!
    }

    fun changeShuffle(cycle: Boolean, value: Boolean = true) {
        // Use the 'shuffle' property to store the shuffled state, since changing
        // it at runtime doesn't do anything.
        val state = getShuffle()
        val newState = if (cycle) state.xor(value) else value
        if (state == newState) return
        NativeLibrary.command(arrayOf(if (newState) "playlist-shuffle" else "playlist-unshuffle"))
        NativeLibrary.setPropertyBoolean("shuffle", newState)
    }

    fun getRepeat(): Int {
        return when (NativeLibrary.getPropertyString("loop-playlist") + NativeLibrary.getPropertyString(
            "loop-file"
        )) {
            "noinf" -> 2
            "infno" -> 1
            else -> 0
        }
    }

    fun cycleRepeat() {
        val state = getRepeat()
        when (state) {
            0, 1 -> {
                NativeLibrary.setPropertyString("loop-playlist", if (state == 1) "no" else "inf")
                NativeLibrary.setPropertyString("loop-file", if (state == 1) "inf" else "no")
            }

            2 -> NativeLibrary.setPropertyString("loop-file", "no")
        }
    }

    data class Chapter(val index: Int, val title: String?, val time: Double)

    fun loadChapters(): MutableList<Chapter> {
        val chapters = mutableListOf<Chapter>()
        val count = NativeLibrary.getPropertyInt("chapter-list/count")!!
        for (i in 0 until count) {
            val title = NativeLibrary.getPropertyString("chapter-list/$i/title")
            val time = NativeLibrary.getPropertyDouble("chapter-list/$i/time")!!
            chapters.add(
                Chapter(
                    index = i, title = title, time = time
                )
            )
        }
        return chapters
    }

    override fun eventProperty(property: String) {
        Log.d(TAG, "eventProperty => $property")
    }

    override fun eventProperty(property: String, value: Long) {
        Log.d(TAG, "eventProperty => $property : $value")
    }

    override fun eventProperty(property: String, value: Boolean) {
        Log.d(TAG, "eventProperty => $property : $value")
    }

    override fun eventProperty(property: String, value: String) {
        Log.d(TAG, "eventProperty => $property : $value")
    }

    override fun event(evtId: Int) {
        Log.d(TAG, "event => $evtId")
    }

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
        const val TAG = "mpv.Player"
    }
}