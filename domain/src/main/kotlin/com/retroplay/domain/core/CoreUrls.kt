package com.retroplay.domain.core

/**
 * URLs do buildbot oficial do libretro.
 *
 * Observação: o buildbot só publica núcleos Android avulsos em `nightly/android/latest`
 * (o diretório `stable/` contém apenas APKs do RetroArch). Por isso o "canal" é uma
 * política de atualização (ver [CoreChannel]), não uma URL diferente.
 */
object CoreUrls {
    const val BUILDBOT = "https://buildbot.libretro.com"

    val SUPPORTED_ABIS = setOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")

    fun download(coreId: String, abi: String): String {
        require(coreId.matches(Regex("[a-z0-9_]+"))) { "id de núcleo inválido: $coreId" }
        require(abi in SUPPORTED_ABIS) { "ABI sem núcleos no buildbot: $abi" }
        return "$BUILDBOT/nightly/android/latest/$abi/${coreId}_libretro_android.so.zip"
    }
}
