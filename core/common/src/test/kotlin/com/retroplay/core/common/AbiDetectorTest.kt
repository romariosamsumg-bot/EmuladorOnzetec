package com.retroplay.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbiDetectorTest {

    @Test
    fun `processo 64 bit em cpu arm64 usa arm64-v8a`() {
        assertEquals(
            CpuAbi.ARM64_V8A,
            AbiDetector.detect(true, listOf("arm64-v8a"), listOf("armeabi-v7a", "armeabi")),
        )
    }

    @Test
    fun `tv com userland 32 bit usa armeabi-v7a mesmo com cpu 64 bit`() {
        // TCL tipica: SUPPORTED_64_BIT_ABIS vazio, processo 32-bit.
        assertEquals(CpuAbi.ARMEABI_V7A, AbiDetector.detect(false, emptyList(), listOf("armeabi-v7a", "armeabi")))
    }

    @Test
    fun `app 32 bit em sistema 64 bit escolhe abi 32 bit`() {
        assertEquals(
            CpuAbi.ARMEABI_V7A,
            AbiDetector.detect(false, listOf("arm64-v8a"), listOf("armeabi-v7a", "armeabi")),
        )
    }

    @Test
    fun `emulador x86_64 e reconhecido`() {
        assertEquals(CpuAbi.X86_64, AbiDetector.detect(true, listOf("x86_64", "arm64-v8a"), listOf("x86")))
    }

    @Test
    fun `abi desconhecido retorna null`() {
        assertNull(AbiDetector.detect(false, emptyList(), listOf("armeabi", "mips")))
    }
}
