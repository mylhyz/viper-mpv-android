package io.viper.android.mpv.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.viper.android.mpv.IPlayer
import io.viper.android.mpv.IPlayerDelegate
import io.viper.android.mpv.renderer.AndroidSurfaceView

class PlayerView @JvmOverloads constructor(
    content: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    FrameLayout(content, attrs, defStyleAttr), IPlayerDelegate {

    private var mOrigin: IPlayer? = null

    init {
        mOrigin = AndroidSurfaceView(content, attrs)
        addView(
            mOrigin as AndroidSurfaceView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    override fun init(configDir: String, cacheDir: String) {
        mOrigin?.apply {
            init(configDir, cacheDir)
        }
    }

    override fun playFile(fp: String) {
        mOrigin?.apply {
            playFile(fp)
        }
    }
}