package com.retroplay.core.common

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.view.InputDevice

/** TV (controle remoto) ou celular/tablet (toque). O mesmo APK roda nos dois. */
object DeviceKind {
    fun isTv(context: Context): Boolean {
        val ui = context.getSystemService(UiModeManager::class.java)
        return ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    }

    fun hasTouchscreen(context: Context): Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)

    /** Algum gamepad físico conectado agora? */
    fun hasGamepad(): Boolean = InputDevice.getDeviceIds().any { id ->
        InputDevice.getDevice(id)?.let { d ->
            !d.isVirtual && (d.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD ||
                d.sources and InputDevice.SOURCE_JOYSTICK == InputDevice.SOURCE_JOYSTICK)
        } == true
    }
}
