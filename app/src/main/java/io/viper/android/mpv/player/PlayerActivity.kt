package io.viper.android.mpv.player

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.util.Rational
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.viper.android.mpv.ActivityResultCallback
import io.viper.android.mpv.IPlayerHandler
import io.viper.android.mpv.NativeLibrary
import io.viper.android.mpv.core.PlaybackStateCache
import io.viper.android.mpv.core.Player
import io.viper.android.mpv.hud.HudContainer
import io.viper.android.mpv.view.PlayerView
import java.lang.IllegalArgumentException

class PlayerActivity : AppCompatActivity(), IPlayerHandler, NativeLibrary.EventObserver {

    private lateinit var mDocumentChooser: ActivityResultLauncher<Array<String>>
    private var mDocumentChooserResultCallback: ActivityResultCallback? = null

    private var mediaSession: MediaSessionCompat? = null

    private val mPlayerView: PlayerView by lazy {
        findViewById(R.id.player_view)
    }
    private val mHudContainer: HudContainer by lazy {
        findViewById(R.id.hud_view)
    }
    private val mPlayer: Player by lazy {
        mPlayerView.getAsPlayer()
    }

    private var mHudContainerEventObserverDelegate: HandlerEventObserver? = null

    private val psc: PlaybackStateCache
        get() = mPlayer.psc

    private var autoRotationMode = "auto"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 沉浸式状态栏
        enableEdgeToEdge()
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        // UI
        setContentView(R.layout.activity_player)
        updateOrientation(true)

