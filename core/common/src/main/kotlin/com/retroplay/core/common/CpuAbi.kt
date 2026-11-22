package com.retroplay.core.common

import android.os.Build
import android.os.Process

/** ABIs para as quais o buildbot do libretro publica núcleos Android. */
enum class CpuAbi(val abiName: String) {
    ARM64_V8A("arm64-v8a"),
    ARMEABI_V7A("armeabi-v7a"),
    X86_64("x86_64"),
    X86("x86"),
    ;

    companion object {
        fun fromName(name: String): CpuAbi? = entries.firstOrNull { it.abiName == name }
    }
}

/**
 * Detecta o ABI que o processo do app realmente usa.
 *
 * Importante para TVs TCL: muitas rodam userland 32-bit com CPU 64-bit. O núcleo .so
 * precisa ter o mesmo ABI do *processo*, e não apenas do hardware, senão o dlopen falha.
 */
object AbiDetector {

    fun detect(): CpuAbi? = detect(
        is64BitProcess = Process.is64Bit(),
        abis64 = Build.SUPPORTED_64_BIT_ABIS.toList(),
        abis32 = Build.SUPPORTED_32_BIT_ABIS.toList(),
    )

    /** Versão pura (testável): escolhe o primeiro ABI suportado compatível com o processo. */
    fun detect(is64BitProcess: Boolean, abis64: List<String>, abis32: List<String>): CpuAbi? {
        val candidates = if (is64BitProcess) abis64 else abis32
        return candidates.firstNotNullOfOrNull { CpuAbi.fromName(it) }
    }
}
