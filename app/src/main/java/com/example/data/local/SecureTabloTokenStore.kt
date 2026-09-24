package com.example.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.model.TabloDevice

/**
 * Stores the short-lived Tablo account and Lighthouse tokens outside Room.
 * The database remains useful for reconnecting to the selected local device,
 * but a database export no longer exposes cloud credentials.
 */
class SecureTabloTokenStore(context: Context) {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        PREFERENCES_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun save(device: TabloDevice) {
        preferences.edit()
            .putString(accountKey(device.serverId), device.accountToken)
            .putString(lighthouseKey(device.serverId), device.lighthouseToken)
            .apply()
    }

    fun restore(device: TabloDevice): TabloDevice = device.copy(
        accountToken = preferences.getString(accountKey(device.serverId), null),
        lighthouseToken = preferences.getString(lighthouseKey(device.serverId), null)
    )

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun accountKey(serverId: String) = "account.$serverId"
    private fun lighthouseKey(serverId: String) = "lighthouse.$serverId"

    private companion object {
        const val PREFERENCES_NAME = "tablo_secure_tokens"
    }
}
