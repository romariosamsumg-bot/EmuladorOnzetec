package com.retroplay.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.retroplay.MainActivity
import com.retroplay.domain.profile.Profile
import com.retroplay.domain.profile.ProfileRepository
import com.retroplay.fakes.FakeProfiles
import com.retroplay.fakes.TestStartup
import com.retroplay.feature.profiles.StartupOptions
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import javax.inject.Inject

/** Base: trilha de inicialização completa ligada (abertura, pergunta de perfil, boas-vindas). */
@OptIn(ExperimentalTestApi::class)
abstract class StartupTestBase {
    abstract val compose: androidx.compose.ui.test.junit4.AndroidComposeTestRule<*, MainActivity>

    protected fun press(key: Key, times: Int = 1) = repeat(times) {
        compose.onAllNodes(isRoot()).onLast().performKeyInput { pressKey(key) }
        compose.mainClock.advanceTimeBy(300)
        compose.waitForIdle()
    }

    protected fun node(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag, useUnmergedTree = true)

    protected fun waitFor(tag: String) {
        compose.waitUntil(10_000) { compose.onAllNodes(hasTestTag(tag), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.waitForIdle()
    }

    protected fun focused(tag: String) = compose.onAllNodes(hasTestTag(tag) and isFocused(), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    protected fun screenshot(name: String) {
        if (System.getProperty("retroplay.screenshots") != "true") return
        val dir = File("build/screenshots/startup").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    companion object {
        val FULL = StartupOptions(intro = true, alwaysAsk = true, welcome = true)
        val TESTS = StartupOptions(intro = false, alwaysAsk = false, welcome = false)
    }
}

/** 1ª abertura: abertura com o logo → "Crie seu perfil" → boas-vindas → Início. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupFirstRunTest : StartupTestBase() {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    override val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var profiles: ProfileRepository

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun abertura_criar_perfil_boas_vindas_e_inicio() {
        waitFor("screen_profile_editor")
        screenshot("01_criar_perfil")
        node("profile_name").assertIsFocused()
        press(Key.DirectionDown)
        node("avatar_controle").assertIsFocused()
        press(Key.DirectionRight, 2)
        node("avatar_estrela").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown, 2)
        repeat(6) { if (!focused("color_LARANJA")) press(Key.DirectionLeft) }
        node("color_LARANJA").assertIsFocused()
        press(Key.DirectionRight)
        node("color_VIOLETA").assertIsFocused()
        press(Key.DirectionCenter)
        press(Key.DirectionDown)
        repeat(3) { if (!focused("profile_save")) press(Key.DirectionLeft) }
        node("profile_save").assertIsFocused()
        // Na 1ª abertura não há "Cancelar" (o perfil precisa existir).
        node("profile_cancel").assertDoesNotExist()
        press(Key.DirectionCenter)
        waitFor("screen_welcome")
        screenshot("02_boas_vindas")
        waitFor("screen_home")
        // O perfil 1 (com os saves antigos) foi configurado, não criado outro.
        val list = runBlocking { profiles.profiles.first() }
        assertEquals(1, list.size)
        assertEquals("estrela", list.single().avatar)
        assertEquals("VIOLETA", list.single().accent)
    }

    companion object {
        @JvmStatic @BeforeClass
        fun before() {
            TestStartup.options = FULL
            FakeProfiles.initial = listOf(Profile(1, "Jogador 1", Profile.colorFor(0)))
        }

        @JvmStatic @AfterClass
        fun after() {
            TestStartup.options = TESTS
            FakeProfiles.initial = FakeProfiles.DEFAULT
        }
    }
}

/** Aberturas seguintes: "Quem vai jogar?" → boas-vindas → Início; e trocar pelo menu lateral. */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@Config(application = HiltTestApplication::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StartupPickTest : StartupTestBase() {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    override val compose = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var profiles: ProfileRepository

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun escolher_perfil_e_trocar_pelo_menu_lateral() {
        waitFor("screen_profiles")
        screenshot("03_quem_vai_jogar")
        node("profile_1").assertIsFocused()
        press(Key.DirectionRight)
        node("profile_2").assertIsFocused()
        press(Key.DirectionCenter)
        waitFor("screen_welcome")
        waitFor("screen_home")
        compose.waitUntil(5_000) { runBlocking { profiles.activeId() } == 2L }
        // Menu lateral: o perfil fica acima do Início; abre "Quem vai jogar?".
        compose.waitUntil(5_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        press(Key.DirectionLeft)
        node("nav_home").assertIsFocused()
        press(Key.DirectionUp)
        node("nav_profile").assertIsFocused()
        screenshot("04_menu_perfil")
        press(Key.DirectionCenter)
        waitFor("screen_profiles")
        // Voltar (sem escolher) retorna ao app.
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        waitFor("screen_home")
    }

    companion object {
        @JvmStatic @BeforeClass
        fun before() {
            TestStartup.options = FULL
            FakeProfiles.initial = listOf(
                Profile(1, "Pai", 0, avatar = "coroa", accent = "CIANO"),
                Profile(2, "Ana", 0, avatar = "gato", accent = "CORAL"),
            )
        }

        @JvmStatic @AfterClass
        fun after() {
            TestStartup.options = TESTS
            FakeProfiles.initial = FakeProfiles.DEFAULT
        }
    }
}
