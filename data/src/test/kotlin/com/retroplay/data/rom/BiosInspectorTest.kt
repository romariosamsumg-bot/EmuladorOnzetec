package com.retroplay.data.rom

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BiosInspectorTest {
    private fun zip(vararg names: String) = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { z -> names.forEach { z.putNextEntry(ZipEntry(it)); z.write(1); z.closeEntry() } }
    }.toByteArray().inputStream()

    @Test
    fun detecta_unibios() {
        assertTrue(BiosInspector.hasUniBios(zip("sp-s2.sp1", "uni-bios_4_0.rom", "sfix.sfix")))
        assertFalse(BiosInspector.hasUniBios(zip("sp-s2.sp1", "sfix.sfix")))
        assertFalse(BiosInspector.hasUniBios("não é zip".byteInputStream()))
    }
}
