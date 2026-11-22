package com.retroplay.data.core

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.RandomAccessFile

/**
 * Teste de integração com o buildbot REAL (desligado por padrão para não depender da rede no CI).
 * Rodar: ./gradlew :data:testDebugUnitTest -Pretroplay.network=true --tests '*BuildbotLiveTest*'
 */
class BuildbotLiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `baixa snes9x2010 real para armeabi-v7a e arm64-v8a`() = runTest(timeout = kotlin.time.Duration.parse("120s")) {
        assumeTrue(System.getProperty("retroplay.network") == "true")
        val storage = CoreStorage(tmp.newFolder("cores"))
        val installer = CoreInstaller(OkHttpClient(), storage)
        for ((abi, machine) in listOf("armeabi-v7a" to 40, "arm64-v8a" to 183)) {
            val core = installer.install("snes9x2010", abi)
            println("$abi: versão=${core.version} tamanho=${core.sizeBytes} sha256=${core.sha256}")
            assertTrue(core.sizeBytes > 500_000)
            // e_machine do cabeçalho ELF: 40 = ARM, 183 = AArch64.
            RandomAccessFile(core.libraryPath, "r").use { f ->
                f.seek(18)
                val m = f.read() or (f.read() shl 8)
                assertEquals("arquitetura do .so para $abi", machine, m)
            }
            assertEquals(core, storage.read("snes9x2010", abi))
        }
    }
}
