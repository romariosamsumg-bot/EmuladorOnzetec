package com.retroplay.baselineprofile

import android.view.KeyEvent
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Caminho crítico: abrir o app, chegar à Início e percorrer fileiras e menu pelo direcional
 * (como um usuário com o controle remoto).
 */
@RunWith(AndroidJUnit4::class)
class HomeBaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = "com.retroplay.tv", includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
        repeat(4) { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN); device.waitForIdle() }
        repeat(6) { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_RIGHT); device.waitForIdle() }
        device.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT, 0)
        repeat(8) { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_LEFT); device.waitForIdle() }
        repeat(5) { device.pressKeyCode(KeyEvent.KEYCODE_DPAD_DOWN); device.waitForIdle() }
    }
}
