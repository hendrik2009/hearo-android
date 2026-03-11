package com.example.hearo

import android.util.Log
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val TAG = "HearoPlaylist"

/** A Spotify device (phone, speaker, etc.) from GET /me/player/devices. */
data class SpotifyDevice(val id: String, val name: String?, val type: String?, val isActive: Boolean)

/** Current player state from GET /me/player. */
data class PlayerState(
    val contextUri: String?,
    val trackUri: String?,
    val trackName: String?,
    val artistName: String?,
    val progressMs: Long,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false
)

/**
 * Spotify Web API: get playback state, start playback by context URI (with optional position), pause.
 * Keeps the user in Hearo; playback is controlled in the background.
 */
class SpotifyWebApi(private val auth: SpotifyAuth) {

    /**
     * Sets the playback context to [uri] and optionally starts at [trackUri] and [positionMs].
     * [deviceId] targets a specific device (avoids "no active device" when Spotify was just started).
     */
    fun playContextUri(
        uri: String,
        trackUri: String? = null,
        positionMs: Long? = null,
        deviceId: String? = null,
        callback: (Boolean) -> Unit
    ) {
        val token = auth.getValidAccessToken()
        if (token == null) {
            Log.w(TAG, "playContextUri: no token")
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            return
        }
        var url = "https://api.spotify.com/v1/me/player/play"
        if (!deviceId.isNullOrBlank()) {
            url += "?device_id=${java.net.URLEncoder.encode(deviceId, "UTF-8")}"
        }
        val body = JSONObject().apply {
            put("context_uri", uri)
            if (!trackUri.isNullOrBlank()) {
                put("offset", JSONObject().put("uri", trackUri))
            }
            if (positionMs != null && positionMs > 0) {
                put("position_ms", positionMs)
            }
        }.toString()
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "playContextUri onFailure: ${e.message}")
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                val ok = response.code in 200..204
                if (!ok) Log.w(TAG, "playContextUri: ${response.code} $bodyStr")
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(ok) }
            }
        })
    }

    /**
     * Transfers playback to [deviceId] so it becomes the active device. Call before play when you get NO_ACTIVE_DEVICE.
     */
    fun transferPlayback(deviceId: String, callback: (Boolean) -> Unit) {
        val token = auth.getValidAccessToken()
        if (token == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            return
        }
        val body = JSONObject().put("device_ids", JSONArray().put(deviceId)).put("play", false).toString()
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player")
            .addHeader("Authorization", "Bearer $token")
            .addHeader("Content-Type", "application/json")
            .put(body.toRequestBody("application/json".toMediaType()))
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.w(TAG, "transferPlayback onFailure: ${e.message}")
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            }
            override fun onResponse(call: Call, response: Response) {
                val ok = response.code in 200..204
                if (!ok) Log.w(TAG, "transferPlayback: ${response.code} ${response.body?.string()?.take(150)}")
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(ok) }
            }
        })
    }

    /**
     * Fetches available devices. Use the first (or active) device id with playContextUri to avoid "no active device".
     */
    fun getDevices(callback: (List<SpotifyDevice>) -> Unit) {
        val token = auth.getValidAccessToken()
        if (token == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(emptyList()) }
            return
        }
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player/devices")
            .addHeader("Authorization", "Bearer $token")
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(emptyList()) }
            }
            override fun onResponse(call: Call, response: Response) {
                val list = if (response.code == 200) parseDevices(response.body?.string() ?: "") else emptyList()
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(list) }
            }
        })
    }

    private fun parseDevices(body: String): List<SpotifyDevice> {
        return try {
            val json = JSONObject(body)
            val arr = json.optJSONArray("devices") ?: return emptyList()
            List(arr.length()) { i ->
                val obj = arr.optJSONObject(i) ?: return@List null
                val id = obj.optString("id").takeIf { it.isNotBlank() } ?: return@List null
                SpotifyDevice(
                    id = id,
                    name = obj.optString("name").takeIf { it.isNotBlank() },
                    type = obj.optString("type").takeIf { it.isNotBlank() },
                    isActive = obj.optBoolean("is_active", false)
                )
            }.filterNotNull()
        } catch (_: Exception) { emptyList() }
    }

    /**
     * Fetches current player state (context URI, current track URI, progress_ms).
     * Returns null if 204 (no player) or error.
     */
    fun getPlayerState(callback: (PlayerState?) -> Unit) {
        val token = auth.getValidAccessToken()
        if (token == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(null) }
            return
        }
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player")
            .addHeader("Authorization", "Bearer $token")
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                val state = when (response.code) {
                    200 -> parsePlayerState(bodyStr)
                    204 -> null
                    else -> null
                }
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(state) }
            }
        })
    }

    private fun parsePlayerState(body: String): PlayerState? {
        return try {
            val json = JSONObject(body)
            val context = json.optJSONObject("context")
            val contextUri = context?.optString("uri")?.takeIf { it.isNotBlank() }
            val item = json.optJSONObject("item")
            val trackUri = item?.optString("uri")?.takeIf { it.isNotBlank() }
            val trackName = item?.optString("name")?.takeIf { it.isNotBlank() }
            val artistName = item?.optJSONArray("artists")?.optJSONObject(0)?.optString("name")?.takeIf { it.isNotBlank() }
            val progressMs = json.optLong("progress_ms", 0L)
            val durationMs = item?.optLong("duration_ms", 0L) ?: 0L
            val isPlaying = json.optBoolean("is_playing", false)
            PlayerState(contextUri, trackUri, trackName, artistName, progressMs, durationMs, isPlaying)
        } catch (_: Exception) { null }
    }

    /** Skips to next track. */
    fun skipNext(callback: (Boolean) -> Unit = {}) {
        apiPost("https://api.spotify.com/v1/me/player/next", callback)
    }

    /** Skips to previous track. */
    fun skipPrevious(callback: (Boolean) -> Unit = {}) {
        apiPost("https://api.spotify.com/v1/me/player/previous", callback)
    }

    /** Seeks to [positionMs] in the current track (0-based). */
    fun seekTo(positionMs: Long, callback: (Boolean) -> Unit = {}) {
        val token = auth.getValidAccessToken() ?: run {
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            return
        }
        val url = "https://api.spotify.com/v1/me/player/seek?position_ms=${positionMs.coerceAtLeast(0)}"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .put("".toRequestBody("application/json".toMediaType()))
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            }
            override fun onResponse(call: Call, response: Response) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(response.code in 200..204)
                }
            }
        })
    }

    /** Seeks [deltaMs] from current position (negative = back). Uses getPlayerState then seekTo. */
    fun seekRelative(deltaMs: Long, callback: (Boolean) -> Unit = {}) {
        getPlayerState { state ->
            if (state == null) {
                callback(false)
                return@getPlayerState
            }
            val duration = state.durationMs.coerceAtLeast(0L)
            val newPos = (state.progressMs + deltaMs).coerceIn(0L, duration)
            seekTo(newPos, callback)
        }
    }

    private fun apiPost(url: String, callback: (Boolean) -> Unit) {
        val token = auth.getValidAccessToken()
        if (token == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            return
        }
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $token")
            .post("".toRequestBody("application/json".toMediaType()))
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            }
            override fun onResponse(call: Call, response: Response) {
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    callback(response.code in 200..204)
                }
            }
        })
    }

    /** Pauses playback. Requires scope user-modify-playback-state. */
    fun pause(callback: (Boolean) -> Unit) {
        val token = auth.getValidAccessToken()
        if (token == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            return
        }
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player/pause")
            .addHeader("Authorization", "Bearer $token")
            .put("".toRequestBody("application/json".toMediaType()))
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(false) }
            }

            override fun onResponse(call: Call, response: Response) {
                val ok = response.code in 200..204
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(ok) }
            }
        })
    }

    fun getCurrentContextUri(callback: (String?) -> Unit) {
        val token = auth.getValidAccessToken()
        Log.d(TAG, "Web API: GET /me/player, token present=${token != null}")
        if (token == null) {
            android.os.Handler(android.os.Looper.getMainLooper()).post { callback(null) }
            return
        }
        val request = Request.Builder()
            .url("https://api.spotify.com/v1/me/player")
            .addHeader("Authorization", "Bearer $token")
            .build()
        OkHttpClient().newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.d(TAG, "Web API: onFailure ${e.message}")
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(null) }
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: ""
                Log.d(TAG, "Web API: code=${response.code}, body=${bodyStr.take(200)}")
                val contextUri = when (response.code) {
                    200 -> parseContextUri(bodyStr)
                    204 -> {
                        Log.d(TAG, "Web API: 204 no active player")
                        null
                    }
                    else -> null
                }
                Log.d(TAG, "Web API: context.uri=$contextUri")
                android.os.Handler(android.os.Looper.getMainLooper()).post { callback(contextUri) }
            }
        })
    }

    private fun parseContextUri(body: String): String? {
        return try {
            val json = JSONObject(body)
            val context = json.optJSONObject("context") ?: return null
            context.optString("uri").takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }
}
