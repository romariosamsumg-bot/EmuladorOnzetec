package com.retroplay.ui

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.system.SystemRegistry
import com.retroplay.fakes.FakeLibraryRepository
import com.retroplay.feature.update.UpdateInfo
import com.retroplay.feature.update.UpdateManager
import com.retroplay.feature.update.UpdateState
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import javax.inject.Inject

/** Versão nova: aviso no topo da Início, com o botão Atualizar agora. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpdateBannerTest {
    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var updates: UpdateManager
    @Inject lateinit var library: FakeLibraryRepository

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun aviso_de_versao_nova_na_inicio() {
        library.games.value = listOf(FakeLibraryRepository.game(1, SystemRegistry.Ids.SNES, "Super Mario World"))
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag("hero")).fetchSemanticsNodes().isNotEmpty() }
        compose.runOnUiThread {
            updates.setStateForTest(UpdateState.Available(UpdateInfo(9999, "9.9.9", listOf("Highlights com som"), "https://x.dev/a.apk")))
        }
        compose.waitUntil(5_000) { compose.onAllNodes(hasTestTag("update_banner"), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("9.9.9", substring = true, useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("update_action").assertTextContains("Atualizar agora")
    }
}
