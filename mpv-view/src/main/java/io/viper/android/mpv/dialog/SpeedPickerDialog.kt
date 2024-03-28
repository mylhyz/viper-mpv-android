package io.viper.android.mpv.dialog

import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import io.viper.android.mpv.view.R
import io.viper.android.mpv.view.databinding.DialogSliderBinding
import kotlin.math.max

class SpeedPickerDialog : IPickerDialog {

    companion object {
        // Middle point of bar (in progress units)
        private const val HALF = 100.0

        // Minimum for <1.0 range (absolute)
        private const val MINIMUM = 0.2

        // Scale factor for >=1.0 range (in progress units)
        private const val SCALE_FACTOR = 20.0
    }

    private lateinit var mBinding: DialogSliderBinding

    private fun toSpeed(it: Int): Double {
        return if (it >= HALF) (it - HALF) / SCALE_FACTOR + 1.0
        else max(MINIMUM, it / HALF)
    }

    private fun fromSpeed(it: Double): Int {
        return if (it >= 1.0) (HALF + (it - 1.0) * SCALE_FACTOR).toInt()
        else (HALF * max(MINIMUM, it)).toInt()
    }

    override var number: Double?
        get() = toSpeed(mBinding.seekBar.progress)
        set(value) {
            mBinding.seekBar.progress = fromSpeed(value!!)
        }

    override fun buildView(layoutInflater: LayoutInflater): View {
        mBinding = DialogSliderBinding.inflate(layoutInflater)
        mBinding.seekBar.max = 200
        val context = layoutInflater.context
        mBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                val progress = toSpeed(p1)
                mBinding.textView.text = context.getString(R.string.ui_speed, progress)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        mBinding.resetBtn.setOnClickListener {
            number = 1.0
        }
        mBinding.textView.isAllCaps = true // match appearance in controls
        return mBinding.root
    }

    override fun isInteger(): Boolean = false
}