package io.viper.android.mpv.dialog

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import io.viper.android.mpv.view.databinding.DialogDecimalBinding

class DecimalPickerDialog(private val rangeMin: Double, private val rangeMax: Double) :
    IPickerDialog {

    private lateinit var mBinding: DialogDecimalBinding

    override var number: Double?
        set(v) = mBinding.editText.setText(v!!.toString())
        get() = mBinding.editText.text.toString().toDoubleOrNull()

    override fun buildView(layoutInflater: LayoutInflater): View {
        mBinding = DialogDecimalBinding.inflate(layoutInflater)

        mBinding.editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val value = s!!.toString().toDoubleOrNull() ?: return
                val valueBounded = value.coerceIn(rangeMin, rangeMax)
                if (valueBounded != value)
                    mBinding.editText.setText(valueBounded.toString())
            }
        })
        mBinding.btnMinus.setOnClickListener {
            val value = this.number ?: 0.0
            this.number = (value - STEP).coerceIn(rangeMin, rangeMax)
        }
        mBinding.btnPlus.setOnClickListener {
            val value = this.number ?: 0.0
            this.number = (value + STEP).coerceIn(rangeMin, rangeMax)
        }

        return mBinding.root
    }

    override fun isInteger(): Boolean = false

    companion object {
        private const val STEP = 1.0
    }
}