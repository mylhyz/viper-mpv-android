package io.viper.android.mpv.core

import io.viper.android.mpv.NativeLibrary

class AudioMetadata {
    var mediaTitle: String? = null
        private set
    var mediaArtist: String? = null
        private set
    var mediaAlbum: String? = null
        private set

    fun readAll() {
        mediaTitle = NativeLibrary.getPropertyString("media-title")
        mediaArtist = NativeLibrary.getPropertyString("metadata/by-key/Artist")
        mediaAlbum = NativeLibrary.getPropertyString("metadata/by-key/Album")
    }

    fun update(property: String, value: String): Boolean {
        when (property) {
            "media-title" -> mediaTitle = value
            "metadata/by-key/Artist" -> mediaArtist = value
            "metadata/by-key/Album" -> mediaAlbum = value
            else -> return false
        }
        return true
    }

    fun formatTitle(): String? = if (!mediaTitle.isNullOrEmpty()) mediaTitle else null

    fun formatArtistAlbum(): String? {
        val artistEmpty = mediaArtist.isNullOrEmpty()
        val albumEmpty = mediaAlbum.isNullOrEmpty()
        return when {
            !artistEmpty && !albumEmpty -> "$mediaArtist / $mediaAlbum"
            !artistEmpty -> mediaAlbum
            !albumEmpty -> mediaArtist
            else -> null
        }
    }
}