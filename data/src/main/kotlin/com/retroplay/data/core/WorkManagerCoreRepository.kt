package com.retroplay.data.core

import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.retroplay.core.common.DispatcherProvider
import com.retroplay.domain.core.CoreError
import com.retroplay.domain.core.CoreInstallState
import com.retroplay.domain.core.CoreRepository
import com.retroplay.domain.core.InstalledCore
import com.retroplay.domain.system.CoreDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** [CoreRepository] que usa WorkManager para downloads e [CoreStorage] para o estado instalado. */
class WorkManagerCoreRepository(
    private val workManager: WorkManager,
    private val storage: CoreStorage,
    /** ABI do processo; null = aparelho sem suporte. */
    private val abi: String?,
    private val dispatchers: DispatcherProvider,
) : CoreRepository {

    override fun observe(core: CoreDescriptor): Flow<CoreInstallState> =
        workManager.getWorkInfosForUniqueWorkFlow(workName(core))
            .map { infos -> stateFrom(core, infos.lastOrNull()) }
            .distinctUntilChanged()
            .flowOn(dispatchers.io)

    private fun stateFrom(core: CoreDescriptor, info: WorkInfo?): CoreInstallState {
        val abi = abi ?: return CoreInstallState.Failed(CoreError.UNSUPPORTED_ABI)
        return when (info?.state) {
            WorkInfo.State.RUNNING -> CoreInstallState.Downloading(
                info.progress.getLong(CoreDownloadWorker.KEY_DOWNLOADED, 0),
                info.progress.getLong(CoreDownloadWorker.KEY_TOTAL, -1),
            )
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> CoreInstallState.Queued
            WorkInfo.State.FAILED -> storage.read(core.id, abi)?.let { CoreInstallState.Installed(it) }
                ?: CoreInstallState.Failed(
                    info.outputData.getString(CoreDownloadWorker.KEY_ERROR)
                        ?.let { runCatching { CoreError.valueOf(it) }.getOrNull() }
                        ?: CoreError.UNKNOWN,
                )
            else -> storage.read(core.id, abi)?.let { CoreInstallState.Installed(it) } ?: CoreInstallState.NotInstalled
        }
    }

    override fun install(core: CoreDescriptor) {
        val abi = abi ?: return
        val request = OneTimeWorkRequestBuilder<CoreDownloadWorker>()
            .setInputData(workDataOf(CoreDownloadWorker.KEY_CORE to core.id, CoreDownloadWorker.KEY_ABI to abi))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()
        // KEEP: tocar "Baixar" duas vezes não duplica o download.
        workManager.enqueueUniqueWork(workName(core), ExistingWorkPolicy.KEEP, request)
    }

    override suspend fun installed(core: CoreDescriptor): InstalledCore? = withContext(dispatchers.io) {
        abi?.let { storage.read(core.id, it) }
    }

    override suspend fun remove(core: CoreDescriptor) = withContext(dispatchers.io) {
        abi?.let { storage.delete(core.id, it) }
        workManager.pruneWork()
        Unit
    }

    private fun workName(core: CoreDescriptor) = "core-${core.id}-$abi"

    companion object {
        const val TAG = "core-download"
    }
}
