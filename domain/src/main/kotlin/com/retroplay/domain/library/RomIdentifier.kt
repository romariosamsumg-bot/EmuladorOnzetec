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

    /** Lê um trecho do arquivo (discos: só alguns setores do começo, para achar o código do disco). */
    fun read(offset: Long, length: Int): ByteArray = ByteArray(0)

    /** Outro arquivo da mesma pasta (a trilha do .cue, um disco do .m3u), ou null se não existe. */
    fun sibling(name: String): ScanTarget? = null

    /** Nomes dos arquivos da mesma pasta (para achar o .m3u que agrupa os discos). */
    val folderFiles: List<String> get() = emptyList()

    fun readText(maxBytes: Int = 64 * 1024): String = read(0, minOf(size, maxBytes.toLong()).toInt()).toString(Charsets.UTF_8)
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
        playStationBios(target, ext)?.let { return it }
        if (ext == "bin") discTrack(target)?.let { return it }
        val byExt = catalog.all.filter { ext in it.extensions }
        if (byExt.isEmpty()) return IdentifyOutcome.NotAGame
        val hint = folderHint(target.parentNames)
        val forced = userSystem?.let { id -> catalog.byId(id) }
        val disc = (if (forced != null) listOf(forced) else byExt).firstOrNull { it.identification == IdentificationStrategy.DISC_SERIAL }
        if (disc != null && ext !in archiveExt) return identifyDisc(target, disc, ext)

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
                // Serve para cada núcleo? Cada DAT pede arquivos diferentes (a neogeo.zip do MAME 0.37 é
                // bem menor que a do FBNeo).
                val byCore = biosVersions.mapNotNull { (game, roms) ->
                    game.core?.let { c -> c to (validate(roms, entries, needParent = false, siblings = emptySet(), biosCrcs = emptySet()).second == 0) }
                }.toMap()
                return IdentifyOutcome.Bios(BiosFound(system.id, setName, complete = best.second == 0, core = best.first.core, byCore = byCore))
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

        // 2) Disco de CD compactado (.cue + .bin, .chd, .pbp, .iso): identificado pelo nome do arquivo
        //    de dentro, sem descompactar. Vem antes do Mega Drive, que também usa .bin.
        val disc = allowed.firstOrNull { it.identification == IdentificationStrategy.DISC_SERIAL }
        if (disc != null && (hint == null || hint == disc) && forced?.identification != IdentificationStrategy.ARCADE_ROMSET) {
            val main = DiscImages.mainDiscEntry(entries.map { it.name })
            if (main != null) {
                val game = lookupDisc(disc, null, DiscImages.baseName(main))
                val discNo = if (main.lowercase().endsWith(".m3u")) null else DiscImages.discNumber(main)
                val multi = entries.count { e -> DiscImages.isDiscFile(e.name) && !e.name.lowercase().endsWith(".bin") } > 1
                val title = when {
                    game == null -> null
                    multi || discNo == null -> game.title.replace(DISC_SUFFIX, "")
                    else -> "${game.title} (Disco $discNo)"
                }
                return discResult(disc, game?.copy(title = title ?: game.title))
            }
        }

        // 3) ROM de console doméstico dentro do compactado (pega a maior, ignorando leia-me etc.).
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

        // 4) Numa pasta de arcade (ex.: "Arcade", "MAME"), um .zip desconhecido é tratado como romset
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

    // ------------------------------------------------------------ discos (CD)

    /**
     * BIOS do PlayStation (512 KB, .bin): reconhecida pelo CRC das versões conhecidas ou pelo
     * nome "scph…". Vem antes do Mega Drive, que também usa .bin.
     */
    private fun playStationBios(target: ScanTarget, ext: String): IdentifyOutcome? {
        if (ext != "bin" || target.size != PS_BIOS_SIZE) return null
        val system = catalog.all.firstOrNull { it.identification == IdentificationStrategy.DISC_SERIAL } ?: return null
        val crc = target.crc32(0)
        val known = PS_BIOS_CRCS[crc]
        if (known == null && !target.fileName.lowercase().startsWith("scph")) return null
        val name = known ?: target.fileName.substringBeforeLast('.').lowercase()
        return IdentifyOutcome.Bios(BiosFound(system.id, name, complete = known != null))
    }

    /**
     * .bin de CD: a trilha citada por um .cue da pasta não é um jogo à parte (o .cue é que aparece);
     * um .bin grande sem .cue (centenas de MB; cartucho de Mega Drive tem poucos MB) é um disco.
     */
    private fun discTrack(target: ScanTarget): IdentifyOutcome? {
        val system = catalog.all.firstOrNull { it.identification == IdentificationStrategy.DISC_SERIAL } ?: return null
        val me = target.fileName.lowercase()
        val inCue = target.folderFiles.filter { it.lowercase().endsWith(".cue") }.any { cue ->
            target.sibling(cue)?.let { DiscImages.cueFiles(it.readText()).any { f -> f.lowercase() == me } } == true
        }
        if (inCue) return IdentifyOutcome.NotAGame
        if (target.size < DISC_TRACK_MIN_BYTES) return null
        if (partOfPlaylist(target)) return IdentifyOutcome.NotAGame
        val serial = runCatching { DiscImages.isoSerial({ o, l -> target.read(o, l) }, target.size) }.getOrNull()
        val game = lookupDisc(system, serial, target.fileName)
        val disc = DiscImages.discNumber(target.fileName)
        return discResult(system, game?.let { g -> if (disc != null) g.copy(title = "${g.title} (Disco $disc)") else g })
    }

    /**
     * Disco: .m3u (vários discos, aparece um jogo só), .cue (+ trilhas .bin), .iso, .pbp, .chd.
     * Identifica pelo código do disco; sem código (ex.: .chd, que é compactado), pelo nome.
     */
    private fun identifyDisc(target: ScanTarget, system: GameSystem, ext: String): IdentifyOutcome {
        if (ext == "m3u") {
            val first = DiscImages.m3uEntries(target.readText()).firstOrNull()?.let(target::sibling)
            val serial = first?.let { serialOf(it, SystemDetector.extensionOf(it.fileName).orEmpty()) }
            val game = lookupDisc(system, serial, first?.fileName ?: target.fileName)
            return discResult(system, game?.copy(title = game.title.replace(DISC_SUFFIX, "")))
        }
        // Disco que faz parte de um .m3u da mesma pasta: só o .m3u aparece na biblioteca.
        if (partOfPlaylist(target)) return IdentifyOutcome.NotAGame
        val serial = serialOf(target, ext)
        val game = lookupDisc(system, serial, target.fileName)
        val disc = DiscImages.discNumber(target.fileName) ?: game?.let { DiscImages.discNumber(it.name) }
        return discResult(system, game?.let { g -> if (disc != null && DISC_SUFFIX !in g.title) g.copy(title = "${g.title} (Disco $disc)") else g })
    }

    private fun discResult(system: GameSystem, game: DatGame?) = IdentifyOutcome.GameResult(
        Identification(system.id, game, if (game != null) IdentStatus.IDENTIFIED else IdentStatus.UNIDENTIFIED),
    )

    private fun serialOf(target: ScanTarget, ext: String): String? = runCatching {
        when (ext) {
            "cue" -> DiscImages.cueFiles(target.readText()).firstOrNull()?.let(target::sibling)
                ?.let { track -> DiscImages.isoSerial({ o, l -> track.read(o, l) }, track.size) }
            "iso" -> DiscImages.isoSerial({ o, l -> target.read(o, l) }, target.size)
            "pbp" -> DiscImages.pbpSerial { o, l -> target.read(o, l) }
            else -> null // .chd é compactado: identificado pelo nome
        }
    }.getOrNull()

    /** Pelo código do disco (escolhendo o disco certo pelo nome do arquivo); senão, pelo nome. */
    private fun lookupDisc(system: GameSystem, serial: String?, fileName: String): DatGame? {
        val bySerial = serial?.let { dat.bySerial(system.id, it) }.orEmpty()
        if (bySerial.isNotEmpty()) {
            val disc = DiscImages.discNumber(fileName)
            return bySerial.firstOrNull { disc != null && DiscImages.discNumber(it.name) == disc }
                ?: bySerial.firstOrNull { (DiscImages.discNumber(it.name) ?: 1) == 1 }
                ?: bySerial.first()
        }
        return dat.byTitle(system.id, normalizeTitle(fileName))
    }

    private fun partOfPlaylist(target: ScanTarget): Boolean {
        val me = target.fileName.lowercase()
        return target.folderFiles.filter { it.lowercase().endsWith(".m3u") }.any { m3u ->
            target.sibling(m3u)?.let { DiscImages.m3uEntries(it.readText()).any { e -> e.lowercase() == me } } == true
        }
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

        const val PS_BIOS_SIZE = 512L * 1024
        /** Acima disso, um .bin é trilha de CD (o maior cartucho de Mega Drive tem 5 MB). */
        const val DISC_TRACK_MIN_BYTES = 32L * 1024 * 1024
        private val DISC_SUFFIX = Regex("\\s*\\(Disco? \\d+\\)")

        /** CRC32 das BIOS de PlayStation conhecidas → nome que o PCSX ReARMed procura. */
        val PS_BIOS_CRCS: Map<Long, String> = mapOf(
            0x8D8CB7E4L to "scph5501", // EUA v3.0
            0xFF3EEB8CL to "scph5500", // Japão v3.0
            0xD786F0B9L to "scph5502", // Europa v3.0
            0x37157331L to "scph1001", // EUA v2.2
            0x502224B6L to "scph7001", // EUA v4.1
            0x171BDCECL to "scph101", // PS one v4.5
        )

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
