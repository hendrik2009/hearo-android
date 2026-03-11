package com.example.hearo

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

internal class SpotifyTokenStore(context: Context) {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "spotify_auth",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getAccessToken(): String? = prefs.getString(KEY_ACCESS_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun getRefreshToken(): String? = prefs.getString(KEY_REFRESH_TOKEN, null)?.takeIf { it.isNotBlank() }
    fun getExpiresAtMs(): Long = prefs.getLong(KEY_EXPIRES_AT_MS, 0L)
    /** Nominal access token lifetime in seconds (e.g. 3600). Used for proactive refresh scheduling. */
    fun getExpiresInSeconds(): Int = prefs.getInt(KEY_EXPIRES_IN_SECONDS, 3600)

    fun setTokens(accessToken: String, refreshToken: String?, expiresInSeconds: Int) {
        val editor = prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putLong(KEY_EXPIRES_AT_MS, System.currentTimeMillis() + expiresInSeconds * 1000L - 60_000)
            .putInt(KEY_EXPIRES_IN_SECONDS, expiresInSeconds)
        refreshToken?.let { editor.putString(KEY_REFRESH_TOKEN, it) }
        editor.apply()
    }

    fun setPendingPkce(codeVerifier: String, state: String) {
        prefs.edit()
            .putString(KEY_PKCE_VERIFIER, codeVerifier)
            .putString(KEY_PKCE_STATE, state)
            .apply()
    }

    fun getPendingPkce(): Pair<String, String>? {
        val verifier = prefs.getString(KEY_PKCE_VERIFIER, null) ?: return null
        val state = prefs.getString(KEY_PKCE_STATE, null) ?: return null
        return verifier to state
    }

    fun clearPendingPkce() {
        prefs.edit().remove(KEY_PKCE_VERIFIER).remove(KEY_PKCE_STATE).apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_EXPIRES_AT_MS)
            .remove(KEY_EXPIRES_IN_SECONDS)
            .remove(KEY_PKCE_VERIFIER)
            .remove(KEY_PKCE_STATE)
            .apply()
    }

    fun isAccessTokenExpired(): Boolean = System.currentTimeMillis() >= getExpiresAtMs()

    companion object {
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_EXPIRES_AT_MS = "expires_at_ms"
        private const val KEY_EXPIRES_IN_SECONDS = "expires_in_seconds"
        private const val KEY_PKCE_VERIFIER = "pkce_verifier"
        private const val KEY_PKCE_STATE = "pkce_state"
    }
}
