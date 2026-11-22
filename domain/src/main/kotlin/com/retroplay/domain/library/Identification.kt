package com.retroplay.domain.library

import com.retroplay.domain.system.SystemId

/** Entrada de um .zip/.7z lida do índice do arquivo (sem descompactar). */
data class ArchiveEntry(val name: String, val size: Long, val crc: Long?)

/** Jogo do banco de DATs. */
data class DatGame(
    val id: Long,
    val system: SystemId,
    val name: String,
    val title: String,
    val region: String?,
    val year: String?,
    val developer: String?,
    val genre: String?,
    val players: Int?,
    val thumbs: Int,
    val thumbName: String?,
    val romset: String? = null,
    val parent: String? = null,
    val isBios: Boolean = false,
)

data class DatRom(val name: String, val size: Long, val crc: Long, val merge: Boolean, val md5: ByteArray? = null)

/** Consulta ao banco de identificação (DATs No-Intro / FBNeo). */
interface DatLookup {
    /** Jogos cujo arquivo tem este CRC32, restritos aos sistemas indicados. */
    fun byCrc(crc: Long, systems: Collection<SystemId>): List<Pair<DatGame, DatRom>>

    /** Romset de arcade pelo nome do .zip (ex.: "kof98"). */
    fun romset(system: SystemId, setName: String): Pair<DatGame, List<DatRom>>?

    /** Último recurso: casa pelo título "limpo" do nome do arquivo. */
    fun byTitle(system: SystemId, normalizedTitle: String): DatGame?
}

/** Resultado da identificação de um arquivo. */
data class Identification(
    val system: SystemId?,
    val dat: DatGame?,
    val status: IdentStatus,
    val romsetStatus: RomsetStatus = RomsetStatus.NOT_APPLICABLE,
    val missingFiles: Int = 0,
    /** Sistemas possíveis quando [status] == NEEDS_SYSTEM. */
    val candidates: List<SystemId> = emptyList(),
    val crc: Long? = null,
)

/** Resultado especial: o arquivo é uma BIOS (ex.: neogeo.zip), não um jogo. */
data class BiosFound(val system: SystemId, val setName: String, val complete: Boolean)
