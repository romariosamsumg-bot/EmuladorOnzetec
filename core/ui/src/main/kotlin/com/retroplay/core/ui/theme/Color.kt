package com.retroplay.core.ui.theme

import androidx.compose.ui.graphics.Color

/** Cores de destaque selecionáveis nas configurações (Interface → Cor de destaque). */
enum class AccentColor(val color: Color, val onColor: Color) {
    /** Laranja da marca Onzetec Games (cor padrão). */
    LARANJA(Color(0xFFFF7A1A), Color(0xFF1F0A00)),
    VIOLETA(Color(0xFF8B6CFF), Color(0xFF12082E)),
    CIANO(Color(0xFF3FD0E0), Color(0xFF00262B)),
    AMBAR(Color(0xFFFFB547), Color(0xFF2B1A00)),
    VERDE(Color(0xFF5BD68A), Color(0xFF00290F)),
    CORAL(Color(0xFFFF7A6B), Color(0xFF330A05)),
}

/** Tons base do tema escuro "cinematográfico". */
internal object RetroPalette {
    val Background = Color(0xFF0B0B10)
    val BackgroundOled = Color(0xFF000000)
    val Surface = Color(0xFF15151C)
    val SurfaceOled = Color(0xFF0A0A0D)
    val SurfaceVariant = Color(0xFF23232E)
    val OnSurface = Color(0xFFF2F2F7)
    val OnSurfaceVariant = Color(0xFFA9A9B8)
    val Border = Color(0xFF3A3A48)
    val Error = Color(0xFFFF6B6B)
}
