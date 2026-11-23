package com.retroplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.library.CopyState
import com.retroplay.domain.library.GameFolder
import com.retroplay.domain.library.GameSource
import com.retroplay.domain.library.IdentStatus
import com.retroplay.domain.library.ScanState
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.FakeLibraryRepository
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject

@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TransferScreenTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Inject
    lateinit var library: FakeLibraryRepository

    @Before
    fun setUp() = hiltRule.inject()

    /** Envia a tecla para a raiz de cima (o diálogo, quando aberto). */
    private fun press(key: Key) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    private fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    /** Início → menu → "Transferir jogos" só com o D-pad. */
    private fun openTransfer() {
        press(Key.DirectionLeft)
        repeat(4) { press(Key.DirectionDown) }
        node("nav_transfer").assertIsFocused()
        press(Key.DirectionCenter)
        node("screen_transfer").assertExists()
    }

    private fun assertQrInteiro() {
        val qr = node("transfer_qr").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assert(qr.top >= root.top && qr.bottom <= root.bottom && qr.width > 0f) { "QR cortado: $qr em $root" }
        assertEquals("QR quadrado", qr.width, qr.height, 1f)
    }

    @Test
    fun sem_permissao_pede_acesso_aos_arquivos() {
        library.gamesFolderReady = false
        // A TV tem a tela da permissão do app e as Configurações gerais.
        val pm = shadowOf(compose.activity.packageManager)
        fun screen(intent: android.content.Intent) = pm.addResolveInfoForIntent(
            intent,
            android.content.pm.ResolveInfo().apply {
                activityInfo = android.content.pm.ActivityInfo().apply { packageName = "com.android.tv.settings"; name = "S" + intent.action }
            },
        )
        val pkg = android.net.Uri.parse("package:" + compose.activity.packageName)
        screen(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkg))
        screen(android.content.Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, pkg))
        screen(android.content.Intent(android.provider.Settings.ACTION_SETTINGS))
        openTransfer()
        node("grant_access").assertIsFocused()
        assertQrInteiro()
        screenshot("20_transfer_sem_permissao")
        press(Key.DirectionCenter)
        // Guia com o caminho na TV e só as telas que existem no aparelho.
        node("all_files_guide").assertExists()
        // Primeiro: Apps → Onzetec Games (Permissões → Arquivos e mídia).
        node("access_details").assertIsFocused()
        node("access_app").assertExists()
        node("access_settings").assertExists()
        node("access_list").assertDoesNotExist()
        node("adb_help").assertExists()
        screenshot("21_transfer_guia_permissao")
        press(Key.DirectionCenter)
        val intent = shadowOf(compose.activity).nextStartedActivity
        assertEquals(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, intent.action)
        assertEquals(pkg, intent.data)
    }

    @Test
    fun pasta_jogos_pronta_mostra_caminho_e_verifica() {
        library.gamesFolderReady = true
        library.games.value = listOf(FakeLibraryRepository.game(1, SystemRegistry.Ids.SNES, "Super Mario World"))
        library.folders.value = listOf(
            GameFolder(1, "/storage/emulated/0/Jogos", GameSource.FILE, "Jogos", true, 1_000, 1),
            GameFolder(2, "content://com.android.externalstorage.documents/tree/ABCD-1234%3AROMs", GameSource.DOCUMENT, "ROMs", false, 1_000, 12),
        )
        library.scan.value = ScanState.Scanning(12, 340, "kof98.zip")
        openTransfer()
        node("scan_now").assertIsFocused()
        compose.onNode(hasText("/storage/emulated/0/Jogos", substring = true)).assertExists()
        compose.onNodeWithTag("transfer_list").performScrollToNode(hasTestTag("scan_status"))
        node("scan_status").assertTextContains("Verificando 12 de 340: kof98.zip")
        // A lista rolou, mas o QR do celular fica inteiro na tela (coluna fixa).
        assertQrInteiro()
        screenshot("21_transfer_pronta")
        val before = library.scanRequests
        press(Key.DirectionCenter)
        assertEquals(before + 1, library.scanRequests)
    }

    @Test
    fun pendrive_pode_ser_copiado_para_a_tv() {
        library.gamesFolderReady = true
        library.folders.value = listOf(
            GameFolder(2, "content://x/tree/ABCD%3AROMs", GameSource.DOCUMENT, "ROMs", false, 1_000, 12),
        )
        openTransfer()
        // Navega até o botão "Copiar para a TV" só com o D-pad.
        repeat(10) {
            runCatching { node("copy_folder_2").assertIsFocused() }.onSuccess { return@repeat }
            press(Key.DirectionDown)
        }
        node("copy_folder_2").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf(2L), library.copied)
        library.copy.value = CopyState.Copying(3, 12, "mslug.zip")
        compose.waitForIdle()
        node("copy_status").assertTextContains("Copiando 3 de 12: mslug.zip")
    }

    @Test
    fun arquivo_ambiguo_escolhe_o_console_uma_vez() {
        library.gamesFolderReady = true
        library.games.value = listOf(
            FakeLibraryRepository.game(7, null, "quebrado", "quebrado.zip", status = IdentStatus.NEEDS_SYSTEM),
        )
        openTransfer()
        repeat(10) {
            runCatching { node("needs_system_7").assertIsFocused() }.onSuccess { return@repeat }
            press(Key.DirectionDown)
        }
        node("needs_system_7").assertIsFocused()
        press(Key.DirectionCenter)
        compose.onNode(isDialog()).assertExists()
        node("choose_snes").assertIsFocused()
        screenshot("22_escolher_console")
        press(Key.DirectionDown)
        node("choose_megadrive").assertIsFocused()
        press(Key.DirectionCenter)
        assertEquals(listOf(7L to SystemRegistry.Ids.MEGA_DRIVE), library.assigned)
        node("needs_system_7").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
