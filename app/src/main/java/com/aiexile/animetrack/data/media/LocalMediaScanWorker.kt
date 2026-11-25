package com.aiexile.animetrack.data.media

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.aiexile.animetrack.BuildConfig
import com.aiexile.animetrack.di.AppContainer
import com.aiexile.animetrack.domain.playback.LocalMediaAutoMatchUseCase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException

class LocalMediaScanWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val sourceId = inputData.getLong(SOURCE_ID, 0)
        if (sourceId <= 0) return failure(LocalScanFailure.OTHER)
        val media = AppContainer.getLocalMediaRepository()
        val started = SystemClock.elapsedRealtime()
        val timings = if (BuildConfig.DEBUG) LocalMediaRepository.ScanTimings() else null
        return try {
            if (media.getSource(sourceId) == null) return Result.success()
            val scanned = media.scanSource(sourceId, timings, inputData.getBoolean(REIDENTIFY, false)) { progress ->
                setProgress(workDataOf(SCANNED to progress.scannedFiles, VIDEOS to progress.videoFiles))
            }
            val matchStarted = SystemClock.elapsedRealtime()
            setProgress(workDataOf(MATCHING to true, SCANNED to scanned.scannedFiles, VIDEOS to scanned.videoFiles))
            if (LocalMediaAutoMatchUseCase(media, AppContainer.getAnimeRepository()).run(sourceId, online = false)) {
                LocalMediaScrapeWorker.enqueue(WorkManager.getInstance(applicationContext), sourceId)
            }
            if (timings != null) {
                Log.i("LocalMediaScan", "files=${scanned.scannedFiles} videos=${scanned.videoFiles} missing=${scanned.missingFiles} " +
                    "parsed=${scanned.parsedFiles} written=${scanned.writtenFiles} " +
                    "scanMs=${matchStarted - started} matchMs=${SystemClock.elapsedRealtime() - matchStarted} " +
                    "walkMs=${timings.traversalNs / 1_000_000} parseMs=${timings.parsingNs / 1_000_000} " +
                    "dbMs=${timings.databaseNs / 1_000_000}")
            }
            Result.success(workDataOf(SCANNED to scanned.scannedFiles, VIDEOS to scanned.videoFiles))
        } catch (e: CancellationException) {
            throw e
        } catch (_: LocalMediaScanBusyException) {
            // WorkManager 可先报告旧任务已取消、再等协程完成清理。不能误报重扫失败。
            Result.retry()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            when (e) {
                is SecurityException -> failure(LocalScanFailure.PERMISSION)
                is IOException -> failure(LocalScanFailure.READ)
                else -> failure(LocalScanFailure.OTHER)
            }
        }
    }

    private fun failure(kind: LocalScanFailure) = Result.failure(workDataOf(FAILURE to kind.name))

    internal companion object {
        const val SOURCE_ID = "sourceId"
        const val REIDENTIFY = "reidentify"
        const val MATCHING = "matching"
        const val SCANNED = "scanned"
        const val VIDEOS = "videos"
        const val FAILURE = "failure"
    }
}
