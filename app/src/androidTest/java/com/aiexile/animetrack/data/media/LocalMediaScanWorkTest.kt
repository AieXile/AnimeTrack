package com.aiexile.animetrack.data.media

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.aiexile.animetrack.di.AppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class LocalMediaScanWorkTest {
    @Test
    fun realWorkManagerKeepsOneTaskPerSourceAndCancellationDoesNotLeakAcrossSources() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        check(context.packageName.endsWith(".debug"))
        val repository = AppContainer.getLocalMediaRepository()
        val manager = WorkManager.getInstance(context)
        val scheduler = LocalMediaScanScheduler(manager, initialDelayMs = 60_000)
        val first = repository.addOrGetSource(Uri.parse("content://test/tree/${UUID.randomUUID()}"), "Scan work test A")
        val second = repository.addOrGetSource(Uri.parse("content://test/tree/${UUID.randomUUID()}"), "Scan work test B")
        try {
            scheduler.enqueue(first.id)
            scheduler.enqueue(first.id)
            assertEquals(1, manager.getWorkInfosForUniqueWorkFlow("local-media-scan:${first.id}").first().size)
            assertEquals(LocalScanPhase.QUEUED, scheduler.await(first.id) { it.isActive }.phase)
            assertNull(scheduler.observe(second.id).first())
            scheduler.enqueue(second.id)
            scheduler.cancel(first.id)
            assertEquals(LocalScanPhase.CANCELLED, scheduler.await(first.id) { !it.isActive }.phase)
            assertEquals(LocalScanPhase.QUEUED, scheduler.await(second.id) { it.isActive }.phase)
            assertEquals(LocalMediaScanState.IDLE, repository.getSource(first.id)?.scanState)

            val reopened = LocalMediaScanScheduler(manager)
            assertEquals(LocalScanPhase.CANCELLED, reopened.observe(first.id).first()?.phase)
            reopened.enqueue(first.id)
            val failure = reopened.await(first.id) { it.phase == LocalScanPhase.FAILED }
            assertEquals(LocalScanFailure.PERMISSION, failure.failure)
            assertFalse(failure.isActive)
            assertEquals(LocalMediaPermissionState.REVOKED, repository.getSource(first.id)?.permissionState)
        } finally {
            scheduler.cancel(first.id)
            scheduler.cancel(second.id)
            repository.deleteSource(first)
            repository.deleteSource(second)
        }
    }

    private suspend fun LocalMediaScanScheduler.await(
        id: Long,
        predicate: (LocalScanStatus) -> Boolean
    ): LocalScanStatus = withTimeout(20_000) {
        checkNotNull(observe(id).first { it != null && predicate(it) })
    }
}
