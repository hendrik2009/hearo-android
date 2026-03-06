package com.example.hearo

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

private const val TAG = "HearoPlaylist"
private const val SPOTIFY_PACKAGE = "com.spotify.music"

/**
 * Media control and playback: play URI via Intent, pause via MediaController.
 * Requires Notification Listener access to get Spotify's MediaSession for pause.
 */
class SpotifyController(
    private val context: Context,
    private val notificationListenerComponent: ComponentName?
) {

    /** Returns true if the Spotify app has a running process. Not 100% reliable on all devices. */
    fun isSpotifyRunning(): Boolean {
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            @Suppress("DEPRECATION")
            am.runningAppProcesses?.any { proc ->
                proc.pkgList?.any { it == SPOTIFY_PACKAGE } == true
            } ?: false
        } catch (_: Exception) { false }
    }

    /** Launches the Spotify app if installed. Returns true if the intent was sent. */
    fun launchSpotify(): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_MAIN).apply {
                setPackage(SPOTIFY_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Log.d(TAG, "launchSpotify: started Spotify")
            true
        } catch (e: android.content.ActivityNotFoundException) {
            Log.w(TAG, "launchSpotify: Spotify not installed")
            false
        } catch (e: Exception) {
            Log.w(TAG, "launchSpotify failed: ${e.message}")
            false
        }
    }

    /** Starts playback of a Spotify URI (playlist, album, etc.) by opening it in the Spotify app. */
    fun playUri(uri: String) {
        if (uri.isBlank()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(uri)
                setPackage(SPOTIFY_PACKAGE)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            Log.d(TAG, "playUri: launched Spotify with $uri")
        } catch (e: Exception) {
            Log.w(TAG, "playUri failed: ${e.message}")
        }
    }

    /** Pauses Spotify playback via MediaController. Requires Notification Listener access. */
    fun pause() {
        try {
            val controller = getSpotifyMediaController() ?: return
            controller.transportControls.pause()
            Log.d(TAG, "pause: sent pause to Spotify")
        } catch (e: Exception) {
            Log.w(TAG, "pause failed: ${e.message}")
        }
    }

    fun skipToNext() {
        try {
            getSpotifyMediaController()?.transportControls?.skipToNext()
        } catch (_: Exception) { }
    }

    fun skipToPrevious() {
        try {
            getSpotifyMediaController()?.transportControls?.skipToPrevious()
        } catch (_: Exception) { }
    }

    /** Seeks [deltaMs] from current position (negative = backward). */
    fun seekRelative(deltaMs: Long) {
        try {
            val controller = getSpotifyMediaController() ?: return
            val state = controller.playbackState ?: return
            val pos = state.position
            val newPos = (pos + deltaMs).coerceAtLeast(0L)
            controller.transportControls.seekTo(newPos)
        } catch (_: Exception) { }
    }

    /** Fallback: current playlist/context URI from MediaSession if available; otherwise null. */
    fun getCurrentPlaylistOrContextUri(): String? {
        val uri = getFromMediaSession()
        Log.d(TAG, "fallback getCurrentPlaylistOrContextUri: $uri")
        return uri
    }

    private fun getSpotifyMediaController(): android.media.session.MediaController? {
        if (notificationListenerComponent == null) return null
        return try {
            val manager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as android.media.session.MediaSessionManager
            val controllers: List<android.media.session.MediaController> = manager.getActiveSessions(notificationListenerComponent) ?: return null
            controllers.firstOrNull { it.packageName == SPOTIFY_PACKAGE }
        } catch (_: Exception) { null }
    }

    private fun getFromMediaSession(): String? {
        val controller = getSpotifyMediaController() ?: return null
        return try {
            val queue = controller.queue ?: return null
            if (queue.isEmpty()) return null
            val desc = queue[0].description ?: return null
            desc.mediaId?.toString()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }

    companion object {
        fun notificationListenerComponent(context: Context): ComponentName? =
            ComponentName(context, HearoNotificationListenerService::class.java)
    }
}
