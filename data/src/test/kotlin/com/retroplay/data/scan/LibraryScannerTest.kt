package com.retroplay.data.scan

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.retroplay.data.dat.DatDatabase
import com.retroplay.data.library.FolderEntity
import com.retroplay.data.library.LibraryDatabase
import com.retroplay.data.testing.CrcForge
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.IdentStatus
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.library.RomsetStatus
import com.retroplay.domain.system.SystemRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Varredura de ponta a ponta com o banco de DATs real e arquivos falsos com CRC forjado. */
@RunWith(RobolectricTestRunner::class)
class LibraryScannerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: LibraryDatabase
    private lateinit var scanner: LibraryScanner
    private lateinit var root: File
    private val dat = DatDatabase(context)
    private var folderId = 0L

    @Before
    fun setUp() = runTest {
        db = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
        scanner = LibraryScanner(db, FileAccess(context.contentResolver), RomIdentifier(SystemRegistry, dat), SystemRegistry) { 1000L }
        root = tmp.newFolder("Jogos")
        folderId = db.folders().upsert(FolderEntity(uri = root.absolutePath, source = "FILE", displayName = "Jogos", isDefault = true, addedAt = 0))
    }

    @After
    fun tearDown() = db.close()

    private fun file(path: String, bytes: ByteArray) = File(root, path).apply { parentFile!!.mkdirs(); writeBytes(bytes) }

    private fun zip(path: String, entries: Map<String, ByteArray>) = File(root, path).apply {
        parentFile!!.mkdirs()
        ZipOutputStream(outputStream()).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
    }

    /** Cria um romset falso com os CRCs exatos do DAT do FBNeo. */
    private fun romset(setName: String, folder: String, drop: Int = 0): File {
        val (_, roms) = dat.romset(SystemRegistry.Ids.ARCADE, setName)!!
        val files = roms.filter { !it.merge }.drop(drop).associate { it.name to CrcForge.fake(it.name, it.crc) }
        return zip("$folder/$setName.zip", files)
    }

    private suspend fun games() = db.games().observeAll(1).first().map { it.game }
    private suspend fun gamesWithStats() = db.games().observeAll(1).first()

    @Test
    fun `identifica snes mega drive neo geo e ignora o que nao e jogo`() = runTest {
        file("Super Nintendo/mario.sfc", CrcForge.fake("smw", 0xB19ED489L))
        val sonicCrc = dat.byTitle(SystemRegistry.Ids.MEGA_DRIVE, "sonicthehedgehog")!!.let { g ->
            dat.byCrc(0, emptyList()) // força abertura
            g
        }
        // CRC real do Sonic (USA, Europe) a partir do banco:
        val sonicRomCrc = android.database.sqlite.SQLiteDatabase.openDatabase(
            File(context.noBackupFilesDir, "db/retroplay-dat.sqlite").path, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
        ).use { d ->
            d.rawQuery("SELECT r.crc FROM rom r JOIN game g ON g.id=r.game_id WHERE g.name=?", arrayOf(sonicCrc.name)).use { c -> c.moveToFirst(); c.getLong(0) }
        }
        zip("Mega Drive/sonic.zip", mapOf("Sonic.md" to CrcForge.fake("sonic", sonicRomCrc), "leiame.txt" to "x".toByteArray()))
        romset("kof98", "Neo Geo")
        romset("neogeo", "BIOS")
        file("foto.jpg", byteArrayOf(1, 2, 3))
        zip("documentos.zip", mapOf("a.pdf" to byteArrayOf(1)))
        file("meu hack.smc", CrcForge.fake("hack", 0x01020304L))

        val summary = scanner.scan()
        val all = games()
        assertEquals(all.joinToString { "${it.fileName}=${it.system}/${it.status}" }, 4, all.size)
        assertEquals(4, summary.added)

        val mario = all.single { it.fileName == "mario.sfc" }
        assertEquals("snes", mario.system)
        assertEquals("Super Mario World", mario.title)
        assertEquals("1991", mario.year)
        assertEquals(IdentStatus.IDENTIFIED.name, mario.status)

        val sonic = all.single { it.fileName == "sonic.zip" }
        assertEquals("megadrive", sonic.system)
        assertEquals(sonicCrc.title, sonic.title)

        val kof = all.single { it.fileName == "kof98.zip" }
        assertEquals("arcade", kof.system)
        assertEquals("The King of Fighters '98 - The Slugfest", kof.title)
        assertEquals(RomsetStatus.COMPLETE.name, kof.romsetStatus)
        // Romset montado com o DAT do MAME 0.37: o app abre no MAME 2000.
        assertEquals("mame2000", kof.coreHint)

        val hack = all.single { it.fileName == "meu hack.smc" }
        assertEquals("snes", hack.system)
        assertEquals(IdentStatus.UNIDENTIFIED.name, hack.status)

        val bios = db.bios().observe().first().single()
        assertEquals("neogeo", bios.setName)
        assertTrue(bios.complete)

        // Romsets nunca são alterados pela varredura.
        assertTrue(File(root, "Neo Geo/kof98.zip").length() > 0)
    }

    /** Imagem crua (2352 bytes/setor, Mode 2) com ISO 9660 mínima e SYSTEM.CNF apontando para [serial]. */
    private fun discImage(serial: String): ByteArray {
        fun le32(b: ByteArray, o: Int, v: Int) { for (i in 0..3) b[o + i] = (v shr (8 * i)).toByte() }
        fun rec(name: String, lba: Int, size: Int): ByteArray {
            val n = name.toByteArray(); val len = 33 + n.size + (if (n.size % 2 == 0) 1 else 0)
            return ByteArray(len).also { r -> r[0] = len.toByte(); le32(r, 2, lba); le32(r, 10, size); r[32] = n.size.toByte(); n.copyInto(r, 33) }
        }
        val iso = ByteArray(24 * 2048)
        iso[16 * 2048] = 1; "CD001".toByteArray().copyInto(iso, 16 * 2048 + 1)
        rec("\u0000", 20, 2048).copyInto(iso, 16 * 2048 + 156)
        val cnf = "BOOT = cdrom:\\$serial;1\r\n".toByteArray()
        rec("SYSTEM.CNF;1", 21, cnf.size).copyInto(iso, 20 * 2048)
        cnf.copyInto(iso, 21 * 2048)
        val raw = ByteArray(24 * 2352)
        for (sct in 0 until 24) { raw[sct * 2352 + 15] = 2; iso.copyInto(raw, sct * 2352 + 24, sct * 2048, sct * 2048 + 2048) }
        return raw
    }

    @Test
    fun `playstation identifica cue pelo codigo do disco e agrupa o m3u`() = runTest {
        file("PlayStation/Crash.cue", "FILE \"Crash.bin\" BINARY\n  TRACK 01 MODE2/2352\n".toByteArray())
        file("PlayStation/Crash.bin", discImage("SCUS_949.00"))
        file("PlayStation/FF7.m3u", "FF7 (Disc 1).cue\nFF7 (Disc 2).cue\n".toByteArray())
        file("PlayStation/FF7 (Disc 1).cue", "FILE \"FF7 (Disc 1).bin\" BINARY\n".toByteArray())
        file("PlayStation/FF7 (Disc 1).bin", discImage("SCUS_941.63"))
        file("PlayStation/FF7 (Disc 2).cue", "FILE \"FF7 (Disc 2).bin\" BINARY\n".toByteArray())
        file("PlayStation/FF7 (Disc 2).bin", discImage("SCUS_941.64"))
        // BIOS do PlayStation em Jogos/BIOS: não vira jogo de Mega Drive.
        file("BIOS/scph5501.bin", ByteArray(512 * 1024))
        scanner.scan()
        val games = games().associateBy { it.fileName }
        assertEquals(setOf("Crash.cue", "FF7.m3u"), games.keys)
        assertEquals("Crash Bandicoot", games.getValue("Crash.cue").title)
        assertEquals("psx", games.getValue("Crash.cue").system)
        assertEquals("Final Fantasy VII", games.getValue("FF7.m3u").title)
        assertEquals(IdentStatus.IDENTIFIED.name, games.getValue("FF7.m3u").status)
        val bios = db.bios().byFolder(folderId)
        assertEquals(listOf("psx" to "scph5501"), bios.map { it.system to it.setName })
    }

    @Test
    fun `neogeo zip antiga e boa para o mame 2000 mas o fbneo e marcado como sem os arquivos`() = runTest {
        // Só o que o DAT do MAME 0.37 pede (poucos arquivos), como no pacote do MAME 0.37.
        val oldRoms = dat.romsets(SystemRegistry.Ids.ARCADE, "neogeo").first { it.first.core == "mame2000" }.second
        zip("BIOS/neogeo.zip", oldRoms.filter { !it.merge }.associate { it.name to CrcForge.fake(it.name, it.crc) })
        scanner.scan()
        val bios = db.bios().byFolder(folderId).single()
        assertEquals("fbneo=0;mame2000=1", bios.coreStatus)
        // Agora com a do FBNeo: serve para o FBNeo.
        val newRoms = dat.romsets(SystemRegistry.Ids.ARCADE, "neogeo").first { it.first.core == "fbneo" }.second
        zip("BIOS/neogeo.zip", newRoms.filter { !it.merge }.associate { it.name to CrcForge.fake(it.name, it.crc) })
        scanner.scan()
        assertEquals("fbneo=1", db.bios().byFolder(folderId).single().coreStatus.split(";").first { it.startsWith("fbneo") })
    }

    @Test
    fun `rom dentro de 7z e identificada`() = runTest {
        val f = File(root, "Super Nintendo/mario.7z").apply { parentFile!!.mkdirs() }
        val rom = CrcForge.fake("smw7z", 0xB19ED489L)
        org.apache.commons.compress.archivers.sevenz.SevenZOutputFile(f).use { out ->
            val e = out.createArchiveEntry(File(tmp.root, "x").apply { writeBytes(rom) }, "Super Mario World (U).sfc")
            out.putArchiveEntry(e); out.write(rom); out.closeArchiveEntry()
        }
        scanner.scan()
        assertEquals("Super Mario World", games().single().title)
    }

    @Test
    fun `romset incompleto e reportado`() = runTest {
        romset("kof98", "Neo Geo", drop = 2)
        scanner.scan()
        val kof = games().single()
        assertEquals(RomsetStatus.INCOMPLETE.name, kof.romsetStatus)
        assertEquals(2, kof.missingFiles)
    }

    @Test
    fun `varredura incremental adiciona altera e remove`() = runTest {
        val mario = file("Super Nintendo/mario.sfc", CrcForge.fake("smw", 0xB19ED489L))
        scanner.scan()
        val id = games().single().id
        db.stats().setFavorite(1, id, true)
        repeat(3) { db.stats().recordPlayStarted(1, id, 100) }

        // Sem mudanças: nada é reidentificado.
        val again = scanner.scan()
        assertEquals(0, again.added + again.updated + again.removed)

        // Arquivo alterado: reidentificado, mantendo favorito e contagem.
        mario.writeBytes(CrcForge.fake("outro", 0x0BADF00DL))
        mario.setLastModified(mario.lastModified() + 10_000)
        val changed = scanner.scan()
        assertEquals(1, changed.updated)
        val updated = gamesWithStats().single()
        assertEquals(IdentStatus.UNIDENTIFIED.name, updated.game.status)
        assertEquals("snes/mario", updated.game.gameKey)
        assertEquals(true, updated.favorite)
        assertEquals(3, updated.playCount)
        // Perfil 2 não vê o favorito do perfil 1.
        assertEquals(null, db.games().observeAll(2).first().single().favorite)

        // Arquivo apagado: sai da biblioteca.
        mario.delete()
        assertEquals(1, scanner.scan().removed)
        assertTrue(games().isEmpty())
    }

    @Test
    fun `arquivo corrompido pede o console e a escolha e lembrada`() = runTest {
        file("quebrado.zip", ByteArray(200) { 9 })
        scanner.scan()
        val broken = db.games().observeNeedsSystem(1).first().single().game
        assertTrue(broken.corrupted)
        assertNull(broken.system)

        db.games().assignSystem(broken.id, "megadrive", "megadrive/quebrado")
        scanner.scan()
        val after = db.games().byId(broken.id)!!
        assertEquals("megadrive", after.system)
        assertEquals("megadrive", after.userSystem)
        assertFalse(after.status == IdentStatus.NEEDS_SYSTEM.name)
    }

    @Test
    fun `nome personalizado e duplicatas sobrevivem a varredura`() = runTest {
        file("Super Nintendo/mario.sfc", CrcForge.fake("smw", 0xB19ED489L))
        file("outra/Super Mario World (U).smc", CrcForge.fake("smw2", 0xB19ED489L))
        scanner.scan()
        val dups = db.games().observeDuplicates(1).first().map { it.game }
        assertEquals(2, dups.size)
        val id = dups.first().id
        db.games().rename(id, "Meu Mario")
        File(root, "Super Nintendo/mario.sfc").setLastModified(System.currentTimeMillis() + 50_000)
        File(root, "outra/Super Mario World (U).smc").setLastModified(System.currentTimeMillis() + 50_000)
        scanner.scan()
        assertEquals("Meu Mario", db.games().byId(id)!!.customTitle)
    }

    @Test
    fun `pasta inacessivel nao apaga os jogos dela`() = runTest {
        file("Super Nintendo/mario.sfc", CrcForge.fake("smw", 0xB19ED489L))
        scanner.scan()
        db.folders().upsert(db.folders().all().single().copy(uri = File(tmp.root, "pendrive-removido").absolutePath))
        // Pasta sumiu (ex.: pendrive removido): listFiles() devolve null → os jogos ficam.
        val s = scanner.scan()
        assertEquals(0, s.removed)
        assertEquals(1, games().size)
    }

    /** Romset falso exatamente como no DAT de um núcleo (MAME 0.37 ou FBNeo). */
    private fun romsetFor(core: String, setName: String, folder: String): File {
        val (_, roms) = dat.romsets(SystemRegistry.Ids.ARCADE, setName).first { it.first.core == core }
        return zip("$folder/$setName.zip", roms.filter { !it.merge }.associate { it.name to CrcForge.fake(it.name, it.crc) })
    }

    @Test
    fun `cada romset abre no nucleo cujo dat ele satisfaz`() = runTest {
        romsetFor("mame2000", "sf2", "Arcade")
        romsetFor("fbneo", "mslug3", "Arcade") // só existe no FBNeo (posterior ao MAME 0.37)
        romsetFor("fbneo", "dino", "Arcade")   // o conteúdo também satisfaz o MAME 0.37
        scanner.scan()
        val all = db.games().observeAll(1).first().map { it.game }
        assertEquals("mame2000", all.single { it.fileName == "sf2.zip" }.coreHint)
        assertEquals("fbneo", all.single { it.fileName == "mslug3.zip" }.coreHint)
        // Satisfaz os dois DATs: fica com o núcleo padrão (MAME 2000).
        assertEquals("mame2000", all.single { it.fileName == "dino.zip" }.coreHint)
        assertTrue(all.all { it.romsetStatus == RomsetStatus.COMPLETE.name })
    }
}
