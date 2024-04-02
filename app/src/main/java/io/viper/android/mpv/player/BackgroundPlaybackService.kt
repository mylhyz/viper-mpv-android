package io.viper.android.mpv.player

import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import io.viper.android.mpv.NativeLibrary

class BackgroundPlaybackService : Service(), NativeLibrary.EventObserver {

    override fun onCreate() {
        super.onCreate()
        NativeLibrary.addEventObserver(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        NativeLibrary.removeEventObserver(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun eventProperty(property: String) {
        
    }

    override fun eventProperty(property: String, value: Long) {
        
    }

    override fun eventProperty(property: String, value: Boolean) {
        
    }

    override fun eventProperty(property: String, value: String) {
        
    }

    override fun event(evtId: Int) {
        
    }

    companion object {
        /* Using this property MPVActivity gives us a thumbnail to display alongside the permanent notification */
        var thumbnail: Bitmap? = null

        /* Same but for connecting the notification to the media session */
        var mediaToken: MediaSessionCompat.Token? = null

        private const val NOTIFICATION_ID = 12345
        private const val NOTIFICATION_CHANNEL_ID = "background_playback"
    }
}