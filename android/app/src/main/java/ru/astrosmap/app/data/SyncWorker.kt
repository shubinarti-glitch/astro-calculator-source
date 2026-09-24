package ru.astrosmap.app.data

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/** Фоновая синхронизация карт (периодическая, WorkManager). */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncManager: SyncManager,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result =
        if (syncManager.sync()) Result.success() else Result.retry()

    companion object {
        const val PERIODIC_NAME = "profiles-sync"
    }
}
