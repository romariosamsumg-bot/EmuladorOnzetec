package com.retroplay.feature.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.retroplay.R
import com.retroplay.ui.tv.Button

/** Texto e ação do aviso de atualização (o mesmo para TV e celular). */
data class UpdateBannerModel(val title: String, val body: String, val action: String?, val progress: Float?, val onAction: () -> Unit)

@Composable
fun rememberUpdateBanner(vm: UpdateViewModel): UpdateBannerModel? {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.resumeAfterPermission(context) }
    val keep = stringResource(R.string.update_keep)
    return when (val s = state) {
        is UpdateState.Available -> UpdateBannerModel(
            stringResource(R.string.update_available, s.info.versionName),
            (s.info.notes.take(3).joinToString(" · ").ifEmpty { keep }),
            stringResource(R.string.update_now), null,
        ) { vm.update(s.info) }
        is UpdateState.Downloading -> UpdateBannerModel(
            stringResource(R.string.update_available, s.info.versionName),
            stringResource(R.string.update_downloading, ((s.fraction ?: 0f) * 100).toInt()), null, s.fraction ?: 0f,
        ) {}
        is UpdateState.NeedsPermission -> UpdateBannerModel(
            stringResource(R.string.update_available, s.info.versionName), stringResource(R.string.update_permission),
            stringResource(R.string.update_permission_button), null,
        ) { vm.openInstallPermission(context) }
        is UpdateState.Installing -> UpdateBannerModel(stringResource(R.string.update_available, s.info.versionName), stringResource(R.string.update_installing), null, 1f) {}
        is UpdateState.Failed -> s.info?.let { info ->
            UpdateBannerModel(stringResource(R.string.update_failed), keep, stringResource(R.string.update_retry), null) { vm.update(info) }
        }
        else -> null
    }
}

/** Aviso de versão nova na Início da TV (alcançável pelo direcional). */
@Composable
fun TvUpdateBanner(model: UpdateBannerModel, modifier: Modifier = Modifier) {
    Row(
        modifier.widthIn(max = 900.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
            .padding(horizontal = 20.dp, vertical = 12.dp).testTag("update_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text("⬆ " + model.title, style = MaterialTheme.typography.titleMedium)
            Text(model.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            model.progress?.let { p ->
                Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(Modifier.fillMaxWidth(p.coerceIn(0f, 1f)).height(6.dp).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        if (model.action != null) {
            Button(onClick = model.onAction, modifier = Modifier.testTag("update_action")) { Text(model.action) }
        }
    }
}
