package com.retroplay.feature.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import com.retroplay.BuildConfig
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Endereço do version.json (null = não procura; usado nos testes). */
class UpdateConfig(val url: String?)

/**
 * Atualizações fora da Play Store: lê o version.json do site, baixa o APK novo, confere que é
 * o mesmo app com a mesma assinatura e uma versão maior, e abre a instalação do Android
 * (PackageInstaller). Saves, perfis e jogos continuam.
 */
@Singleton
class UpdateManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: OkHttpClient,
    private val settings: SettingsRepository,
    private val config: UpdateConfig,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val flavor get() = if (BuildConfig.TV_APP) "tv" else "mobile"

    /**
     * Procura versão nova. Sem [force], respeita "Avisar sobre novas versões".
     * @return a versão nova, ou null.
     */
    suspend fun check(force: Boolean = false): UpdateInfo? = withContext(Dispatchers.IO) {
        val url = config.url ?: return@withContext null
        if (!force && !settings.current().value(AppSettings.UPDATE_NOTIFY)) return@withContext null
        val busy = _state.value
        if (busy is UpdateState.Downloading || busy is UpdateState.Installing) return@withContext null
        if (force) _state.value = UpdateState.Checking
        val info = runCatching {
            client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build()).execute().use { r ->
                if (!r.isSuccessful) null else UpdateInfo.parse(r.body.string(), flavor, url)
            }
        }.getOrNull()
        val newer = info?.takeIf { it.versionCode > BuildConfig.VERSION_CODE }
        _state.value = when {
            newer != null -> UpdateState.Available(newer)
            info != null || !force -> if (force) UpdateState.UpToDate else UpdateState.Idle
            else -> UpdateState.Failed(null)
        }
        newer
    }

    /** Baixa, confere e abre a instalação. */
    suspend fun downloadAndInstall(info: UpdateInfo) = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !pm.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsPermission(info)
            return@withContext
        }
        _state.value = UpdateState.Downloading(info, 0f)
        val file = File(context.cacheDir, "updates/OnzetecGames-${info.versionName}.apk").apply { parentFile?.mkdirs() }
        val ok = runCatching {
            client.newCall(Request.Builder().url(info.apkUrl).build()).execute().use { r ->
                check(r.isSuccessful)
                val total = r.body.contentLength()
                r.body.byteStream().use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var last = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (done - last > 256 * 1024) {
                                last = done
                                _state.value = UpdateState.Downloading(info, if (total > 0) done.toFloat() / total else null)
                            }
                        }
                    }
                }
            }
            verify(file)
        }.getOrElse { false }
        if (!ok) {
            file.delete()
            _state.value = UpdateState.Failed(info)
            return@withContext
        }
        _state.value = UpdateState.Installing(info)
        runCatching { install(file) }.onFailure { _state.value = UpdateState.Failed(info) }
    }

    /** O APK é deste app, de versão maior e com a mesma assinatura? */
    private fun verify(apk: File): Boolean {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES
        val archive = pm.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        val installed = pm.getPackageInfo(context.packageName, flags)
        if (archive.packageName != context.packageName) return false
        if (versionCode(archive) <= versionCode(installed)) return false
        return signatures(archive) == signatures(installed)
    }

    private fun versionCode(p: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.longVersionCode else @Suppress("DEPRECATION") p.versionCode.toLong()

    private fun signatures(p: PackageInfo): Set<String> {
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) p.signingInfo?.apkContentsSigners else @Suppress("DEPRECATION") p.signatures
        return sigs.orEmpty().map { it.toCharsString() }.toSet()
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Android 12+: depois da 1ª atualização feita pelo próprio app, as próximas podem
            // dispensar a pergunta "Instalar?".
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
            val intent = Intent(context, InstallResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
        }
    }

    /** O usuário cancelou a instalação ou ela falhou: volta a mostrar "Atualizar". */
    fun installEnded(success: Boolean) {
        val info = (_state.value as? UpdateState.Installing)?.info ?: return
        _state.value = if (success) UpdateState.Idle else UpdateState.Available(info)
    }

    /** Só para testes de tela: mostra um estado sem ir à internet. */
    @androidx.annotation.VisibleForTesting
    fun setStateForTest(state: UpdateState) {
        _state.value = state
    }

    /** Depois de liberar a permissão nas Configurações do Android. */
    fun retryAfterPermission(): UpdateInfo? = (_state.value as? UpdateState.NeedsPermission)?.info

    companion object {
        const val UNIQUE_WORK = "update-check"
    }
}
