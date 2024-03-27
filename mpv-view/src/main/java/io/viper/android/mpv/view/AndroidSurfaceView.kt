package io.viper.android.mpv.view

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.SurfaceView
import io.viper.android.mpv.core.Player

class AndroidSurfaceView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    SurfaceView(context, attrs), SurfaceHolder.Callback {

    val mPlayer = Player()

    override fun surfaceCreated(holder: SurfaceHolder) {
        mPlayer.surfaceCreated(holder)

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        mPlayer.surfaceChanged(holder, format, width, height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        mPlayer.surfaceDestroyed(holder)
    }

    companion object {
        private const val TAG = "AndroidSurfaceView"
    }
}