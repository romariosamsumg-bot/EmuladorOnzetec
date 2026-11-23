package com.retroplay.data.library

import androidx.room.withTransaction
import com.retroplay.domain.profile.Profile
import com.retroplay.domain.profile.ProfileRepository
import com.retroplay.domain.settings.AppSettings
import com.retroplay.domain.settings.ScopeRef
import com.retroplay.domain.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Perfis guardados no Room; o perfil ativo fica nas configurações (lido também pelo processo
 * do jogo). Os saves de cada perfil ficam em subpastas "p<id>" de states/ e saves/.
 */
class RoomProfileRepository(
    private val db: LibraryDatabase,
    private val settings: SettingsRepository,
    /** Pastas com subpastas por perfil (save states e saves de bateria). */
    private val profileDirs: List<File>,
    private val clock: () -> Long = System::currentTimeMillis,
) : ProfileRepository {

    override val profiles: Flow<List<Profile>> = db.profiles().observe().map { list -> list.map(::toProfile) }

    override val active: Flow<Profile> = combine(profiles, settings.snapshots) { list, snap ->
        val id = snap.value(AppSettings.ACTIVE_PROFILE).toLongOrNull()
        list.firstOrNull { it.id == id } ?: list.firstOrNull() ?: DEFAULT
    }

    override suspend fun activeId(): Long = active.first().id

    override suspend fun create(name: String, avatar: String?, accent: String?): Profile {
        val count = db.profiles().all().size
        val entity = ProfileEntity(
            name = name.trim().ifEmpty { "Jogador ${count + 1}" }.take(MAX_NAME),
            color = Profile.colorFor(count),
            createdAt = clock(),
            avatar = avatar,
            accent = accent ?: Profile.DEFAULT_ACCENT,
        )
        val id = db.profiles().insert(entity)
        return toProfile(entity.copy(id = id))
    }

    override suspend fun update(id: Long, name: String, avatar: String, accent: String) {
        val clean = name.trim().take(MAX_NAME).ifEmpty { db.profiles().all().firstOrNull { it.id == id }?.name ?: "Jogador" }
        db.profiles().update(id, clean, avatar, accent)
    }

    override suspend fun rename(id: Long, name: String) {
        name.trim().take(MAX_NAME).takeIf { it.isNotEmpty() }?.let { db.profiles().rename(id, it) }
    }

    override suspend fun delete(id: Long): Boolean {
        val all = db.profiles().all()
        if (all.size <= 1 || all.none { it.id == id }) return false
        db.withTransaction {
            db.stats().deleteProfile(id)
            db.profiles().delete(id)
        }
        Profile.dirName(id)?.let { dir -> withContext(Dispatchers.IO) { profileDirs.forEach { File(it, dir).deleteRecursively() } } }
        if (activeId() == id || all.none { it.id == activeId() }) switchTo(all.first { it.id != id }.id)
        return true
    }

    override suspend fun switchTo(id: Long) {
        settings.set(AppSettings.ACTIVE_PROFILE, ScopeRef.Global, id.toString())
    }

    companion object {
        const val MAX_NAME = Profile.MAX_NAME
        val DEFAULT = Profile(Profile.DEFAULT_ID, "Jogador 1", Profile.colorFor(0))
        fun toProfile(e: ProfileEntity) = Profile(e.id, e.name, e.color, e.avatar, e.accent)
    }
}
