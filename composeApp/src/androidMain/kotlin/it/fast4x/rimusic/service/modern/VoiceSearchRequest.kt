package it.fast4x.rimusic.service.modern

import android.os.Bundle
import android.provider.MediaStore

/**
 * A play request from Gemini or Google Assistant, for example "Play my playlist Roadtrip on
 * KruXx". The assistant passes the recognised category as [MediaStore.EXTRA_MEDIA_FOCUS].
 */
internal sealed interface VoiceSearchRequest {

    /** "Play music on KruXx": nothing specific was asked for. */
    data object Anything : VoiceSearchRequest

    data class Playlist(val name: String, val query: String) : VoiceSearchRequest

    data class Artist(val name: String, val query: String) : VoiceSearchRequest

    data class Album(val title: String, val artist: String?, val query: String) : VoiceSearchRequest

    /** Free text, or a song request turned into "title artist". */
    data class Text(val query: String) : VoiceSearchRequest

    companion object {

        fun from(query: String?, extras: Bundle?): VoiceSearchRequest {
            val text = query?.trim().orEmpty()
            fun extra(key: String) = extras?.getString(key)?.trim()?.takeIf(String::isNotEmpty)

            val focused = when (extras?.getString(MediaStore.EXTRA_MEDIA_FOCUS)) {
                MediaStore.Audio.Playlists.ENTRY_CONTENT_TYPE ->
                    extra(MediaStore.EXTRA_MEDIA_PLAYLIST)?.let { Playlist(it, text) }
                MediaStore.Audio.Artists.ENTRY_CONTENT_TYPE ->
                    extra(MediaStore.EXTRA_MEDIA_ARTIST)?.let { Artist(it, text) }
                MediaStore.Audio.Albums.ENTRY_CONTENT_TYPE ->
                    extra(MediaStore.EXTRA_MEDIA_ALBUM)?.let {
                        Album(it, extra(MediaStore.EXTRA_MEDIA_ARTIST), text)
                    }
                MediaStore.Audio.Media.ENTRY_CONTENT_TYPE ->
                    extra(MediaStore.EXTRA_MEDIA_TITLE)?.let { title ->
                        Text(listOfNotNull(title, extra(MediaStore.EXTRA_MEDIA_ARTIST)).joinToString(" "))
                    }
                else -> null
            }
            return focused ?: if (text.isEmpty()) Anything else Text(text)
        }
    }
}

/** Trimmed and case-insensitive, for matching spoken names against library entries. */
internal fun String.voiceKey(): String = trim().lowercase().replace(Regex("\\s+"), " ")