        // data
        val filepath = parsePathFromIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            parseIntentExtras(intent.extras)
        }
        if (filepath == null) {
            Log.e(TAG, "No file given, exiting")
            showToast(getString(R.string.error_no_file))
            finishWithResult(RESULT_CANCELED)
            return
        }

        // attach to player
        mPlayerView.initPlayer(
            applicationContext.filesDir.path, applicationContext.cacheDir.path
        )
        mHudContainer.mPlayer = mPlayer
        mHudContainer.mPlayerHandler = this
        mPlayer.playFile(filepath)
        // register event observer
        NativeLibrary.addEventObserver(mPlayer)
        mHudContainerEventObserverDelegate =
            HandlerEventObserver(mHudContainer, Handler(Looper.getMainLooper()))
        NativeLibrary.addEventObserver(mHudContainerEventObserverDelegate!!)
        NativeLibrary.addEventObserver(this)

        mediaSession = initMediaSession()

        mDocumentChooser = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            mDocumentChooserResultCallback?.invoke(it)
            mDocumentChooserResultCallback = null
        }
    }

    override fun onResume() {
        super.onResume()
        mHudContainer.resume()
        mPlayer.resume(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        // unregister event observer
        NativeLibrary.removeEventObserver(this)
        mHudContainerEventObserverDelegate?.let { NativeLibrary.removeEventObserver(it) }
        NativeLibrary.removeEventObserver(mPlayer)
    }

    // Intent/Uri parsing

    private fun parsePathFromIntent(intent: Intent): String? {
        val filepath: String? = when (intent.action) {
            Intent.ACTION_VIEW -> intent.data?.let { resolveUri(it) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                val uri = Uri.parse(it.trim())
                if (uri.isHierarchical && !uri.isRelative) resolveUri(uri) else null
            }

            else -> intent.getStringExtra("filepath")
        }
        return filepath
    }

    private fun resolveUri(data: Uri): String? {
        val filepath = when (data.scheme) {
            "file" -> data.path
            "content" -> openContentFd(data)
            "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf" -> data.toString()
            else -> null
        }

        if (filepath == null) Log.e(TAG, "unknown scheme: ${data.scheme}")
        return filepath
    }

    override fun openContentFd(uri: Uri): String? {
        val resolver = applicationContext.contentResolver
        Log.v(TAG, "Resolving content URI: $uri")
        val fd = try {
            val desc = resolver.openFileDescriptor(uri, "r")
            desc!!.detachFd()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open content fd: $e")
            return null
        }
        // See if we skip the indirection and read the real file directly
        val path = findRealPath(fd)
        if (path != null) {
            Log.v(TAG, "Found real file path: $path")
            ParcelFileDescriptor.adoptFd(fd).close() // we don't need that anymore
            return path
        }
        // Else, pass the fd to mpv
        return "fd://${fd}"
    }

    private fun parseIntentExtras(extras: Bundle?) {
        mPlayer.onloadCommands.clear()
        if (extras == null) return

        // Refer to http://mpv-android.github.io/mpv-android/intent.html
        if (extras.getByte("decode_mode") == 2.toByte()) mPlayer.onloadCommands.add(
            arrayOf(
                "set", "file-local-options/hwdec", "no"
            )
        )
        if (extras.containsKey("subs")) {
            val subList =
                extras.getParcelableArray("subs")?.mapNotNull { it as? Uri } ?: emptyList()
            val subsToEnable =
                extras.getParcelableArray("subs.enable")?.mapNotNull { it as? Uri } ?: emptyList()

            for (suburi in subList) {
                val subfile = resolveUri(suburi) ?: continue
                val flag = if (subsToEnable.filter { it.compareTo(suburi) == 0 }
                        .any()) "select" else "auto"

                Log.v(TAG, "Adding subtitles from intent extras: $subfile")
                mPlayer.onloadCommands.add(arrayOf("sub-add", subfile, flag))
            }
        }
        if (extras.getInt("position", 0) > 0) {
            val pos = extras.getInt("position", 0) / 1000f
            mPlayer.onloadCommands.add(arrayOf("set", "start", pos.toString()))
        }
    }

    override fun showToast(msg: String, cancel: Boolean) {
        ToastUtils.showToast(applicationContext, msg, Toast.LENGTH_SHORT, cancel)
    }

    override fun activityMoveTaskToBack(nonRoot: Boolean) {
        moveTaskToBack(nonRoot)
    }

    override fun openFilePickerFor(
        callback: ActivityResultCallback
    ) {
        try {
            mDocumentChooserResultCallback = callback
            mDocumentChooser.launch(arrayOf("*/*"))
        } catch (e: ActivityNotFoundException) {
            // ignore
        }
    }

    override fun cycleOrientation() {
        requestedOrientation =
            if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    // activity result
    private fun finishWithResult(code: Int, includeTimePos: Boolean = false) {
        // Refer to http://mpv-android.github.io/mpv-android/intent.html
        // FIXME: should track end-file events to accurately report OK vs CANCELED
        if (isFinishing) // only count first call
            return
        val result = Intent(RESULT_INTENT)
        result.data = if (intent.data?.scheme == "file") null else intent.data
        if (includeTimePos) {
            result.putExtra("position", psc.position.toInt())
            result.putExtra("duration", psc.duration.toInt())
        }
        setResult(code, result)
        finish()
    }

    override fun updateOrientation(initial: Boolean) {
        if (autoRotationMode != "auto") {
            if (!initial) return // don't reset at runtime
            requestedOrientation = when (autoRotationMode) {
                "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        if (initial || mPlayer.vid == -1) return

        val ratio = mPlayer.videoAspect?.toFloat() ?: 0f
        Log.v(TAG, "auto rotation: aspect ratio = $ratio")

        if (ratio == 0f || ratio in (1f / ASPECT_RATIO_MIN)..ASPECT_RATIO_MIN) {
            // video is square, let Android do what it wants
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            return
        }
        requestedOrientation = if (ratio > 1f) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    // PictureInPictureMode
    override fun updatePictureInPictureParams(force: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return
        if (!isInPictureInPictureMode && !force)
            return
        val intent1 = NotificationButtonReceiver.createIntent(this, "PLAY_PAUSE")
        val action1 = if (psc.pause) {
            RemoteAction(
                Icon.createWithResource(
                    this,
                    io.viper.android.mpv.view.R.drawable.ic_play_arrow_black_24dp
                ),
                "Play", "", intent1
            )
        } else {
            RemoteAction(
                Icon.createWithResource(
                    this,
                    io.viper.android.mpv.view.R.drawable.ic_pause_black_24dp
                ),
                "Pause", "", intent1
            )
        }

        val params = with(PictureInPictureParams.Builder()) {
            val aspect = mPlayer.videoAspect ?: 1.0
            setAspectRatio(Rational(aspect.times(10000).toInt(), 10000))
            setActions(listOf(action1))
        }
        try {
            setPictureInPictureParams(params.build())
        } catch (e: IllegalArgumentException) {
            // Android has some limits of what the aspect ratio can be
            params.setAspectRatio(Rational(1, 1))
            setPictureInPictureParams(params.build())
        }
    }

    override fun getIntoPictureInPictureMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        updatePictureInPictureParams(true)
        enterPictureInPictureMode()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)
        Log.v(TAG, "onPiPModeChanged($isInPictureInPictureMode)")
        // TODO
//        if (isInPictureInPictureMode) {
//            lockedUI = true
//            hideControls()
//            return
//        }
//
//        unlockUI()
//        // For whatever stupid reason Android provides no good detection for when PiP is exited
//        // so we have to do this shit (https://stackoverflow.com/questions/43174507/#answer-56127742)
//        if (activityIsStopped) {
//            // audio-only detection doesn't work in this situation, I don't care to fix this:
//            this.backgroundPlayMode = "never"
//            onPauseImpl() // behave as if the app normally went into background
//        }
    }

    override fun updateKeepScreenOn(paused: Boolean) {
        if (paused)
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // Media Session handling

    private fun initMediaSession(): MediaSessionCompat {/*
            https://developer.android.com/guide/topics/media-apps/working-with-a-media-session
            https://developer.android.com/guide/topics/media-apps/audio-app/mediasession-callbacks
            https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat
         */
        val session = MediaSessionCompat(this, TAG)
        session.setFlags(0)
        session.setCallback(object : MediaSessionCompat.Callback() {
            override fun onPause() {
                mPlayer.paused = true
            }

            override fun onPlay() {
                mPlayer.paused = false
            }

            override fun onSeekTo(pos: Long) {
                mPlayer.timePos = (pos / 1000).toInt()
            }

            override fun onSkipToNext() {
                NativeLibrary.command(arrayOf("playlist-next"))
            }

            override fun onSkipToPrevious() {
                NativeLibrary.command(arrayOf("playlist-prev"))
            }

            override fun onSetRepeatMode(repeatMode: Int) {
                NativeLibrary.setPropertyString(
                    "loop-playlist",
                    if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) "inf" else "no"
                )
                NativeLibrary.setPropertyString(
                    "loop-file",
                    if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) "inf" else "no"
                )
            }

            override fun onSetShuffleMode(shuffleMode: Int) {
                mPlayer.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
            }
        })
        return session
    }

    private fun updateMediaSession() {
        synchronized(psc) {
            mediaSession?.let { psc.write(it) }
        }
    }


    // Event Observer

    override fun eventProperty(property: String) {
        if (property == "loop-file" || property == "loop-playlist") {
            mediaSession?.setRepeatMode(
                when (mPlayer.getRepeat()) {
                    2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                    1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                    else -> PlaybackStateCompat.REPEAT_MODE_NONE
                }
            )
        }
    }

    override fun eventProperty(property: String, value: Long) {
        if (psc.update(property, value))
            updateMediaSession()
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (psc.update(property, value))
            updateMediaSession()
        if (property == "shuffle") {
            mediaSession?.setShuffleMode(
                if (value)
                    PlaybackStateCompat.SHUFFLE_MODE_ALL
                else
                    PlaybackStateCompat.SHUFFLE_MODE_NONE
            )
        }
    }

    override fun eventProperty(property: String, value: String) {
        val triggerMetaUpdate = psc.update(property, value)
        if (triggerMetaUpdate)
            updateMediaSession()
    }

    override fun event(evtId: Int) {
        if (evtId == NativeLibrary.EventId.MPV_EVENT_SHUTDOWN)
            finishWithResult(if (mPlayer.playbackHasStarted) RESULT_OK else RESULT_CANCELED)
    }

    companion object {
        private const val TAG = "mpv.PlayerActivity"

        private const val ASPECT_RATIO_MIN = 1.2f // covers 5:4 and up

        // action of result intent
        private const val RESULT_INTENT = "io.viper.android.mpv.player.PlayerActivity.result"
    }
}