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
    /** Arcade: núcleo a que o romset pertence (o DAT de onde veio: "mame2000", "fbneo"). */
    val core: String? = null,
    /** Arcade: BIOS exigida pelo jogo (ex.: "neogeo"), ou null. */
    val bios: String? = null,
)

data class DatRom(val name: String, val size: Long, val crc: Long, val merge: Boolean, val md5: ByteArray? = null)

/** Consulta ao banco de identificação (DATs No-Intro / FBNeo). */
interface DatLookup {
    /** Jogos cujo arquivo tem este CRC32, restritos aos sistemas indicados. */
    fun byCrc(crc: Long, systems: Collection<SystemId>): List<Pair<DatGame, DatRom>>

    /** Romset de arcade pelo nome do .zip (ex.: "kof98"). */
    fun romset(system: SystemId, setName: String): Pair<DatGame, List<DatRom>>?

    /** Todas as versões do romset (uma por núcleo/DAT), na ordem de preferência. */
    fun romsets(system: SystemId, setName: String): List<Pair<DatGame, List<DatRom>>> = listOfNotNull(romset(system, setName))

    /** Discos (PlayStation): jogos com este código de disco (ex.: "SLUS-00594"). */
    fun bySerial(system: SystemId, serial: String): List<DatGame> = emptyList()

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
    /** Arcade: núcleo cujo DAT o romset satisfaz melhor (o app abre o jogo nele). */
    val coreHint: String? = null,
)

/** Resultado especial: o arquivo é uma BIOS (ex.: neogeo.zip), não um jogo. */
data class BiosFound(
    val system: SystemId,
    val setName: String,
    val complete: Boolean,
    val core: String? = null,
    /** Núcleo → este arquivo tem tudo que o DAT desse núcleo pede (MAME 0.37 e FBNeo têm versões diferentes). */
    val byCore: Map<String, Boolean> = emptyMap(),
)
