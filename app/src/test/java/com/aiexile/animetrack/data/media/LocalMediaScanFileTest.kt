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
    fun changedOrUnknownMetadataAndLegacyClassificationRequireParsing() {
        assertFalse(file.copy(size = 101).hasUnchangedContent(saved))
        assertFalse(file.copy(lastModified = 201).hasUnchangedContent(saved))
        assertFalse(file.copy(relativePath = "Moved/Series - 01.mkv").hasUnchangedContent(saved))
        assertFalse(file.copy(size = 0).hasUnchangedContent(saved.copy(size = 0)))
        assertFalse(file.copy(lastModified = 0).hasUnchangedContent(saved.copy(lastModified = 0)))
        assertFalse(file.hasUnchangedContent(saved.copy(episodeType = null)))
    }
}
