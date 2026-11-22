package com.retroplay.data.dat

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.retroplay.domain.library.DatGame
import com.retroplay.domain.library.DatLookup
import com.retroplay.domain.library.DatRom
import com.retroplay.domain.library.RomIdentifier
import com.retroplay.domain.system.SystemId
import java.io.File

/**
 * Banco de identificação pré-gerado (tools/dat/build_database.py), embutido em assets.
 * É copiado uma vez para o armazenamento interno e aberto só para leitura.
 */
class DatDatabase(private val context: Context) : DatLookup {

    private val db: SQLiteDatabase by lazy { open() }
    private val titleIndex = HashMap<String, Map<String, DatGame>>()

    private fun open(): SQLiteDatabase {
        val target = File(context.noBackupFilesDir, "db/$ASSET_NAME")
        val stamp = File(target.parentFile, "$ASSET_NAME.version")
        val appVersion = context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime.toString()
        if (!target.isFile || stamp.takeIf { it.isFile }?.readText() != appVersion) {
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "$ASSET_NAME.tmp")
            context.assets.open("db/$ASSET_NAME").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            target.delete()
            tmp.renameTo(target)
            stamp.writeText(appVersion)
        }
        return SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
    }

    override fun byCrc(crc: Long, systems: Collection<SystemId>): List<Pair<DatGame, DatRom>> {
        if (systems.isEmpty()) return emptyList()
        val placeholders = systems.joinToString(",") { "?" }
        val args = arrayOf(crc.toString()) + systems.map { it.value }
        return db.rawQuery(
            "SELECT $GAME_COLUMNS, r.name, r.size, r.crc, r.merge, r.md5 FROM rom r JOIN game g ON g.id = r.game_id " +
                "WHERE r.crc = ? AND g.system IN ($placeholders) AND g.romset IS NULL",
            args,
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(readGame(c) to DatRom(c.getString(15), c.getLong(16), c.getLong(17), c.getInt(18) == 1, c.getBlobOrNull(19)))
            }
        }
    }

    override fun romset(system: SystemId, setName: String): Pair<DatGame, List<DatRom>>? {
        val game = db.rawQuery("SELECT $GAME_COLUMNS FROM game g WHERE g.system = ? AND g.romset = ?", arrayOf(system.value, setName))
            .use { c -> if (c.moveToFirst()) readGame(c) else null } ?: return null
        val roms = db.rawQuery("SELECT name, size, crc, merge FROM rom WHERE game_id = ?", arrayOf(game.id.toString())).use { c ->
            buildList { while (c.moveToNext()) add(DatRom(c.getString(0), c.getLong(1), c.getLong(2), c.getInt(3) == 1)) }
        }
        return game to roms
    }

    @Synchronized
    override fun byTitle(system: SystemId, normalizedTitle: String): DatGame? {
        val index = titleIndex.getOrPut(system.value) {
            // Índice em memória por console (poucos milhares de títulos), preferindo EUA/Mundo/Europa.
            val all = db.rawQuery("SELECT $GAME_COLUMNS FROM game g WHERE g.system = ? AND g.is_bios = 0", arrayOf(system.value))
                .use { c -> buildList { while (c.moveToNext()) add(readGame(c)) } }
            all.sortedBy { regionRank(it.region) }
                .groupBy { RomIdentifier.normalizeTitle(it.name + ".x") }
                .mapValues { it.value.first() }
        }
        return index[normalizedTitle]
    }

    private fun regionRank(region: String?) = when {
        region == null -> 5
        "USA" in region -> 0
        "World" in region -> 1
        "Europe" in region -> 2
        "Brazil" in region -> 3
        else -> 4
    }

    private fun readGame(c: android.database.Cursor) = DatGame(
        id = c.getLong(0),
        system = SystemId(c.getString(1)),
        name = c.getString(2),
        title = c.getString(3),
        region = c.getStringOrNull(4),
        year = c.getStringOrNull(5),
        developer = c.getStringOrNull(6),
        genre = c.getStringOrNull(8),
        players = if (c.isNull(9)) null else c.getInt(9),
        thumbs = c.getInt(10),
        thumbName = c.getStringOrNull(11),
        romset = c.getStringOrNull(12),
        parent = c.getStringOrNull(13),
        isBios = c.getInt(14) == 1,
    )

    private fun android.database.Cursor.getStringOrNull(i: Int) = if (isNull(i)) null else getString(i)
    private fun android.database.Cursor.getBlobOrNull(i: Int) = if (isNull(i)) null else getBlob(i)

    companion object {
        const val ASSET_NAME = "retroplay-dat.sqlite"
        private const val GAME_COLUMNS =
            "g.id, g.system, g.name, g.title, g.region, g.year, g.developer, g.publisher, g.genre, g.players, " +
                "g.thumbs, g.thumb_name, g.romset, g.parent, g.is_bios"
    }
}
