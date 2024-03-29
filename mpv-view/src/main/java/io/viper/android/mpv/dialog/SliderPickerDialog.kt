package io.viper.android.mpv.dialog

import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.annotation.StringRes
import io.viper.android.mpv.view.databinding.DialogSliderBinding

class SliderPickerDialog(
    private val rangeMin: Double, private val rangeMax: Double, private val intScale: Int,
    @StringRes private val formatTextRes: Int
) : IPickerDialog {

    private lateinit var mBinding: DialogSliderBinding

    private fun unscale(it: Int): Double = rangeMin + it.toDouble() / intScale

    private fun scale(it: Double): Int = ((it - rangeMin) * intScale).toInt()
    override var number: Double?
        set(v) {
            mBinding.seekBar.progress = scale(v!!)
        }
        get() = unscale(mBinding.seekBar.progress)

    override fun buildView(layoutInflater: LayoutInflater): View {
        mBinding = DialogSliderBinding.inflate(layoutInflater)
        val context = layoutInflater.context

        mBinding.seekBar.max = ((rangeMax - rangeMin) * intScale).toInt()
        mBinding.seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(p0: SeekBar?, p1: Int, p2: Boolean) {
                val progress = unscale(p1)
                mBinding.textView.text = context.getString(formatTextRes, progress)
            }

            override fun onStartTrackingTouch(p0: SeekBar?) {}
            override fun onStopTrackingTouch(p0: SeekBar?) {}
        })
        mBinding.resetBtn.setOnClickListener {
            number = rangeMin + (rangeMax - rangeMin) / 2 // works for us
        }

        return mBinding.root
    }

    override fun isInteger(): Boolean = intScale == 1
}