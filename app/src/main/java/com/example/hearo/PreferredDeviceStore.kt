package com.example.hearo

import android.content.Context

/** Persists the user's preferred Spotify device (id + name for display). */
class PreferredDeviceStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getPreferredDeviceId(): String? =
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    fun getPreferredDeviceName(): String? =
        prefs.getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }

    /** Display string: "Name (Type)" or null if no name. Caller uses "No preferred device" when id is null. */
    fun getPreferredDeviceDisplay(): String? {
        val name = getPreferredDeviceName() ?: return null
        val type = prefs.getString(KEY_DEVICE_TYPE, null)?.takeIf { it.isNotBlank() }
        return if (type != null) "$name ($type)" else name
    }

    fun setPreferredDevice(deviceId: String?, deviceName: String?, deviceType: String?) {
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId ?: "")
            .putString(KEY_DEVICE_NAME, deviceName ?: "")
            .putString(KEY_DEVICE_TYPE, deviceType ?: "")
            .apply()
    }

    fun clear() {
        prefs.edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_DEVICE_NAME)
            .remove(KEY_DEVICE_TYPE)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "preferred_device"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_DEVICE_NAME = "device_name"
        private const val KEY_DEVICE_TYPE = "device_type"
    }
}
