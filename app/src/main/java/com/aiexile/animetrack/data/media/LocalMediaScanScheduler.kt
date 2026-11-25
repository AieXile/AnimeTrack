package com.aiexile.animetrack.data.media

import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.await
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.concurrent.TimeUnit

internal enum class LocalScanPhase { QUEUED, SCANNING, MATCHING, COMPLETED, CANCELLED, FAILED }

internal enum class LocalScanFailure { PERMISSION, READ, OTHER }

internal enum class LocalScrapePhase { QUEUED, RUNNING, RETRY_SCHEDULED, STOPPED }

internal data class LocalScanStatus(
    val phase: LocalScanPhase,
    val scannedFiles: Int = 0,
    val videoFiles: Int = 0,
    val failure: LocalScanFailure? = null
) {
    val isActive: Boolean
        get() = phase == LocalScanPhase.QUEUED || phase == LocalScanPhase.SCANNING || phase == LocalScanPhase.MATCHING
}

internal class LocalMediaScanScheduler(
    private val workManager: WorkManager,
    private val initialDelayMs: Long = 0L
) {
    suspend fun enqueue(sourceId: Long, reidentify: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<LocalMediaScanWorker>()
            .setInputData(workDataOf(LocalMediaScanWorker.SOURCE_ID to sourceId, LocalMediaScanWorker.REIDENTIFY to reidentify))
            .setInitialDelay(initialDelayMs, TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(name(sourceId), ExistingWorkPolicy.KEEP, request).await()
    }

    suspend fun cancel(sourceId: Long) {
        workManager.cancelUniqueWork(name(sourceId)).await()
        workManager.cancelUniqueWork(LocalMediaScrapeWorker.workName(sourceId)).await()
    }

    fun observe(sourceId: Long): Flow<LocalScanStatus?> = workManager.getWorkInfosForUniqueWorkFlow(name(sourceId))
        .map { history ->
            // KEEP 同一时间只允许一个未结束任务；有活跃任务时不显示以前的结果。
            (history.firstOrNull { !it.state.isFinished } ?: history.firstOrNull())?.toStatus()
        }
        .distinctUntilChanged()

    suspend fun retryScrape(sourceId: Long) {
        val phase = observeScrape(sourceId).first()
        if (phase == LocalScrapePhase.STOPPED || phase == LocalScrapePhase.RETRY_SCHEDULED) {
            LocalMediaScrapeWorker.enqueue(workManager, sourceId)
        }
    }

    fun observeScrape(sourceId: Long): Flow<LocalScrapePhase> =
        workManager.getWorkInfosForUniqueWorkFlow(LocalMediaScrapeWorker.workName(sourceId))
            .map { history ->
                val active = history.firstOrNull { !it.state.isFinished }
                when {
                    active == null -> LocalScrapePhase.STOPPED
                    active.state == WorkInfo.State.RUNNING -> LocalScrapePhase.RUNNING
                    active.runAttemptCount > 0 -> LocalScrapePhase.RETRY_SCHEDULED
                    else -> LocalScrapePhase.QUEUED
                }
            }.distinctUntilChanged()

    private fun WorkInfo.toStatus(): LocalScanStatus {
        val phase = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> LocalScanPhase.QUEUED
            WorkInfo.State.RUNNING -> if (progress.getBoolean(LocalMediaScanWorker.MATCHING, false)) {
                LocalScanPhase.MATCHING
            } else {
                LocalScanPhase.SCANNING
            }
            WorkInfo.State.SUCCEEDED -> LocalScanPhase.COMPLETED
            WorkInfo.State.FAILED -> LocalScanPhase.FAILED
            WorkInfo.State.CANCELLED -> LocalScanPhase.CANCELLED
        }
        val data = if (state.isFinished) outputData else progress
        val failure = if (phase == LocalScanPhase.FAILED) {
            LocalScanFailure.entries.firstOrNull { it.name == data.getString(LocalMediaScanWorker.FAILURE) }
                ?: LocalScanFailure.OTHER
        } else null
        return LocalScanStatus(
            phase,
            data.getInt(LocalMediaScanWorker.SCANNED, 0),
            data.getInt(LocalMediaScanWorker.VIDEOS, 0),
            failure
        )
    }

    private fun name(sourceId: Long) = "local-media-scan:$sourceId"
}
