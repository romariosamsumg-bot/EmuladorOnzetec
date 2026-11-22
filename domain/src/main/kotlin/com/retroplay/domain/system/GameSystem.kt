package com.retroplay.domain.system

/**
 * Identificador estável de um sistema (usado em banco, configurações e rotas).
 * Nunca renomeie um valor existente: ele é persistido.
 */
@JvmInline
value class SystemId(val value: String) {
    override fun toString(): String = value
}

/** Como os jogos deste sistema são identificados durante a varredura. */
enum class IdentificationStrategy {
    /** CRC32 (ou MD5) do conteúdo da ROM, casado com DATs No-Intro (libretro-database). */
    CONTENT_HASH,

    /**
     * Romset de arcade: identificado pelo nome do .zip e pelos CRCs dos arquivos internos
     * (DAT do FBNeo). O arquivo nunca é descompactado nem modificado.
     */
    ARCADE_ROMSET,
}

/** Um núcleo libretro capaz de rodar o sistema. */
data class CoreDescriptor(
    /** Nome do núcleo no buildbot do libretro, ex.: "snes9x2010". */
    val id: String,
    val displayName: String,
    /** Descrição curta exibida nas configurações. */
    val description: String,
) {
    /** Nome do arquivo .so dentro do zip do buildbot. */
    val libraryFileName: String get() = "${id}_libretro_android.so"
}

/** Arquivo de BIOS exigido (ou opcional) por um sistema. O app nunca fornece BIOS. */
data class BiosRequirement(
    val fileName: String,
    val description: String,
    val required: Boolean,
)

/**
 * Descreve um console suportado. Adicionar um console novo = adicionar uma instância
 * em [SystemRegistry]; a UI é toda gerada a partir destes dados.
 */
data class GameSystem(
    val id: SystemId,
    /** Nome exibido na UI (descritivo, sem logotipos ou marcas). */
    val displayName: String,
    val shortName: String,
    val releaseYear: Int,
    /** Extensões (sem ponto, minúsculas) que podem pertencer a este sistema. */
    val extensions: Set<String>,
    /** Núcleos disponíveis; o primeiro é o padrão. */
    val cores: List<CoreDescriptor>,
    val bios: List<BiosRequirement> = emptyList(),
    val identification: IdentificationStrategy = IdentificationStrategy.CONTENT_HASH,
    val controllerLayout: ControllerLayout,
    /** Cor de destaque do sistema (ARGB), usada em cards gerados e ícones. */
    val accentColor: Long,
    /** Chave do ícone genérico desenhado pelo app (não usa logos oficiais). */
    val iconKey: String,
    /** Nome do sistema no libretro-database / libretro-thumbnails. */
    val libretroDatabaseName: String,
) {
    init {
        require(cores.isNotEmpty()) { "Sistema ${id.value} precisa de ao menos um núcleo" }
        require(extensions.all { it == it.lowercase() && !it.startsWith(".") }) {
            "Extensões devem ser minúsculas e sem ponto"
        }
    }

    val defaultCore: CoreDescriptor get() = cores.first()
    val requiresBios: Boolean get() = bios.any { it.required }
}
