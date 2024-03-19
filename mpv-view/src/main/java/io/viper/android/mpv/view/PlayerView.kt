package io.viper.android.mpv.view

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.viper.android.mpv.renderer.AndroidSurfaceView

class PlayerView @JvmOverloads constructor(
    content: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    FrameLayout(content, attrs, defStyleAttr) {

    init {
        addView(
            AndroidSurfaceView(content, attrs),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }
}