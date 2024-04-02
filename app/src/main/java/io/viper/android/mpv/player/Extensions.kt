package io.viper.android.mpv.player

import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import io.viper.android.mpv.core.PlaybackStateCache
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

fun findRealPath(fd: Int): String? {
    var ins: InputStream? = null
    try {
        val path = File("/proc/self/fd/${fd}").canonicalPath
        if (!path.startsWith("/proc") && File(path).canRead()) {
            // Double check that we can read it
            ins = FileInputStream(path)
            ins.read()
            return path
        }
    } catch (e: Exception) {
        // ignored
    } finally {
        ins?.close()
    }
    return null
}

fun PlaybackStateCache.buildMediaMetadata(includeThumb: Boolean): MediaMetadataCompat {
    // TODO could provide: genre, num_tracks, track_number, year
    return with(MediaMetadataCompat.Builder()) {
        putText(MediaMetadataCompat.METADATA_KEY_ALBUM, meta.mediaAlbum)
        if (includeThumb && BackgroundPlaybackService.thumbnail != null)
            putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BackgroundPlaybackService.thumbnail)
        putText(MediaMetadataCompat.METADATA_KEY_ARTIST, meta.mediaArtist)
        putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration.takeIf { it > 0 } ?: -1)
        putText(MediaMetadataCompat.METADATA_KEY_TITLE, meta.mediaTitle)
        build()
    }
}

fun PlaybackStateCache.buildPlaybackState(): PlaybackStateCompat {
    val stateInt = when {
        position < 0 || duration <= 0 -> PlaybackStateCompat.STATE_NONE
        cachePause -> PlaybackStateCompat.STATE_BUFFERING
        pause -> PlaybackStateCompat.STATE_PAUSED
        else -> PlaybackStateCompat.STATE_PLAYING
    }
    var actions = PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_SET_REPEAT_MODE
    if (duration > 0)
        actions = actions or PlaybackStateCompat.ACTION_SEEK_TO
    if (playlistCount > 1) {
        // we could be very pedantic here but it's probably better to either show both or none
        actions = actions or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
    }
    return with(PlaybackStateCompat.Builder()) {
        setState(stateInt, position, 1.0f)
        setActions(actions)
        //setActiveQueueItemId(0) TODO
        build()
    }
}

fun PlaybackStateCache.write(session: MediaSessionCompat, includeThumb: Boolean = true) {
    with(session) {
        setMetadata(buildMediaMetadata(includeThumb))
        val ps = buildPlaybackState()
        isActive = ps.state != PlaybackStateCompat.STATE_NONE
        setPlaybackState(ps)
        //setQueue(listOf()) TODO
    }
}


