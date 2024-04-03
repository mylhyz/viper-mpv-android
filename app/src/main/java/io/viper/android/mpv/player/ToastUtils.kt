package io.viper.android.mpv.player

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast

object ToastUtils {

    private var mToast: Toast? = null
    fun showToast(context: Context, message: String, duration: Int, cancel: Boolean = false) {
        if (cancel && mToast != null) {
            mToast!!.cancel()
        }
        mToast = Toast(context)
        mToast?.let {
            //可以是其他自定义布局
            val rootView = LayoutInflater.from(context).inflate(R.layout.toast_view, null, false)

            //设置消息
            val txtContent = rootView.findViewById<TextView>(R.id.toast_text)
            txtContent.text = message

            it.view = rootView
            it.duration = duration
            it.show()
        }
    }
}