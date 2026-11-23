package com.retroplay.domain.library

/**
 * Leitura dos formatos de disco (CD) sem bibliotecas externas: lista do .cue e do .m3u e o
 * código do disco (ex.: SLUS-00594) no SYSTEM.CNF da imagem ISO 9660 ou no PARAM.SFO do .pbp.
 * Só lê alguns setores do começo do disco: nunca a imagem inteira.
 */
object DiscImages {

    /** Lê [length] bytes a partir de [offset] (pode devolver menos no fim do arquivo). */
    fun interface Reader {
        fun read(offset: Long, length: Int): ByteArray
    }

    // ------------------------------------------------------------------ listas

    /** Arquivos citados no .cue (FILE "x.bin" BINARY), na ordem. */
    fun cueFiles(text: String): List<String> =
        Regex("^\\s*FILE\\s+(?:\"([^\"]+)\"|(\\S+))", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
            .findAll(text).map { (it.groupValues[1].ifEmpty { it.groupValues[2] }).let(::baseName) }.toList()

    /** Discos listados no .m3u (linhas que não são comentários). */
    fun m3uEntries(text: String): List<String> =
        text.lineSequence().map { it.trim().removePrefix("\uFEFF") }.filter { it.isNotEmpty() && !it.startsWith("#") }.map(::baseName).toList()

    private val DISC_EXT = setOf("m3u", "cue", "chd", "pbp", "iso")

    /** Arquivo de disco (as trilhas .bin contam só junto de um .cue). */
    fun isDiscFile(name: String) = name.substringAfterLast('.', "").lowercase() in DISC_EXT

    /**
     * O arquivo que o núcleo deve abrir dentro de um compactado: .m3u; senão .cue; .chd; .pbp; .iso.
     * Vários discos sem .m3u: o primeiro (o app cria o .m3u ao extrair). null = não é um disco.
     */
    fun mainDiscEntry(names: List<String>): String? {
        val files = names.filter { !it.endsWith("/") }.sortedWith(compareBy({ discNumber(it) ?: 0 }, { it.lowercase() }))
        for (ext in listOf("m3u", "cue", "chd", "pbp", "iso")) {
            files.firstOrNull { it.lowercase().endsWith(".$ext") }?.let { return it }
        }
        return null
    }

    /** Só o nome do arquivo (o app procura os discos na mesma pasta). */
    fun baseName(path: String) = path.substringAfterLast('/').substringAfterLast('\\')

    /** Número do disco no nome ("... (Disc 2).cue" → 2). */
    fun discNumber(name: String): Int? = Regex("\\(Dis[ck]o?\\s*(\\d+)", RegexOption.IGNORE_CASE).find(name)?.groupValues?.get(1)?.toIntOrNull()

    // ------------------------------------------------------------------ código do disco

    /** "SLUS_005.94", "SCUS-94163-0", "slus00594" → "SLUS-00594"; null se não parece um código. */
    fun normalizeSerial(raw: String): String? {
        val m = Regex("([A-Z]{4})[_\\-. ]?(\\d{3})\\.?(\\d{2})").find(raw.uppercase()) ?: return null
        return "${m.groupValues[1]}-${m.groupValues[2]}${m.groupValues[3]}"
    }

    /** Código do disco numa imagem ISO 9660 (crua de 2352 bytes por setor ou de 2048). */
    fun isoSerial(reader: Reader, fileSize: Long): String? {
        val layout = detectLayout(reader, fileSize) ?: return null
        val pvd = layout.readSectors(reader, PVD_SECTOR, 1)
        val root = pvd.copyOfRange(156, 156 + 34)
        val rootLba = le32(root, 2)
        val rootSize = le32(root, 10).coerceAtMost(64 * 2048)
        val dir = layout.readSectors(reader, rootLba.toLong(), (rootSize + 2047) / 2048)
        val cnf = findFile(dir, "SYSTEM.CNF") ?: return null
        val text = layout.readSectors(reader, cnf.first.toLong(), 1).copyOf(cnf.second.coerceAtMost(2048)).toString(Charsets.ISO_8859_1)
        return bootSerial(text)
    }

    /** "BOOT = cdrom:\SLUS_005.94;1" → "SLUS-00594". */
    fun bootSerial(systemCnf: String): String? {
        val boot = Regex("BOOT\\s*=\\s*cdrom:?\\\\*([^\\s;]+)", RegexOption.IGNORE_CASE).find(systemCnf)?.groupValues?.get(1) ?: return null
        return normalizeSerial(boot.substringAfterLast('\\').substringAfterLast('/'))
    }

    /** Código do disco num .pbp (PSP/PS1 "eboot"): chave DISC_ID do PARAM.SFO. */
    fun pbpSerial(reader: Reader): String? {
        val head = reader.read(0, 40)
        if (head.size < 40 || head[0] != 0.toByte() || head[1] != 'P'.code.toByte() || head[2] != 'B'.code.toByte() || head[3] != 'P'.code.toByte()) return null
        val sfoStart = le32(head, 8).toLong()
        val sfoEnd = le32(head, 12).toLong()
        val len = (sfoEnd - sfoStart).toInt()
        if (len !in 20..65_536) return null
        val sfo = reader.read(sfoStart, len)
        return sfoValue(sfo, "DISC_ID")?.let(::normalizeSerial)
    }

    /** Valor de texto de uma chave do PARAM.SFO. */
    fun sfoValue(sfo: ByteArray, key: String): String? {
        if (sfo.size < 20 || sfo[1] != 'P'.code.toByte() || sfo[2] != 'S'.code.toByte() || sfo[3] != 'F'.code.toByte()) return null
        val keyTable = le32(sfo, 8)
        val dataTable = le32(sfo, 12)
        val count = le32(sfo, 16)
        for (i in 0 until count.coerceAtMost(64)) {
            val e = 20 + i * 16
            if (e + 16 > sfo.size) return null
            val keyOff = keyTable + le16(sfo, e)
            val dataLen = le32(sfo, e + 4)
            val dataOff = dataTable + le32(sfo, e + 12)
            val name = cString(sfo, keyOff)
            if (name == key && dataOff + dataLen <= sfo.size) {
                return String(sfo, dataOff, dataLen, Charsets.UTF_8).trimEnd('\u0000').trim()
            }
        }
        return null
    }

    // ------------------------------------------------------------------ ISO 9660

    private const val PVD_SECTOR = 16L

    /** Onde ficam os 2048 bytes de dados em cada setor da imagem. */
    private class Layout(val sectorSize: Int, val dataOffset: Int) {
        fun readSectors(reader: Reader, lba: Long, count: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream(count * 2048)
            for (i in 0 until count) {
                val raw = reader.read((lba + i) * sectorSize + dataOffset, 2048)
                out.write(raw, 0, raw.size)
                if (raw.size < 2048) break
            }
            return out.toByteArray()
        }
    }

    private fun detectLayout(reader: Reader, fileSize: Long): Layout? {
        val options = listOf(Layout(2352, 24), Layout(2352, 16), Layout(2048, 0))
        return options.firstOrNull { l ->
            if (PVD_SECTOR * l.sectorSize + l.dataOffset + 6 > fileSize) return@firstOrNull false
            val id = reader.read(PVD_SECTOR * l.sectorSize + l.dataOffset, 6)
            id.size == 6 && id[0] == 1.toByte() && String(id, 1, 5, Charsets.ISO_8859_1) == "CD001"
        }
    }

    /** Procura [name] no diretório; devolve (setor, tamanho). */
    private fun findFile(dir: ByteArray, name: String): Pair<Int, Int>? {
        var pos = 0
        while (pos < dir.size) {
            val len = dir[pos].toInt() and 0xFF
            if (len == 0) {
                pos = (pos / 2048 + 1) * 2048 // registros não cruzam setores
                continue
            }
            if (pos + 33 > dir.size) break
            val nameLen = dir[pos + 32].toInt() and 0xFF
            if (pos + 33 + nameLen > dir.size) break
            val entry = String(dir, pos + 33, nameLen, Charsets.ISO_8859_1).substringBefore(';')
            if (entry.equals(name, ignoreCase = true)) return le32(dir, pos + 2) to le32(dir, pos + 10)
            pos += len
        }
        return null
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    private fun le16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun cString(b: ByteArray, o: Int): String {
        var end = o
        while (end < b.size && b[end] != 0.toByte()) end++
        return String(b, o, end - o, Charsets.ISO_8859_1)
    }
}
