package io.viper.android.mpv.player

import android.os.Handler
import io.viper.android.mpv.NativeLibrary

class HandlerEventObserver(
    private val observer: NativeLibrary.EventObserver,
    private val handler: Handler
) :
    NativeLibrary.EventObserver {

    override fun eventProperty(property: String) {
        handler.post {
            observer.eventProperty(property)
        }
    }

    override fun eventProperty(property: String, value: Long) {
        handler.post {
            observer.eventProperty(property, value)
        }
    }

    override fun eventProperty(property: String, value: Boolean) {
        handler.post {
            observer.eventProperty(property, value)
        }
    }

    override fun eventProperty(property: String, value: String) {
        handler.post {
            observer.eventProperty(property, value)
        }
    }

    override fun event(evtId: Int) {
        handler.post {
            observer.event(evtId)
        }
    }
}