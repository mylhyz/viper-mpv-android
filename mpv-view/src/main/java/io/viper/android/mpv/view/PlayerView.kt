package io.viper.android.mpv.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.viper.android.mpv.core.Player

class PlayerView @JvmOverloads constructor(
    content: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : FrameLayout(content, attrs, defStyleAttr) {

    private var mSurfaceView: AndroidSurfaceView? = null
    private var mPlayer: Player? = null

    init {
        mSurfaceView = AndroidSurfaceView(content, attrs)
        mPlayer = mSurfaceView!!.mPlayer
        addView(
            mSurfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    fun attachToPlayer(
        configDir: String,
        cacheDir: String
    ) {
        mPlayer!!.init(this.context, configDir, cacheDir, mSurfaceView!!.holder, mSurfaceView!!)
    }

    fun getAsPlayer(): Player {
        return mPlayer!!
    }
}