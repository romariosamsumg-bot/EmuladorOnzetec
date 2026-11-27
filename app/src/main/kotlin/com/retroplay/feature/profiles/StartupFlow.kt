package com.retroplay.feature.profiles

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.core.ui.theme.AnimationLevel
import com.retroplay.core.ui.theme.LocalRetroUiPrefs
import com.retroplay.core.ui.theme.RetroPlayTheme
import com.retroplay.domain.profile.Profile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Como o app começa. Produção: abertura com o logo, sempre "Quem vai jogar?" e boas-vindas.
 * Nos testes de interface, tudo desligado (o app abre direto na Início, como antes).
 */
data class StartupOptions(
    val intro: Boolean = true,
    /** Pergunta o perfil a cada abertura, mesmo com um só (se a opção estiver ligada). */
    val alwaysAsk: Boolean = true,
    val welcome: Boolean = true,
)

/** Ações de perfil usadas pela trilha (implementadas com o ProfileRepository). */
interface StartupActions {
    suspend fun switchTo(id: Long)
    suspend fun create(name: String, avatar: String, accent: String): Profile
    suspend fun update(id: Long, name: String, avatar: String, accent: String)
    suspend fun delete(id: Long)
}

/**
 * Telas da trilha. A TV usa [TvStartupUi]; o app de celular troca a escolha e o editor de perfil
 * por versões de toque em retrato.
 */
interface StartupUi {
    @Composable
    fun Picker(profiles: List<Profile>, activeId: Long, onPick: (Profile) -> Unit, onAdd: () -> Unit, onEdit: (Profile) -> Unit)

    @Composable
    fun Editor(
        initial: Profile?,
        suggestedIndex: Int,
        firstRun: Boolean,
        canDelete: Boolean,
        onSave: (name: String, avatar: String, accent: String) -> Unit,
        onCancel: () -> Unit,
        onDelete: () -> Unit,
    )
}

object TvStartupUi : StartupUi {
    @Composable
    override fun Picker(profiles: List<Profile>, activeId: Long, onPick: (Profile) -> Unit, onAdd: () -> Unit, onEdit: (Profile) -> Unit) =
        ProfilePickerScreen(profiles, activeId, onPick, onAdd, onEdit)

    @Composable
    override fun Editor(
        initial: Profile?,
        suggestedIndex: Int,
        firstRun: Boolean,
        canDelete: Boolean,
        onSave: (name: String, avatar: String, accent: String) -> Unit,
        onCancel: () -> Unit,
        onDelete: () -> Unit,
    ) = ProfileEditorScreen(initial, suggestedIndex, firstRun, canDelete, onSave, onCancel, onDelete)
}

private enum class Stage { INTRO, PICK, EDIT, WELCOME, APP }

/** Editor: novo perfil. */
private const val NEW_PROFILE = -1L

/**
 * Trilha de inicialização: Abertura → (Criar perfil na 1ª vez | Quem vai jogar?) → Boas-vindas
 * → app. [app] recebe a ação "trocar perfil" (usada no menu lateral), que volta para a escolha.
 */
