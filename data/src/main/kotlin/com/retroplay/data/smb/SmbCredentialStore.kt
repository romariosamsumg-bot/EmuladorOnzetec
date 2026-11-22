package com.retroplay.data.smb

import android.content.Context

/**
 * Usuário e senha de cada compartilhamento, guardados só no armazenamento privado do app
 * (fora do backup e das configurações exportadas).
 */
class SmbCredentialStore(context: Context) {
    private val prefs = context.getSharedPreferences("smb_credentials", Context.MODE_PRIVATE)

    private fun key(loc: SmbLocation) = "${loc.host.lowercase()}:${loc.port}/${loc.share.lowercase()}"

    fun get(loc: SmbLocation): SmbCredentials? {
        val k = key(loc)
        if (!prefs.contains("$k|user")) return null
        return SmbCredentials(prefs.getString("$k|user", "").orEmpty(), prefs.getString("$k|pass", "").orEmpty(), prefs.getString("$k|domain", "").orEmpty())
    }

    fun put(loc: SmbLocation, creds: SmbCredentials) {
        val k = key(loc)
        prefs.edit().putString("$k|user", creds.user).putString("$k|pass", creds.password).putString("$k|domain", creds.domain).apply()
    }
}
