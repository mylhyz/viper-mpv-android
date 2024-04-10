package io.viper.android.mpv.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.viper.android.mpv.NativeLibrary
import io.viper.android.mpv.core.AudioMetadata

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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.v(TAG, "BackgroundPlaybackService: starting")

        // read some metadata

        cachedMetadata.readAll()
        paused = NativeLibrary.getPropertyBoolean("pause")!!
        shouldShowPrevNext = (NativeLibrary.getPropertyInt("playlist-count") ?: 0) > 1

        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_NOT_STICKY
    }

    override fun eventProperty(property: String) {

    }

    override fun eventProperty(property: String, value: Long) {

    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property != "pause") return
        paused = value

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun eventProperty(property: String, value: String) {
        if (!cachedMetadata.update(property, value)) return

        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    override fun event(evtId: Int) {
        if (evtId == NativeLibrary.EventId.MPV_EVENT_SHUTDOWN) stopSelf()
    }

    private fun buildNotificationAction(
        @DrawableRes icon: Int, @StringRes title: Int, intentAction: String
    ): NotificationCompat.Action {
        val intent = NotificationButtonReceiver.createIntent(this, intentAction)

        val builder = NotificationCompat.Action.Builder(icon, getString(title), intent)
        with(builder) {
            setContextual(false)
            setShowsUserInterface(false)
            return build()
        }
    }

    private var cachedMetadata = AudioMetadata()
    private var paused: Boolean = false
    private var shouldShowPrevNext: Boolean = false

    @SuppressLint("UnspecifiedImmutableFlag")
    private fun buildNotification(): Notification {
        val notificationIntent = Intent(this, PlayerActivity::class.java)
        val pendingIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            else PendingIntent.getActivity(this, 0, notificationIntent, 0)

        val builder = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)

        with(builder) {
            setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            setPriority(NotificationCompat.PRIORITY_LOW)
            setContentTitle(cachedMetadata.formatTitle())
            setContentText(cachedMetadata.formatArtistAlbum())
            setSmallIcon(R.drawable.ic_mpv_symbolic)
            setContentIntent(pendingIntent)
            setOngoing(true)
        }

        thumbnail?.let {
            builder.setLargeIcon(it)

            builder.setColorized(true)
            // scale thumbnail to a single color in two steps
            val b1 = Bitmap.createScaledBitmap(it, 16, 16, true)
            val b2 = Bitmap.createScaledBitmap(b1, 1, 1, true)
            builder.setColor(b2.getPixel(0, 0))
            b2.recycle(); b1.recycle()
        }

        val playPauseAction = if (paused) buildNotificationAction(
            io.viper.android.mpv.view.R.drawable.ic_play_arrow_black_24dp,
            R.string.btn_play,
            "PLAY_PAUSE"
        )
        else buildNotificationAction(
            io.viper.android.mpv.view.R.drawable.ic_pause_black_24dp,
            R.string.btn_pause,
            "PLAY_PAUSE"
        )

        val style = androidx.media.app.NotificationCompat.MediaStyle()
        mediaToken?.let { style.setMediaSession(it) }
        if (shouldShowPrevNext) {
            builder.addAction(
                buildNotificationAction(
                    io.viper.android.mpv.view.R.drawable.ic_skip_previous_black_24dp,
                    R.string.dialog_prev,
                    "ACTION_PREV"
                )
            )
            builder.addAction(playPauseAction)
            builder.addAction(
                buildNotificationAction(
                    io.viper.android.mpv.view.R.drawable.ic_skip_next_black_24dp,
                    R.string.dialog_next,
                    "ACTION_NEXT"
                )
            )
            style.setShowActionsInCompactView(0, 2)
        } else {
            builder.addAction(playPauseAction)
        }
        builder.setStyle(style)

        return builder.build()
    }

    companion object {
        /* Using this property MPVActivity gives us a thumbnail to display alongside the permanent notification */
        var thumbnail: Bitmap? = null

        /* Same but for connecting the notification to the media session */
        var mediaToken: MediaSessionCompat.Token? = null

        private const val NOTIFICATION_ID = 12345
        private const val NOTIFICATION_CHANNEL_ID = "background_playback"

        private const val TAG = "mpv.Service"

        fun createNotificationChannel(context: Context) {
            val manager = NotificationManagerCompat.from(context)
            val builder = NotificationChannelCompat.Builder(NOTIFICATION_CHANNEL_ID,
                NotificationManagerCompat.IMPORTANCE_MIN)
            manager.createNotificationChannel(with (builder) {
                setName(context.getString(R.string.pref_background_play_title))
                build()
            })
        }
    }
}