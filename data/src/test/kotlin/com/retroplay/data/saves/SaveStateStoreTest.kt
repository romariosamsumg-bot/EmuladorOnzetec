package com.retroplay.data.saves

import com.retroplay.domain.saves.FpsCounter
import com.retroplay.domain.saves.RewindBuffer
import com.retroplay.domain.system.SystemRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveStateStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val snes = SystemRegistry.Ids.SNES

    @Test
    fun `grava le lista e acha o mais recente`() {
        val store = SaveStateStore(tmp.root)
        store.write(snes, "Super Mario World (USA).sfc", 0, byteArrayOf(1, 2), byteArrayOf(9))
        File(store.dir(snes, "Super Mario World (USA).sfc"), "slot_0.state").setLastModified(1_000_000)
        store.write(snes, "Super Mario World (USA).sfc", 3, byteArrayOf(3))
        val slots = store.list(snes, "Super Mario World (USA).sfc")
        assertEquals(listOf(0, 3), slots.map { it.slot })
        assertTrue(slots.first().thumbnailPath!!.endsWith("slot_0.png"))
        assertNull(slots[1].thumbnailPath)
        assertEquals(3, store.latest(snes, "Super Mario World (USA).sfc")!!.slot)
        assertTrue(store.read(snes, "Super Mario World (USA).sfc", 3)!!.contentEquals(byteArrayOf(3)))
        // Mesma chave mesmo se o arquivo for .zip (o jogo é o mesmo).
        assertEquals(2, store.list(snes, "Super Mario World (USA).zip").size)
    }

    @Test
    fun `miniatura posterior e exclusao`() {
        val store = SaveStateStore(tmp.root)
        store.write(snes, "a.sfc", 5, byteArrayOf(1))
        store.writeThumbnail(snes, "a.sfc", 5, byteArrayOf(7))
        assertTrue(store.list(snes, "a.sfc").single().thumbnailPath != null)
        store.writeThumbnail(snes, "a.sfc", 6, byteArrayOf(7)) // sem state: ignorada
        assertEquals(1, store.list(snes, "a.sfc").size)
        store.delete(snes, "a.sfc", 5)
        assertTrue(store.list(snes, "a.sfc").isEmpty())
        assertNull(store.read(snes, "a.sfc", 5))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `slot invalido`() {
        SaveStateStore(tmp.root).write(snes, "a.sfc", 10, byteArrayOf(1))
    }

    @Test
    fun `nome com caracteres proibidos`() {
        assertEquals("a_b", SaveStateStore.key("a:b.sfc"))
        assertEquals("jogo", SaveStateStore.key(".sfc"))
    }

    @Test
    fun `rewind respeita o limite de memoria e volta em ordem`() {
        val rb = RewindBuffer(maxBytes = 10)
        (1..6).forEach { rb.push(ByteArray(3) { _ -> it.toByte() }) }
        assertEquals(3, rb.size) // 9 bytes cabem; os mais antigos foram descartados
        assertEquals(6, rb.pop()!![0].toInt())
        assertEquals(4, rb.rewind(2)!![0].toInt())
        assertNull(rb.pop())
        assertEquals(0L, rb.usedBytes)
        rb.push(ByteArray(50)) // maior que o buffer: ignorado
        assertEquals(0, rb.size)
    }

    @Test
    fun `contador de fps`() {
        val fps = FpsCounter()
        var last = 0
        for (i in 0 until 120) last = fps.onFrame(i * 16_666_667L)
        assertTrue("fps=$last", last in 60..61)
    }
}
