package com.retroplay.data.core

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.retroplay.domain.core.CoreError
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Baixa e instala um núcleo em segundo plano, publicando o progresso. */
@HiltWorker
class CoreDownloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val installer: CoreInstaller,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val coreId = inputData.getString(KEY_CORE) ?: return Result.failure(errorData(CoreError.UNKNOWN))
        val abi = inputData.getString(KEY_ABI) ?: return Result.failure(errorData(CoreError.UNSUPPORTED_ABI))
        return try {
            installer.install(coreId, abi) { downloaded, total ->
                setProgressAsync(workDataOf(KEY_DOWNLOADED to downloaded, KEY_TOTAL to total))
            }
            Result.success()
        } catch (e: CoreInstallException) {
            // Falhas de rede são re-tentadas automaticamente algumas vezes.
            if (e.error == CoreError.NO_NETWORK && runAttemptCount < MAX_RETRIES) Result.retry()
            else Result.failure(errorData(e.error))
        } catch (e: Exception) {
            Result.failure(errorData(CoreError.UNKNOWN))
        }
    }

    private fun errorData(error: CoreError) = workDataOf(KEY_ERROR to error.name)

    companion object {
        const val KEY_CORE = "core"
        const val KEY_ABI = "abi"
        const val KEY_DOWNLOADED = "downloaded"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val MAX_RETRIES = 2
    }
}
