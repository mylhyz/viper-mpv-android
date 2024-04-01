package io.viper.android.mpv.player

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Gravity
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
import io.viper.android.mpv.core.Player
import io.viper.android.mpv.hud.HudContainer
import io.viper.android.mpv.view.PlayerView

class PlayerActivity : AppCompatActivity(), IPlayerHandler {

    private lateinit var mDocumentChooser: ActivityResultLauncher<Array<String>>
    private var mDocumentChooserResultCallback: ActivityResultCallback? = null

    private var mToast: Toast? = null
    private val psc = PlaybackStateCache()
    private val mPlayerView: PlayerView by lazy {
        findViewById(R.id.player_view)
    }
    private val mHudContainer: HudContainer by lazy {
        findViewById(R.id.hud_view)
    }
    private val mPlayer: Player by lazy {
        mPlayerView.getAsPlayer()
    }

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
        initMessageToast()
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
        NativeLibrary.addEventObserver(mPlayer)
        mHudContainer.mPlayer = mPlayer
        mHudContainer.mPlayerHandler = this
        mPlayer.playFile(filepath)

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

    // Toast
    private fun initMessageToast() {
        mToast = Toast.makeText(this, "This totally shouldn't be seen", Toast.LENGTH_SHORT)
        mToast?.apply { setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0) }
    }

    override fun showToast(msg: String, cancel: Boolean) {
        mToast?.apply {
            if (cancel) cancel()
            setText(msg)
            show()
        }
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

    private fun updateOrientation(initial: Boolean = false) {
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


    companion object {
        private const val TAG = "mpv.PlayerActivity"

        private const val ASPECT_RATIO_MIN = 1.2f // covers 5:4 and up

        // action of result intent
        private const val RESULT_INTENT = "io.viper.android.mpv.player.PlayerActivity.result"
    }
}