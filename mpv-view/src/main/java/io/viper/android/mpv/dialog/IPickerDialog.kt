package io.viper.android.mpv.dialog

import android.view.LayoutInflater
import android.view.View

interface IPickerDialog {

    var number: Double?

    fun buildView(layoutInflater: LayoutInflater): View

    fun isInteger(): Boolean // eh....
}