package com.retroplay.domain.library

import com.retroplay.domain.system.GameSystem
import com.retroplay.domain.system.IdentificationStrategy
import com.retroplay.domain.system.SystemCatalog
import com.retroplay.domain.system.SystemDetector
import com.retroplay.domain.system.SystemId

/** Um arquivo encontrado na varredura. A leitura (arquivo comum ou SAF) fica na camada de dados. */
interface ScanTarget {
    val fileName: String
    val size: Long

    /** Nomes das pastas entre a pasta monitorada e o arquivo (dicas de console). */
    val parentNames: List<String>

    /** Nomes (minúsculos, sem extensão) dos outros arquivos da mesma pasta — para achar romsets "pai". */
    val siblingNames: Set<String>

    /** Índice do .zip/.7z (nomes, tamanhos e CRCs), sem descompactar. */
    fun archiveEntries(): List<ArchiveEntry>

    fun crc32(skipBytes: Long): Long
    fun md5(skipBytes: Long): ByteArray

    /**
     * CRC de uma entrada cujo índice não traz o CRC (alguns .7z): descompacta só essa entrada.
     * Só é chamado para ROMs de console doméstico (poucos MB), nunca para romsets de arcade.
     */
    fun archiveEntryCrc(entryName: String): Long? = null
}

sealed interface IdentifyOutcome {
    data class GameResult(val identification: Identification) : IdentifyOutcome
    data class Bios(val bios: BiosFound) : IdentifyOutcome
    data object NotAGame : IdentifyOutcome
}

/**
 * Identifica o console e o jogo de um arquivo:
 * 1. extensão → consoles possíveis;
 * 2. CRC32 (do arquivo ou do índice do .zip/.7z) → DAT No-Intro; MD5 só para desempatar;
 * 3. arcade: nome do romset + CRCs internos (DATs do MAME 0.37 e do FBNeo), sem descompactar;
 * 4. dicas pelo nome da pasta e, por fim, pergunta ao usuário (uma vez).
 */
class RomIdentifier(private val catalog: SystemCatalog, private val dat: DatLookup) {

    private val archiveExt = setOf("zip", "7z")

    fun identify(target: ScanTarget, userSystem: SystemId? = null): IdentifyOutcome {
        val ext = SystemDetector.extensionOf(target.fileName) ?: return IdentifyOutcome.NotAGame
        val byExt = catalog.all.filter { ext in it.extensions }
        if (byExt.isEmpty()) return IdentifyOutcome.NotAGame
        val hint = folderHint(target.parentNames)
        val forced = userSystem?.let { id -> catalog.byId(id) }

        return if (ext in archiveExt) identifyArchive(target, byExt, hint, forced)
        else identifyPlain(target, ext, if (forced != null) listOf(forced) else byExt, hint)
    }

    // ------------------------------------------------------------ arquivo comum

    private fun identifyPlain(target: ScanTarget, ext: String, candidates: List<GameSystem>, hint: GameSystem?): IdentifyOutcome {
        val skip = copierHeader(ext, target.size)
        val crc = target.crc32(skip)
        val match = pick(dat.byCrc(crc, candidates.map { it.id }), hint) { target.md5(skip) }
        return result(match, candidates, hint, crc, target.fileName)
    }

    // ------------------------------------------------------------ .zip / .7z

