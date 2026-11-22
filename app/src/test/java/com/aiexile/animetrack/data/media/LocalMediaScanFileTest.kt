package com.aiexile.animetrack.data.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalMediaScanFileTest {
    private val file = LocalMediaScanFile("Series/Series - 01.mkv", "Series - 01.mkv", "uri", 100, 200, "parent")
    private val saved = LocalMediaEntity(
        sourceId = 1,
        documentUri = file.documentUri,
        relativePath = file.relativePath,
        fileName = file.fileName,
        size = file.size,
        lastModified = file.lastModified,
        episodeType = LocalEpisodeType.MAIN,
        parentDocumentId = file.parentDocumentId
    )

    @Test
    fun knownUnchangedMetadataAllowsReuseEvenIfLocatorChanges() {
        assertTrue(file.hasUnchangedContent(saved))
        assertTrue(file.copy(documentUri = "new-uri", parentDocumentId = "new-parent").hasUnchangedContent(saved))
    }

    @Test
    fun recognitionRetryDoesNotResetResolvedOrManuallyClearedFiles() {
        val waiting = saved.copy(groupKey = "group", scrapeState = LocalMediaScrapeState.WAITING)
        assertTrue(waiting.canRetryRecognition())
        assertTrue(waiting.copy(scrapeState = LocalMediaScrapeState.REVIEW).canRetryRecognition())
        assertFalse(waiting.copy(archiveKey = "bgm:42").canRetryRecognition())
        assertFalse(waiting.copy(matchedAnimeId = 12).canRetryRecognition())
        assertFalse(waiting.copy(isUserOverride = true).canRetryRecognition())
        assertFalse(waiting.copy(isHidden = true).canRetryRecognition())
        assertFalse(waiting.copy(groupKey = null).canRetryRecognition())
        assertFalse(waiting.copy(scrapeState = LocalMediaScrapeState.ENRICH).canRetryRecognition())
    }

    @Test
    fun changedOrUnknownMetadataAndLegacyClassificationRequireParsing() {
        assertFalse(file.copy(size = 101).hasUnchangedContent(saved))
        assertFalse(file.copy(lastModified = 201).hasUnchangedContent(saved))
        assertFalse(file.copy(relativePath = "Moved/Series - 01.mkv").hasUnchangedContent(saved))
        assertFalse(file.copy(size = 0).hasUnchangedContent(saved.copy(size = 0)))
        assertFalse(file.copy(lastModified = 0).hasUnchangedContent(saved.copy(lastModified = 0)))
        assertFalse(file.hasUnchangedContent(saved.copy(episodeType = null)))
    }
}
