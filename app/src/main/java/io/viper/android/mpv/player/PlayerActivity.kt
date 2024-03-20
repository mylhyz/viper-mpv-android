package io.viper.android.mpv.player

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import io.viper.android.mpv.IPlayerDelegate
import io.viper.android.mpv.view.PlayerView

class PlayerActivity : AppCompatActivity() {

    private val onLoadCommands = mutableListOf<Array<String>>()
    private var mToast: Toast? = null
    private val psc = PlaybackStateCache()
    private val mPlayerDelegate: IPlayerDelegate by lazy {
        findViewById(R.id.player_view)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // UI
        enableEdgeToEdge()
        setContentView(R.layout.activity_player)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initMessageToast()

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

        // player
        mPlayerDelegate.init(applicationContext.filesDir.path, applicationContext.cacheDir.path)
        mPlayerDelegate.playFile(filepath)
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

    private fun openContentFd(uri: Uri): String? {
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
        onLoadCommands.clear()
        if (extras == null) return

        // Refer to http://mpv-android.github.io/mpv-android/intent.html
        if (extras.getByte("decode_mode") == 2.toByte()) onLoadCommands.add(
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
                onLoadCommands.add(arrayOf("sub-add", subfile, flag))
            }
        }
        if (extras.getInt("position", 0) > 0) {
            val pos = extras.getInt("position", 0) / 1000f
            onLoadCommands.add(arrayOf("set", "start", pos.toString()))
        }
    }

    // Toast
    private fun initMessageToast() {
        mToast = Toast.makeText(this, "This totally shouldn't be seen", Toast.LENGTH_SHORT)
        mToast?.apply { setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 0) }
    }

    private fun showToast(msg: String, cancel: Boolean = false) {
        mToast?.apply {
            if (cancel) cancel()
            setText(msg)
            show()
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


    companion object {
        private const val TAG = "PlayerActivity"

        // action of result intent
        private const val RESULT_INTENT = "is.xyz.mpv.MPVActivity.result"
    }
}