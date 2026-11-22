package com.retroplay.domain.system

/** Botões do "RetroPad", o controle abstrato do libretro. */
enum class RetroPadButton {
    B, Y, SELECT, START, UP, DOWN, LEFT, RIGHT, A, X, L, R, L2, R2, L3, R3,
}

/**
 * Um botão do console original e o botão do RetroPad que o núcleo espera.
 * @param highlighted destaca o botão na UI (ex.: "Ficha" do Neo Geo).
 */
data class ConsoleButton(
    val label: String,
    val retroPad: RetroPadButton,
    val highlighted: Boolean = false,
)

/** Layout de controle padrão de fábrica de um sistema. */
data class ControllerLayout(val buttons: List<ConsoleButton>) {
    init {
        val dup = buttons.groupBy { it.retroPad }.filterValues { it.size > 1 }.keys
        require(dup.isEmpty()) { "Botões do RetroPad repetidos no layout: $dup" }
    }

    fun buttonFor(retroPad: RetroPadButton): ConsoleButton? = buttons.firstOrNull { it.retroPad == retroPad }
}
