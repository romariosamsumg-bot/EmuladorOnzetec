package com.retroplay.data.smb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SmbLocationTest {
    @Test
    fun endereco_vai_e_volta() {
        val loc = SmbLocation("nas.local", 445, "Meus Jogos", "Super Nintendo/RPG")
        assertEquals("smb://nas.local/Meus%20Jogos/Super%20Nintendo/RPG", loc.uri())
        assertEquals(loc, SmbLocation.parse(loc.uri()))
        assertEquals("Super Nintendo\\RPG", loc.smbPath)
        assertEquals(SmbLocation("192.168.0.10", 4455, "jogos", ""), SmbLocation.parse("smb://192.168.0.10:4455/jogos"))
        assertEquals("smb://192.168.0.10:4455/jogos/a%2Bb.sfc", SmbLocation("192.168.0.10", 4455, "jogos", "").child("a+b.sfc").uri())
        assertEquals("a+b.sfc", SmbLocation.parse("smb://h/s/a%2Bb.sfc")!!.path)
        assertNull(SmbLocation.parse("smb://servidor"))
        assertNull(SmbLocation.parse("/storage/x.sfc"))
    }

    @Test
    fun entrada_do_usuario() {
        assertEquals(SmbLocation("pc-sala", 445, "Jogos", "SNES"), SmbLocation.fromInput(" \\\\pc-sala\\ ", "Jogos", "\\SNES\\"))
        assertEquals(SmbLocation("10.0.0.2", 4455, "j", ""), SmbLocation.fromInput("smb://10.0.0.2:4455", "j", ""))
        assertNull(SmbLocation.fromInput("", "Jogos", ""))
        assertNull(SmbLocation.fromInput("pc", "a/b", ""))
    }
}
