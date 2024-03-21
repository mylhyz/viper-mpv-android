package io.viper.android.mpv.hud

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import io.viper.android.mpv.view.R

class HudContainer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) :
    FrameLayout(context, attrs, defStyleAttr) {

    init {
        inflate(context, R.layout.hud_container, this)
    }
}