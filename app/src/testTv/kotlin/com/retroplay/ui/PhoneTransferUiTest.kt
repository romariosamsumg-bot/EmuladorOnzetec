package com.retroplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.zxing.BinaryBitmap
import com.google.zxing.LuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import com.retroplay.MainActivity
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.domain.transfer.IncomingFile
import com.retroplay.domain.transfer.PhoneTransferState
import com.retroplay.domain.transfer.TransferError
import com.retroplay.fakes.FakePhoneTransfer
import com.retroplay.ui.components.QrCodes
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PhoneTransferUiTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var phone: FakePhoneTransfer

    @Before
    fun setUp() = hiltRule.inject()

    private fun press(key: Key) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    private fun waitFor(tag: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    @Test
    fun biblioteca_vazia_mostra_qr_do_celular() {
        waitFor("home_phone")
        node("transfer_qr").assertExists()
        node("transfer_pin").assertTextEquals("4821")
        assertEquals(1, phone.users)
        node("home_transfer").assertIsFocused()
        screenshot("40_home_vazia_qr")
    }

    @Test
    fun tela_transferir_liga_o_servidor_e_desliga_ao_sair() {
        waitFor("home_transfer")
        press(Key.DirectionLeft)
        repeat(5) { press(Key.DirectionDown) }
        node("nav_transfer").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("transfer_qr")
        compose.onNodeWithTag("transfer_url", useUnmergedTree = true).assertTextContains("http://192.168.0.15:8080", substring = true)
        node("transfer_pin").assertTextEquals("4821")
        // Arquivos chegando ao vivo.
        phone.incoming.value = listOf(
            IncomingFile(2, "kof98.zip", 50, 100, IncomingFile.Status.RECEIVING),
            IncomingFile(1, "Super Mario World.sfc", 10, 10, IncomingFile.Status.DONE, SystemRegistry.Ids.SNES),
            IncomingFile(3, "virus.zip", 10, 10, IncomingFile.Status.FAILED, error = TransferError.NOT_A_GAME),
        )
        compose.waitForIdle()
        compose.onNode(hasText("Recebendo kof98.zip… 50%", substring = true)).assertExists()
        compose.onNode(hasText("Super Mario World.sfc → Super Nintendo", substring = true)).assertExists()
        compose.onNode(hasText("virus.zip não parece um jogo", substring = true)).assertExists()
        screenshot("41_transferir_celular")
        // Sai da tela (volta ao Início, que com biblioteca vazia também usa o servidor) → vai para Configurações.
        press(Key.DirectionLeft)
        press(Key.DirectionDown)
        press(Key.DirectionCenter)
        compose.mainClock.advanceTimeBy(500)
        compose.waitForIdle()
        node("screen_settings").assertExists()
        assertEquals(0, phone.users)
        assertEquals(PhoneTransferState.Stopped, phone.state.value)
    }

    @Test
    fun sem_rede_mostra_aviso() {
        phone.running = phone.running.copy(url = "")
        waitFor("home_transfer")
        // Simula a TV sem rede depois de abrir a tela.
        phone.state.value = PhoneTransferState.Failed(PhoneTransferState.Failed.Reason.NO_NETWORK)
        compose.waitForIdle()
        compose.onNodeWithTag("transfer_error", useUnmergedTree = true).assertTextContains("não está conectada", substring = true)
    }

    @Test
    fun qr_code_gerado_e_legivel() {
        val text = "http://192.168.0.15:8080/?pin=4821"
        val m = QrCodes.matrix(text, size = 400)
        val source = object : LuminanceSource(m.width, m.height) {
            override fun getRow(y: Int, row: ByteArray?): ByteArray =
                ByteArray(width) { x -> if (m.get(x, y)) 0 else 255.toByte() }
            override fun getMatrix(): ByteArray = ByteArray(width * height) { i -> if (m.get(i % width, i / width)) 0 else 255.toByte() }
        }
        val decoded = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(source)))
        assertEquals(text, decoded.text)
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