    private fun identifyArchive(target: ScanTarget, byExt: List<GameSystem>, hint: GameSystem?, forced: GameSystem?): IdentifyOutcome {
        val entries = target.archiveEntries()
        val allowed = if (forced != null) listOf(forced) else byExt
        val arcade = allowed.filter { it.identification == IdentificationStrategy.ARCADE_ROMSET }

        // 1) Romset de arcade conhecido cujo conteúdo bate com o DAT (vem antes: romsets e a
        //    BIOS do Neo Geo contêm arquivos .bin, que também é extensão do Mega Drive).
        //    O .zip é lido pelo núcleo como está; nunca descompactamos.
        val setName = target.fileName.substringBeforeLast('.').lowercase()
        for (system in arcade) {
            // O mesmo nome pode existir em vários DATs (MAME 0.37, FBNeo) com conteúdo diferente:
            // confere contra todos e fica com o que o .zip satisfaz melhor.
            val versions = dat.romsets(system.id, setName).filter { looksLikeRomset(it.second, entries) }
            if (versions.isEmpty()) continue
            val biosVersions = versions.filter { it.first.isBios }
            if (biosVersions.isNotEmpty()) {
                val best = biosVersions
                    .map { (game, roms) -> game to validate(roms, entries, needParent = false, siblings = emptySet(), biosCrcs = emptySet()).second }
                    .minWith(compareBy({ it.second }, { coreOrder(system, it.first.core) }))
                return IdentifyOutcome.Bios(BiosFound(system.id, setName, complete = best.second == 0, core = best.first.core))
            }
            val results = versions.map { (game, roms) ->
                val biosCrcs = game.bios?.let { b -> dat.romsets(system.id, b).firstOrNull { it.first.core == game.core }?.second }
                    ?.map { it.crc }?.toSet().orEmpty()
                val (status, missing) = validate(roms, entries, game.parent != null, target.siblingNames, biosCrcs, game.parent)
                Triple(game, status, missing)
            }
            val best = results.minWith(compareBy({ statusRank(it.second) }, { it.third }, { coreOrder(system, it.first.core) }))
            return IdentifyOutcome.GameResult(
                Identification(system.id, best.first, IdentStatus.IDENTIFIED, best.second, best.third, coreHint = best.first.core),
            )
        }

        // 2) ROM de console doméstico dentro do compactado (pega a maior, ignorando leia-me etc.).
        val homeSystems = allowed.filter { it.identification == IdentificationStrategy.CONTENT_HASH }
        val romEntry = entries
            .filter { e -> homeSystems.any { s -> entryExt(e) in s.extensions - archiveExt } }
            .maxByOrNull { it.size }
        if (romEntry != null && (hint == null || hint in homeSystems)) {
            val candidates = homeSystems.filter { entryExt(romEntry) in it.extensions }
            val crc = (romEntry.crc ?: target.archiveEntryCrc(romEntry.name))
                ?.takeIf { copierHeader(entryExt(romEntry), romEntry.size) == 0L }
            val match = crc?.let { pick(dat.byCrc(it, candidates.map { s -> s.id }), hint, md5 = null) }
            return result(match, candidates, hint, crc, romEntry.name)
        }

        // 3) Numa pasta de arcade (ex.: "Arcade", "MAME"), um .zip desconhecido é tratado como romset
        //    não reconhecido (o usuário vê o aviso em vez de o arquivo sumir).
        val arcadeHint = arcade.firstOrNull { it == hint || it == forced }
        if (arcadeHint != null) {
            val known = dat.romsets(arcadeHint.id, setName).firstOrNull { !it.first.isBios }
            if (known != null) {
                val (status, missing) = validate(known.second, entries, false, target.siblingNames, emptySet())
                return IdentifyOutcome.GameResult(
                    Identification(arcadeHint.id, known.first, IdentStatus.IDENTIFIED, status, missing, coreHint = known.first.core),
                )
            }
            return IdentifyOutcome.GameResult(Identification(arcadeHint.id, null, IdentStatus.UNIDENTIFIED, RomsetStatus.UNKNOWN_SET))
        }
        // Compactado sem nada reconhecível (ex.: documentos): não é jogo.
        return IdentifyOutcome.NotAGame
    }

    /** Ao menos um arquivo do .zip tem CRC ou nome de um arquivo do romset. */
    private fun looksLikeRomset(roms: List<DatRom>, entries: List<ArchiveEntry>): Boolean {
        val crcs = roms.map { it.crc }.toSet()
        val names = roms.map { it.name.lowercase() }.toSet()
        return entries.any { it.crc in crcs || it.name.substringAfterLast('/').lowercase() in names }
    }

    /**
     * Confere o romset contra o DAT. Arquivos marcados como "merge" podem vir do romset pai
     * ou da BIOS; os demais têm de estar dentro do .zip.
     */
    private fun validate(
        roms: List<DatRom>,
        entries: List<ArchiveEntry>,
        needParent: Boolean,
        siblings: Set<String>,
        biosCrcs: Set<Long>,
        parent: String? = null,
    ): Pair<RomsetStatus, Int> {
        val crcs = entries.mapNotNull { it.crc }.toSet()
        val names = entries.map { it.name.substringAfterLast('/').lowercase() }.toSet()
        // .7z sem CRC no índice: confere por nome + tamanho (sem descompactar o romset).
        val noCrc = entries.filter { it.crc == null }.map { it.name.substringAfterLast('/').lowercase() to it.size }.toSet()
        val required = roms.filter { !it.merge }
        val missing = required.filter { it.crc !in crcs && (it.name.lowercase() to it.size) !in noCrc }
        if (missing.isNotEmpty()) {
            val wrongVersion = missing.any { it.name.lowercase() in names }
            return (if (wrongVersion) RomsetStatus.WRONG_VERSION else RomsetStatus.INCOMPLETE) to missing.size
        }
        if (needParent && parent != null) {
            val fromParent = roms.filter { it.merge && it.crc !in biosCrcs && it.crc !in crcs }
            if (fromParent.isNotEmpty() && parent.lowercase() !in siblings) return RomsetStatus.NEEDS_PARENT to fromParent.size
        }
        return RomsetStatus.COMPLETE to 0
    }

