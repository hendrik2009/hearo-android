package com.example.hearo

import android.content.Context
import java.util.Calendar

/** Persists listening limit and counter. Limit is applied on reset (manual or midnight). */
class ListeningTimeStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** User-set limit (duration in seconds). Applied as active limit on next reset. */
    fun getLimitSeconds(): Int = prefs.getInt(KEY_LIMIT_SECONDS, DEFAULT_LIMIT_SECONDS).coerceIn(MIN_LIMIT_SECONDS, MAX_LIMIT_SECONDS)

    fun setLimitSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_LIMIT_SECONDS, seconds.coerceIn(MIN_LIMIT_SECONDS, MAX_LIMIT_SECONDS)).apply()
    }

    /** Limit in effect for the current session (set at last reset). */
    fun getActiveLimitSeconds(): Int = prefs.getInt(KEY_ACTIVE_LIMIT_SECONDS, getLimitSeconds()).coerceIn(MIN_LIMIT_SECONDS, MAX_LIMIT_SECONDS)

    /** Counter since last reset (seconds). */
    fun getCounterSeconds(): Long = prefs.getLong(KEY_COUNTER_SECONDS, 0L).coerceAtLeast(0L)

    fun setCounterSeconds(seconds: Long) {
        prefs.edit().putLong(KEY_COUNTER_SECONDS, seconds.coerceAtLeast(0L)).apply()
    }

    /** Timestamp of last reset (millis). Used for midnight detection. */
    fun getLastResetMillis(): Long = prefs.getLong(KEY_LAST_RESET_MILLIS, 0L)

    fun setLastResetMillis(millis: Long) {
        prefs.edit().putLong(KEY_LAST_RESET_MILLIS, millis).apply()
    }

    /** Adds [deltaSeconds] to counter. Caller should check midnight first via [checkAndApplyMidnightReset]. */
    fun addCounterSeconds(deltaSeconds: Long) {
        setCounterSeconds(getCounterSeconds() + deltaSeconds)
    }

    /** If local midnight has passed since last reset, resets counter and sets active limit = current limit. Returns true if reset was performed. */
    fun checkAndApplyMidnightReset(): Boolean {
        val last = getLastResetMillis()
        if (last <= 0L) return false
        val calLast = Calendar.getInstance().apply { timeInMillis = last }
        val calNow = Calendar.getInstance()
        if (calNow.get(Calendar.YEAR) != calLast.get(Calendar.YEAR) ||
            calNow.get(Calendar.DAY_OF_YEAR) != calLast.get(Calendar.DAY_OF_YEAR)
        ) {
            applyReset()
            return true
        }
        return false
    }

    /** Resets counter to 0, sets active limit = current limit, updates last reset time. */
    fun applyReset() {
        val limit = getLimitSeconds()
        prefs.edit()
            .putLong(KEY_COUNTER_SECONDS, 0L)
            .putInt(KEY_ACTIVE_LIMIT_SECONDS, limit)
            .putLong(KEY_LAST_RESET_MILLIS, System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "listening_time"
        private const val KEY_LIMIT_SECONDS = "limit_seconds"
        private const val KEY_ACTIVE_LIMIT_SECONDS = "active_limit_seconds"
        private const val KEY_COUNTER_SECONDS = "counter_seconds"
        private const val KEY_LAST_RESET_MILLIS = "last_reset_millis"
        const val MIN_LIMIT_SECONDS = 5 * 60
        const val MAX_LIMIT_SECONDS = 24 * 3600
        private const val DEFAULT_LIMIT_SECONDS = 60 * 60
    }
}
