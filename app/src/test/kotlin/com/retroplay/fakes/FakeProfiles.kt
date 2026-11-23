package com.retroplay.fakes

import com.retroplay.data.di.ProfileModule
import com.retroplay.domain.profile.Profile
import com.retroplay.domain.profile.ProfileRepository
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Singleton

/** Perfis em memória; o perfil ativo continua nas configurações reais (como no app). */
class FakeProfiles(private val settings: SettingsRepository) : ProfileRepository {
    val list = MutableStateFlow(initial)

    companion object {
        /** Perfis com que cada teste começa (StartupFlowTest começa com um perfil não configurado). */
        val DEFAULT = listOf(Profile(1, "Jogador 1", Profile.colorFor(0), avatar = "controle"))
        @Volatile var initial: List<Profile> = DEFAULT
    }
    override val profiles: Flow<List<Profile>> = list
    override val active: Flow<Profile> = combine(list, settings.snapshots) { l, s ->
        l.firstOrNull { it.id == s.value(AppSettings.ACTIVE_PROFILE).toLongOrNull() } ?: l.first()
    }
    override suspend fun activeId() = active.first().id
    override suspend fun create(name: String, avatar: String?, accent: String?): Profile {
        val p = Profile((list.value.maxOf { it.id }) + 1, name.trim().ifEmpty { "Jogador" }, Profile.colorFor(list.value.size), avatar, accent ?: Profile.DEFAULT_ACCENT)
        list.value = list.value + p
        return p
    }
    override suspend fun update(id: Long, name: String, avatar: String, accent: String) {
        list.value = list.value.map { if (it.id == id) it.copy(name = name.trim().ifEmpty { it.name }, avatar = avatar, accent = accent) else it }
    }
    override suspend fun rename(id: Long, name: String) { list.value = list.value.map { if (it.id == id) it.copy(name = name) else it } }
    override suspend fun delete(id: Long): Boolean {
        if (list.value.size <= 1) return false
        list.value = list.value.filter { it.id != id }
        if (activeId() == id) switchTo(list.value.first().id)
        return true
    }
    override suspend fun switchTo(id: Long) = settings.set(AppSettings.ACTIVE_PROFILE, ScopeRef.Global, id.toString())
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ProfileModule::class])
object FakeProfileModule {
    @Provides
    @Singleton
    fun profiles(settings: SettingsRepository): ProfileRepository = FakeProfiles(settings)
}
