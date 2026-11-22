package com.retroplay.data.scan

import android.content.Context
import android.os.Process
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Varredura da biblioteca em segundo plano, com prioridade baixa (não disputa CPU com a UI). */
@HiltWorker
class LibraryScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val scanner: LibraryScanner,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        return try {
            val summary = scanner.scan { processed, total, current ->
                setProgress(workDataOf(KEY_PROCESSED to processed, KEY_TOTAL to total, KEY_CURRENT to current))
            }
            Result.success(workDataOf(KEY_ADDED to summary.added, KEY_REMOVED to summary.removed))
        } catch (e: Exception) {
            Result.failure()
        }
    }

    companion object {
        const val UNIQUE_NAME = "library-scan"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT = "current"
        const val KEY_ADDED = "added"
        const val KEY_REMOVED = "removed"
    }
}
