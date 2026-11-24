package com.example.core

import android.content.Context

/**
 * Stores sensitive server credentials (RCON passwords and Panel API tokens) strictly in
 * app-private SharedPreferences on the device. Never synced to Firestore.
 */
class ServerSecretsStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "mindgpt_server_secrets",
        Context.MODE_PRIVATE
    )

    fun saveSecret(serverId: String, secret: String) {
        if (serverId.isBlank()) return
        prefs.edit().putString("secret_$serverId", secret).apply()
    }

    fun getSecret(serverId: String): String {
        if (serverId.isBlank()) return ""
        return prefs.getString("secret_$serverId", "").orEmpty()
    }

    fun deleteSecret(serverId: String) {
        if (serverId.isBlank()) return
        prefs.edit().remove("secret_$serverId").apply()
    }
}
