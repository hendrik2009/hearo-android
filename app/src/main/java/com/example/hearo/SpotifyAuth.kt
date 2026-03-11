package com.example.hearo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.Executors

class SpotifyAuth(private val context: Context) {

    private val tokenStore by lazy { SpotifyTokenStore(context) }
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /** Invoked on main thread when refresh fails (e.g. revoked token). Activity should clear UI and show Sign in. */
    var onRefreshFailed: (() -> Unit)? = null

    fun hasToken(): Boolean = tokenStore.getAccessToken() != null

    fun getValidAccessToken(): String? {
        var token = tokenStore.getAccessToken() ?: run {
            Log.d(TAG, "getValidAccessToken: no stored token")
            return null
        }
        if (tokenStore.isAccessTokenExpired()) {
            Log.d(TAG, "getValidAccessToken: token expired, refreshing")
            token = refreshAccessToken() ?: run {
                Log.w(TAG, "getValidAccessToken: refresh failed")
                mainHandler.post { onRefreshFailed?.invoke() }
                return null
            }
        }
        return token
    }

    /** Expiry time (ms) we store; used for scheduling. */
    fun getExpiresAtMs(): Long = tokenStore.getExpiresAtMs()

    /** Nominal token lifetime in seconds from Spotify (e.g. 3600). */
    fun getExpiresInSeconds(): Int = tokenStore.getExpiresInSeconds()

    /** When to run proactive refresh (epoch ms). Refresh at 3/4 of lifetime. Null if no token. */
    fun getRefreshDueAtMs(): Long? {
        if (!hasToken()) return null
        val expiresAt = tokenStore.getExpiresAtMs()
        val lifetimeMs = tokenStore.getExpiresInSeconds() * 1000L
        return expiresAt - (lifetimeMs / 4)
    }

    fun signOut() {
        tokenStore.clear()
    }

    fun signIn(clientId: String) {
        if (clientId.isBlank()) return
        val state = java.util.UUID.randomUUID().toString().replace("-", "")
        val (verifier, challenge) = generatePkce()
        tokenStore.setPendingPkce(verifier, state)
        val scope = "user-read-playback-state user-modify-playback-state"
        val authUrl = "https://accounts.spotify.com/authorize?" +
            "client_id=${Uri.encode(clientId)}" +
            "&response_type=code" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
            "&scope=${Uri.encode(scope)}" +
            "&code_challenge=${Uri.encode(challenge)}" +
            "&code_challenge_method=S256" +
            "&state=${Uri.encode(state)}"
        CustomTabsIntent.Builder().build().launchUrl(context, Uri.parse(authUrl))
        (context as? Activity)?.overridePendingTransition(0, 0)
    }

    fun handleRedirect(intent: Intent?, onResult: (Boolean) -> Unit) {
        val data = intent?.data ?: run { onResult(false); return }
        if (data.scheme != "hearo" || data.host != "callback") { onResult(false); return }
        val code = data.getQueryParameter("code") ?: run {
            Log.w(TAG, "handleRedirect: no code in intent")
            onResult(false); return
        }
        val state = data.getQueryParameter("state")
        val pkce = tokenStore.getPendingPkce()
        if (pkce == null) {
            Log.w(TAG, "handleRedirect: no pending PKCE")
            onResult(false); return
        }
        val (verifier, savedState) = pkce
        if (state != savedState) {
            Log.w(TAG, "handleRedirect: state mismatch")
            onResult(false); return
        }
        tokenStore.clearPendingPkce()
        Executors.newSingleThreadExecutor().execute {
            val success = exchangeCodeForTokens(code, verifier)
            android.os.Handler(android.os.Looper.getMainLooper()).post { onResult(success) }
        }
    }

    private fun exchangeCodeForTokens(code: String, codeVerifier: String): Boolean {
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        if (clientId.isBlank()) return false
        val body = "grant_type=authorization_code" +
            "&code=${Uri.encode(code)}" +
            "&redirect_uri=${Uri.encode(REDIRECT_URI)}" +
            "&client_id=${Uri.encode(clientId)}" +
            "&code_verifier=${Uri.encode(codeVerifier)}"
        val request = okhttp3.Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
        return try {
            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.e(TAG, "exchangeCodeForTokens failed: ${response.code} $bodyStr")
                return false
            }
            val json = JSONObject(bodyStr)
            val accessToken = json.optString("access_token").takeIf { it.isNotBlank() }
            if (accessToken == null) {
                Log.e(TAG, "exchangeCodeForTokens: 200 but missing access_token")
                return false
            }
            val refreshToken = json.optString("refresh_token").takeIf { it.isNotBlank() }
            val expiresIn = json.optInt("expires_in", 3600)
            tokenStore.setTokens(accessToken, refreshToken, expiresIn)
            true
        } catch (e: Exception) {
            Log.e(TAG, "exchangeCodeForTokens exception: ${e.message}", e)
            false
        }
    }

    fun refreshAccessToken(): String? {
        val refreshToken = tokenStore.getRefreshToken() ?: return null
        val clientId = BuildConfig.SPOTIFY_CLIENT_ID
        if (clientId.isBlank()) return null
        val body = "grant_type=refresh_token" +
            "&refresh_token=${Uri.encode(refreshToken)}" +
            "&client_id=${Uri.encode(clientId)}"
        val request = okhttp3.Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .post(body.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .build()
        return try {
            val response = okhttp3.OkHttpClient().newCall(request).execute()
            val bodyStr = response.body?.string() ?: ""
            if (!response.isSuccessful) {
                Log.w(TAG, "refreshAccessToken: ${response.code} $bodyStr")
                return null
            }
            val json = JSONObject(bodyStr)
            val accessToken = json.optString("access_token").takeIf { it.isNotBlank() } ?: return null
            val newRefresh = json.optString("refresh_token").takeIf { it.isNotBlank() }
            val expiresIn = json.optInt("expires_in", 3600)
            tokenStore.setTokens(accessToken, newRefresh ?: refreshToken, expiresIn)
            accessToken
        } catch (_: Exception) { null }
    }

    private fun generatePkce(): Pair<String, String> {
        val verifier = ByteArray(32).also { java.security.SecureRandom().nextBytes(it) }
        val verifierStr = Base64.encodeToString(verifier, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        val digest = MessageDigest.getInstance("SHA-256").digest(verifierStr.toByteArray(Charsets.US_ASCII))
        val challenge = Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return verifierStr to challenge
    }

    companion object {
        private const val TAG = "SpotifyAuth"
        const val REDIRECT_URI = "hearo://callback"
    }
}
