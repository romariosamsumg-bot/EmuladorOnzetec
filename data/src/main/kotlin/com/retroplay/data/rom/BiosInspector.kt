package com.retroplay.data.rom

import java.io.InputStream
import java.util.zip.ZipInputStream

/** Inspeciona a neogeo.zip sem extrair nada. */
object BiosInspector {
    /** true se a BIOS traz alguma UniBIOS (arquivos "uni-bios_*.rom"), usada no modo UniBIOS do FBNeo. */
    fun hasUniBios(input: InputStream): Boolean = ZipInputStream(input).use { zip ->
        generateSequence { zip.nextEntry }.any { it.name.substringAfterLast('/').lowercase().startsWith("uni-bios") }
    }
}
