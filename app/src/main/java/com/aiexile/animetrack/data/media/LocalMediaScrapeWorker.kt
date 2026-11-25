package com.aiexile.animetrack.data.media

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.await
import androidx.work.workDataOf
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.playback.LocalMediaAutoMatchUseCase
import java.util.concurrent.TimeUnit

/** 联网重试只处理 Room 中的证据，绝不重新遍历 SAF。 */
internal class LocalMediaScrapeWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(LocalMediaScanWorker.SOURCE_ID, 0)
        val repository = AppContainer.getLocalMediaRepository()
        if (id <= 0 || repository.getSource(id) == null) return Result.success()
        AppContainer.getSettingsRepository().awaitNetworkPreferences()
        return if (LocalMediaAutoMatchUseCase(repository, AppContainer.getAnimeRepository()).run(id)) {
            Result.retry()
        } else Result.success()
    }

    companion object {
        fun workName(sourceId: Long) = "local-media-scrape:$sourceId"

        suspend fun enqueue(workManager: WorkManager, sourceId: Long) {
            val request = OneTimeWorkRequestBuilder<LocalMediaScrapeWorker>()
                .setInputData(workDataOf(LocalMediaScanWorker.SOURCE_ID to sourceId))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(workName(sourceId), ExistingWorkPolicy.REPLACE, request).await()
        }
    }
}
