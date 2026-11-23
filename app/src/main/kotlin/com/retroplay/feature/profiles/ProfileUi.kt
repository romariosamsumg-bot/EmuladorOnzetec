package com.retroplay.feature.profiles

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.core.ui.theme.AccentColor
import com.retroplay.domain.profile.Profile
import com.retroplay.ui.components.rememberInitialFocus
import com.retroplay.ui.tv.Card
import com.retroplay.ui.tv.OutlinedButton

/** Cor de tema (AccentColor) pelo nome guardado no perfil. */
fun accentOf(name: String?): AccentColor = runCatching { AccentColor.valueOf(name!!) }.getOrDefault(AccentColor.LARANJA)

/** Avatar do perfil: ilustração sobre a cor do tema do perfil (ou a inicial, se não escolheu). */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) =
    AvatarBadge(profile.avatar, profile.accent, profile.initial, size, modifier)

@Composable
fun AvatarBadge(avatar: String?, accent: String, initial: String, size: Dp, modifier: Modifier = Modifier) {
    val a = accentOf(accent)
    Box(modifier.size(size).clip(CircleShape).background(a.color), contentAlignment = Alignment.Center) {
        if (avatar != null) {
            Canvas(Modifier.size(size * 0.86f)) { AvatarArt.draw(this, avatar, a.onColor, a.color) }
        } else {
            Text(initial, color = a.onColor, fontSize = (size.value * 0.45f).sp, style = MaterialTheme.typography.titleLarge)
        }
    }
}

/**
 * "Quem vai jogar?": escolhe o perfil, adiciona um novo ou, em "Gerenciar perfis", abre o
 * editor de um perfil (avatar, nome, cor e excluir).
 */
@Composable
fun ProfilePickerScreen(
    profiles: List<Profile>,
    activeId: Long,
    onPick: (Profile) -> Unit,
    onAdd: () -> Unit = {},
    onEdit: (Profile) -> Unit = {},
) {
    var managing by rememberSaveable { mutableStateOf(false) }
    val first = rememberInitialFocus(profiles.isNotEmpty())
    Column(
        Modifier.fillMaxSize().padding(48.dp).testTag("screen_profiles"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(if (managing) R.string.profile_manage_title else R.string.who_plays),
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(36.dp))
        // Margem para o card focado (cresce 10%) não ser cortado nas pontas.
        LazyRow(horizontalArrangement = Arrangement.spacedBy(28.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 12.dp)) {
            val start = profiles.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
            itemsIndexed(profiles, key = { _, p -> p.id }) { i, p ->
                ProfileTile(
                    label = p.name,
                    tag = "profile_${p.id}",
                    modifier = if (i == start) Modifier.focusRequester(first) else Modifier,
                    onClick = { if (managing) onEdit(p) else onPick(p) },
                ) {
                    Box {
                        ProfileAvatar(p, 112.dp)
                        if (managing) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center,
                            ) { Text("✎", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface) }
                        }
                    }
                }
            }
            if (!managing && profiles.size < Profile.MAX_PROFILES) {
                item(key = "add") {
                    ProfileTile(stringResource(R.string.profile_add), "profile_add", onClick = onAdd) {
                        Box(
                            Modifier.size(112.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) { Text("+", fontSize = 56.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
        OutlinedButton(onClick = { managing = !managing }, modifier = Modifier.testTag("profile_manage")) {
            Text(stringResource(if (managing) R.string.profile_manage_done else R.string.profile_manage))
        }
    }
}

@Composable
private fun ProfileTile(label: String, tag: String, modifier: Modifier = Modifier, onClick: () -> Unit, avatar: @Composable () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier.width(160.dp).testTag(tag),
        shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
        scale = CardDefaults.scale(focusedScale = 1.1f),
        border = CardDefaults.border(focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.onSurface), shape = RoundedCornerShape(16.dp))),
        colors = CardDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp).width(132.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            avatar()
            Spacer(Modifier.height(12.dp))
            Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