@Composable
fun StartupFlow(
    options: StartupOptions,
    profiles: List<Profile>?,
    activeId: Long,
    ask: Boolean,
    actions: StartupActions,
    ui: StartupUi = TvStartupUi,
    app: @Composable (switchProfile: () -> Unit) -> Unit,
) {
    // Sem abertura (testes), a Início aparece já enquanto os perfis carregam (fração de segundo).
    var stage by rememberSaveable { mutableStateOf(if (options.intro) Stage.INTRO else Stage.APP) }
    var decided by rememberSaveable { mutableStateOf(false) }
    var introDone by rememberSaveable { mutableStateOf(!options.intro) }
    var editing by rememberSaveable { mutableStateOf(NEW_PROFILE) }
    var firstRun by rememberSaveable { mutableStateOf(false) }
    /** A escolha foi aberta pelo menu lateral (Voltar retorna ao app). */
    var fromApp by rememberSaveable { mutableStateOf(false) }
    var welcomeId by rememberSaveable { mutableStateOf(activeId) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    fun enter(id: Long) {
        welcomeId = id
        fromApp = false
        stage = if (options.welcome) Stage.WELCOME else Stage.APP
    }

    // Decide o primeiro passo depois da abertura (uma vez por abertura do app).
    if (!decided && introDone && profiles != null) {
        decided = true
        val noneSetUp = profiles.isNotEmpty() && profiles.none { it.isSetUp }
        stage = when {
            noneSetUp -> { firstRun = true; editing = profiles.first().id; Stage.EDIT }
            ask && (options.alwaysAsk || profiles.size > 1) -> Stage.PICK
            options.welcome && options.intro -> { welcomeId = activeId; Stage.WELCOME }
            else -> Stage.APP
        }
    }

    val animations = LocalRetroUiPrefs.current.animations
    val duration = if (animations == AnimationLevel.DESLIGADAS) 0 else 450
    AnimatedContent(
        targetState = stage,
        transitionSpec = {
            (fadeIn(tween(duration)) + scaleIn(tween(duration, easing = FastOutSlowInEasing), initialScale = 0.96f)) togetherWith fadeOut(tween(duration))
        },
        label = "startup",
    ) { s ->
        when (s) {
            Stage.INTRO -> IntroScreen(fast = animations == AnimationLevel.DESLIGADAS) { introDone = true }
            Stage.PICK -> {
                BackHandler(enabled = fromApp) { fromApp = false; stage = Stage.APP }
                ui.Picker(
                    profiles.orEmpty(), activeId,
                    onPick = { p -> scope.launch { actions.switchTo(p.id) }; enter(p.id) },
                    onAdd = { editing = NEW_PROFILE; firstRun = false; stage = Stage.EDIT },
                    onEdit = { p -> editing = p.id; firstRun = false; stage = Stage.EDIT },
                )
            }
            Stage.EDIT -> {
                val list = profiles.orEmpty()
                val current = list.firstOrNull { it.id == editing }
                BackHandler(enabled = !firstRun) { stage = Stage.PICK }
                ui.Editor(
                    initial = current,
                    suggestedIndex = if (current == null) list.size else list.indexOf(current),
                    firstRun = firstRun,
                    canDelete = list.size > 1,
                    onSave = { name, avatar, accent ->
                        scope.launch {
                            when {
                                current == null -> {
                                    val p = actions.create(name, avatar, accent)
                                    actions.switchTo(p.id)
                                    enter(p.id)
                                }
                                firstRun -> {
                                    actions.update(current.id, name, avatar, accent)
                                    actions.switchTo(current.id)
                                    firstRun = false
                                    enter(current.id)
                                }
                                else -> {
                                    actions.update(current.id, name, avatar, accent)
                                    stage = Stage.PICK
                                }
                            }
                        }
                    },
                    onCancel = { stage = Stage.PICK },
                    onDelete = { current?.let { p -> scope.launch { actions.delete(p.id) } }; stage = Stage.PICK },
                )
            }
            Stage.WELCOME -> {
                val p = profiles?.firstOrNull { it.id == welcomeId } ?: profiles?.firstOrNull()
                if (p == null) {
                    LaunchedEffect(Unit) { stage = Stage.APP }
                } else {
                    WelcomeScreen(p, fast = animations == AnimationLevel.DESLIGADAS) { stage = Stage.APP }
                }
            }
            Stage.APP -> app { fromApp = true; stage = Stage.PICK }
        }
    }
}

/** Abertura: logo surgindo sobre o azul-marinho da marca. */
@Composable
fun IntroScreen(fast: Boolean, onDone: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) {
        if (fast) {
            alpha.snapTo(1f); scale.snapTo(1f); delay(400)
        } else {
            launch { alpha.animateTo(1f, tween(700)) }
            scale.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            delay(700)
        }
        onDone()
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF011C38), MaterialTheme.colorScheme.background))).testTag("screen_intro"),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(R.drawable.logo_onzetec),
            contentDescription = stringResource(R.string.app_name),
            modifier = Modifier.fillMaxWidth(0.8f).widthIn(max = 360.dp).graphicsLayer { this.alpha = alpha.value; scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/** Boas-vindas ao perfil escolhido, já na cor dele, antes de entrar na Início. */
@Composable
fun WelcomeScreen(profile: Profile, fast: Boolean, onDone: () -> Unit) {
    val scale = remember { Animatable(0.6f) }
    val alpha = remember { Animatable(0f) }
    LaunchedEffect(profile.id) {
        if (fast) {
            scale.snapTo(1f); alpha.snapTo(1f); delay(300)
        } else {
            launch { alpha.animateTo(1f, tween(400)) }
            scale.animateTo(1f, tween(550, easing = FastOutSlowInEasing))
            delay(650)
        }
        onDone()
    }
    RetroPlayTheme(prefs = LocalRetroUiPrefs.current.copy(accent = accentOf(profile.accent))) {
        Column(
            Modifier.fillMaxSize()
                .background(Brush.radialGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), MaterialTheme.colorScheme.background), radius = 800f))
                .testTag("screen_welcome"),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ProfileAvatar(profile, 160.dp, Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value; this.alpha = alpha.value })
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.welcome_hello, profile.name),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.graphicsLayer { this.alpha = alpha.value },
            )
        }
    }
}
