package com.retroplay.data.library

import androidx.room.testing.MigrationTestHelper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Quem já tem o app instalado não pode perder a biblioteca ao atualizar. */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), LibraryDatabase::class.java)

    @Test
    fun `v1 para v2 mantem os jogos e adiciona nome personalizado`() {
        helper.createDatabase("migracao.db", 1).use { db ->
            db.execSQL("INSERT INTO folder(id, uri, source, display_name, is_default, added_at) VALUES (1, '/Jogos', 'FILE', 'Jogos', 1, 0)")
            db.execSQL(
                "INSERT INTO game(uri, source, folder_id, file_name, size_bytes, last_modified, system, title, dat_name, region, year, developer, genre, players, thumbs, thumb_name, crc, status, romset_status, missing_files, corrupted, is_favorite, play_count, added_at, play_time_ms) " +
                    "VALUES ('/Jogos/a.sfc', 'FILE', 1, 'a.sfc', 10, 1, 'snes', 'Mario', null, null, null, null, null, null, 0, null, null, 'IDENTIFIED', 'NOT_APPLICABLE', 0, 0, 1, 3, 0, 0)",
            )
        }
        helper.runMigrationsAndValidate("migracao.db", 2, true, MIGRATION_1_2).use { db ->
            db.query("SELECT title, is_favorite, play_count, custom_title FROM game").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("Mario", c.getString(0))
                assertEquals(1, c.getInt(1))
                assertEquals(3, c.getInt(2))
                assertTrue(c.isNull(3))
            }
        }
    }

    @Test
    fun `v2 para v3 leva favoritos e historico para o perfil 1`() {
        helper.createDatabase("migracao3.db", 2).use { db ->
            db.execSQL("INSERT INTO folder(id, uri, source, display_name, is_default, added_at) VALUES (1, '/Jogos', 'FILE', 'Jogos', 1, 0)")
            val cols = "uri, source, folder_id, file_name, size_bytes, last_modified, system, title, thumbs, status, romset_status, missing_files, corrupted, is_favorite, play_count, last_played_at, added_at, play_time_ms, custom_title"
            db.execSQL("INSERT INTO game($cols) VALUES ('/Jogos/Super Mario World (USA).sfc', 'FILE', 1, 'Super Mario World (USA).sfc', 10, 1, 'snes', 'Mario', 0, 'IDENTIFIED', 'NOT_APPLICABLE', 0, 0, 1, 3, 500, 0, 9000, 'Meu Mario')")
            db.execSQL("INSERT INTO game($cols) VALUES ('/Jogos/sonic.md', 'FILE', 1, 'sonic.md', 10, 1, 'megadrive', 'Sonic', 0, 'IDENTIFIED', 'NOT_APPLICABLE', 0, 0, 0, 0, null, 0, 0, null)")
            db.execSQL("INSERT INTO game($cols) VALUES ('/Jogos/x.bin', 'FILE', 1, 'x.bin', 10, 1, null, 'x', 0, 'NEEDS_SYSTEM', 'NOT_APPLICABLE', 0, 1, 0, 0, null, 0, 0, null)")
        }
        helper.runMigrationsAndValidate("migracao3.db", 3, true, MIGRATION_2_3).use { db ->
            db.query("SELECT name FROM profile WHERE id = 1").use { c -> assertTrue(c.moveToFirst()); assertEquals("Jogador 1", c.getString(0)) }
            db.query("SELECT game_key, custom_title FROM game ORDER BY id").use { c ->
                assertTrue(c.moveToFirst()); assertEquals("snes/Super Mario World (USA)", c.getString(0)); assertEquals("Meu Mario", c.getString(1))
                assertTrue(c.moveToNext()); assertEquals("megadrive/sonic", c.getString(0))
                assertTrue(c.moveToNext()); assertTrue(c.isNull(0))
            }
            db.query("SELECT profile_id, game_key, favorite, play_count, last_played_at, play_time_ms FROM play_stats").use { c ->
                assertEquals(1, c.count)
                assertTrue(c.moveToFirst())
                assertEquals(1L, c.getLong(0)); assertEquals("snes/Super Mario World (USA)", c.getString(1))
                assertEquals(1, c.getInt(2)); assertEquals(3, c.getInt(3)); assertEquals(500L, c.getLong(4)); assertEquals(9000L, c.getLong(5))
            }
        }
    }
}
