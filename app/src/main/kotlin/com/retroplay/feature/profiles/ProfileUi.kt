package com.retroplay.feature.profiles

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
import com.retroplay.ui.tv.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.domain.profile.Profile
import com.retroplay.ui.components.rememberInitialFocus

/** Avatar do perfil: inicial do nome sobre a cor do perfil. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(Color(profile.color)), contentAlignment = Alignment.Center) {
        Text(profile.initial, color = Color(0xFF12082E), fontSize = (size.value * 0.45f).sp, style = MaterialTheme.typography.titleLarge)
    }
}

/** "Quem vai jogar?" ao abrir o app (com mais de um perfil). */
@Composable
fun ProfilePickerScreen(profiles: List<Profile>, activeId: Long, onPick: (Profile) -> Unit) {
    val first = rememberInitialFocus(profiles.isNotEmpty())
    Column(
        Modifier.fillMaxSize().padding(48.dp).testTag("screen_profiles"),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.who_plays), style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(40.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
            val start = profiles.indexOfFirst { it.id == activeId }.coerceAtLeast(0)
            itemsIndexed(profiles, key = { _, p -> p.id }) { i, p ->
                Card(
                    onClick = { onPick(p) },
                    modifier = Modifier.width(180.dp).testTag("profile_${p.id}").let { if (i == start) it.focusRequester(first) else it },
                    shape = CardDefaults.shape(RoundedCornerShape(16.dp)),
                    scale = CardDefaults.scale(focusedScale = 1.1f),
                    colors = CardDefaults.colors(containerColor = Color.Transparent),
                ) {
                    Column(Modifier.padding(16.dp).width(148.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        ProfileAvatar(p, 120.dp)
                        Spacer(Modifier.height(12.dp))
                        Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}