    // ------------------------------------------------------------ auxiliares

    /** Completo primeiro; "só falta o pai" logo depois; versões erradas por último. */
    private fun statusRank(s: RomsetStatus) = when (s) {
        RomsetStatus.COMPLETE -> 0
        RomsetStatus.NEEDS_PARENT -> 1
        RomsetStatus.INCOMPLETE -> 2
        RomsetStatus.WRONG_VERSION -> 3
        else -> 4
    }

    /** Ordem dos núcleos do console (o primeiro é o padrão). */
    private fun coreOrder(system: GameSystem, core: String?) = system.cores.indexOfFirst { it.id == core }.let { if (it < 0) Int.MAX_VALUE else it }

    /** Escolhe entre vários casamentos: dica de pasta, depois MD5 (só se ainda houver empate). */
    private fun pick(
        matches: List<Pair<DatGame, DatRom>>,
        hint: GameSystem?,
        md5: (() -> ByteArray)?,
    ): DatGame? {
        if (matches.size <= 1) return matches.firstOrNull()?.first
        val bySystem = matches.groupBy { it.first.system }
        val narrowed = if (bySystem.size > 1 && hint != null && hint.id in bySystem) bySystem.getValue(hint.id) else matches
        if (narrowed.size == 1 || md5 == null || narrowed.none { it.second.md5 != null }) return narrowed.first().first
        val digest = md5()
        return (narrowed.firstOrNull { it.second.md5?.contentEquals(digest) == true } ?: narrowed.first()).first
    }

    private fun result(match: DatGame?, candidates: List<GameSystem>, hint: GameSystem?, crc: Long?, name: String): IdentifyOutcome {
        if (match != null) return IdentifyOutcome.GameResult(Identification(match.system, match, IdentStatus.IDENTIFIED, crc = crc))
        val system = when {
            candidates.size == 1 -> candidates.single()
            hint != null && hint in candidates -> hint
            else -> null
        }
        if (system == null) {
            return IdentifyOutcome.GameResult(
                Identification(null, null, IdentStatus.NEEDS_SYSTEM, candidates = candidates.map { it.id }, crc = crc),
            )
        }
        // Não está no DAT pelo CRC: tenta pelo nome (ex.: ROM com cabeçalho ou dump alternativo).
        val byTitle = dat.byTitle(system.id, normalizeTitle(name))
        return IdentifyOutcome.GameResult(
            Identification(system.id, byTitle, if (byTitle != null) IdentStatus.IDENTIFIED else IdentStatus.UNIDENTIFIED, crc = crc),
        )
    }

    private fun folderHint(parents: List<String>): GameSystem? {
        for (name in parents.asReversed()) {
            val key = normalizeFolder(name)
            catalog.all.firstOrNull { key in it.folderAliases }?.let { return it }
        }
        return null
    }

    private fun entryExt(e: ArchiveEntry) = SystemDetector.extensionOf(e.name).orEmpty()

    /** ROMs de SNES com cabeçalho de copiadora (512 bytes) não batem com o No-Intro sem pular o cabeçalho. */
    private fun copierHeader(ext: String, size: Long): Long =
        if (ext in setOf("smc", "sfc", "fig", "swc") && size % 1024 == 512L) 512 else 0

    companion object {
        const val BIOS_SET = "neogeo"

        fun normalizeFolder(name: String) = name.lowercase().filter { it.isLetterOrDigit() }

        /** "Super Mario World (USA) [!].sfc" → "supermarioworld"; "Zelda, The" → "thezelda". */
        fun normalizeTitle(fileName: String): String {
            var base = fileName.substringAfterLast('/').substringBeforeLast('.')
            base = base.replace(Regex("\\s*[\\(\\[].*$"), "").trim()
            Regex("^(.*), (The|A|An)\\b(.*)$", RegexOption.IGNORE_CASE).find(base)?.let { m ->
                base = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
            }
            return base.lowercase().filter { it.isLetterOrDigit() }
        }
    }
}
