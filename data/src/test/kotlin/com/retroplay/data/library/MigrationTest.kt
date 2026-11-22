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
}
