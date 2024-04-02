package io.viper.android.mpv.core

// does about 200% more than AudioMetadata
class PlaybackStateCache {
    val meta = AudioMetadata()
    var cachePause = false
        private set
    var pause = false
        private set
    var position = -1L // in ms
        private set
    var duration = 0L // in ms
        private set
    var playlistPos = 0
        private set
    var playlistCount = 0
        private set

    val position_s get() = (position / 1000).toInt()
    val duration_s get() = (duration / 1000).toInt()

    fun reset() {
        position = -1
        duration = 0
    }

    fun update(property: String, value: String): Boolean {
        if (meta.update(property, value))
            return true
        return false
    }

    fun update(property: String, value: Boolean): Boolean {
        when (property) {
            "pause" -> pause = value
            "paused-for-cache" -> cachePause = value
            else -> return false
        }
        return true
    }

    fun update(property: String, value: Long): Boolean {
        when (property) {
            "time-pos" -> position = value * 1000
            "duration" -> duration = value * 1000
            "playlist-pos" -> playlistPos = value.toInt()
            "playlist-count" -> playlistCount = value.toInt()
            else -> return false
        }
        return true
    }
}