package com.example.hearo

import android.content.Context

/** Playback position for one tag: same context + track + ms. */
data class PlaybackProgress(
    val contextUri: String,
    val trackUri: String,
    val progressMs: Long
)

/** Persists NFC tag id -> playlist URL and per-tag playback progress. */
class NfcPlaylistRepository(context: Context) {

    private val prefs = context.getSharedPreferences("nfc_playlists", Context.MODE_PRIVATE)

    fun getPlaylistUrl(nfcId: String): String? =
        prefs.getString(keyPlaylist(nfcId), null)?.takeIf { it.isNotBlank() }

    fun setPlaylistUrl(nfcId: String, url: String) {
        prefs.edit().putString(keyPlaylist(nfcId), url).apply()
    }

    fun getProgress(nfcId: String): PlaybackProgress? {
        val contextUri = prefs.getString(keyProgressContext(nfcId), null) ?: return null
        val trackUri = prefs.getString(keyProgressTrack(nfcId), null) ?: return null
        val progressMs = prefs.getLong(keyProgressMs(nfcId), -1L)
        if (contextUri.isBlank() || trackUri.isBlank() || progressMs < 0) return null
        return PlaybackProgress(contextUri, trackUri, progressMs)
    }

    fun setProgress(nfcId: String, contextUri: String, trackUri: String, progressMs: Long) {
        prefs.edit()
            .putString(keyProgressContext(nfcId), contextUri)
            .putString(keyProgressTrack(nfcId), trackUri)
            .putLong(keyProgressMs(nfcId), progressMs.coerceAtLeast(0L))
            .apply()
    }

    fun remove(nfcId: String) {
        prefs.edit()
            .remove(keyPlaylist(nfcId))
            .remove(keyProgressContext(nfcId))
            .remove(keyProgressTrack(nfcId))
            .remove(keyProgressMs(nfcId))
            .apply()
    }

    private fun keyPlaylist(nfcId: String) = "playlist_$nfcId"
    private fun keyProgressContext(nfcId: String) = "progress_context_$nfcId"
    private fun keyProgressTrack(nfcId: String) = "progress_track_$nfcId"
    private fun keyProgressMs(nfcId: String) = "progress_ms_$nfcId"
}
