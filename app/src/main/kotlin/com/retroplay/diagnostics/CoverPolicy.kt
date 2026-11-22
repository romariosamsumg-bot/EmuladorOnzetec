package com.retroplay.diagnostics

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * Regra de download de capas (Rede → Baixar capas / Só no Wi-Fi). Capas já em cache continuam
 * aparecendo: o Coil consulta o disco antes da rede.
 */
class CoverPolicyInterceptor(
    private val downloadEnabled: () -> Boolean,
    private val wifiOnly: () -> Boolean,
    private val isMetered: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        if (!allowed()) throw IOException("download de capas desativado nas configurações")
        return chain.proceed(chain.request())
    }

    fun allowed(): Boolean = downloadEnabled() && !(wifiOnly() && isMetered())
}
