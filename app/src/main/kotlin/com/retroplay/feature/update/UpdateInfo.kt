package com.retroplay.feature.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URI

/** Versão publicada no site (version.json). */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val notes: List<String>,
    /** Endereço absoluto do APK deste app (TV ou celular). */
    val apkUrl: String,
) {
    companion object {
        /**
         * Lê o version.json:
         * { "versionCode": 250, "versionName": "1.15.0", "notes": ["..."],
         *   "apk": { "tv": "/downloads/OnzetecGames.apk", "mobile": "/downloads/OnzetecGames-Celular.apk" } }
         * Endereços relativos são resolvidos a partir de [baseUrl] (o endereço do próprio JSON).
         */
        fun parse(json: String, flavor: String, baseUrl: String): UpdateInfo? = runCatching {
            val o: JsonObject = Json.parseToJsonElement(json).jsonObject
            val apk = o["apk"]!!.jsonObject[flavor]!!.jsonPrimitive.content
            UpdateInfo(
                versionCode = o["versionCode"]!!.jsonPrimitive.int,
                versionName = o["versionName"]!!.jsonPrimitive.content,
                notes = o["notes"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty(),
                apkUrl = URI(baseUrl).resolve(apk).toString(),
            )
        }.getOrNull()?.takeIf { it.apkUrl.startsWith("https://") }
    }
}

/** Situação da atualização (mostrada na Início e em Configurações → Sobre). */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val fraction: Float?) : UpdateState
    /** Falta liberar "Instalar apps desconhecidos" para o Onzetec Games. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val info: UpdateInfo?) : UpdateState
}
