package com.retroplay.core.ui.components

import androidx.annotation.DrawableRes
import com.retroplay.core.ui.R

/** Ícones genéricos desenhados pelo app (sem logos oficiais), por chave do [GameSystem.iconKey]. */
object SystemIcons {
    @DrawableRes
    fun forKey(iconKey: String): Int = when (iconKey) {
        "pad_16bit_rounded" -> R.drawable.ic_sys_pad_rounded
        "pad_16bit_crescent" -> R.drawable.ic_sys_pad_crescent
        "pad_8bit" -> R.drawable.ic_sys_pad_8bit
        "arcade_stick" -> R.drawable.ic_sys_arcade
        "pad_dual_grip" -> R.drawable.ic_sys_pad_dual
        "handheld" -> R.drawable.ic_sys_handheld
        "handheld_wide" -> R.drawable.ic_sys_handheld_wide
        "joystick_atari" -> R.drawable.ic_sys_joystick
        else -> R.drawable.ic_nav_consoles
    }
}
