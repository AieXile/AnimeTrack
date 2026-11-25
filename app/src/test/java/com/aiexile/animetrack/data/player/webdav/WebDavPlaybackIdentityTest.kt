package com.aiexile.animetrack.data.player.webdav

import com.aiexile.animetrack.data.player.chooseLegacyWebDavProgress
import com.aiexile.animetrack.model.PlaybackProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavPlaybackIdentityTest {
    @Test
    fun normalizesEquivalentEncodingsAndDefaultEndpointsWithoutChangingRequest() {
        val first = target("http://NAS.example:80/dav", "Shows/%41%20B%2fC.mkv?token=draft")
        val second = target("https://nas.example:443/dav/", "Shows/A%20B%2FC.mkv/")
        assertEquals(first.mediaKey, second.mediaKey)
        assertEquals("token=draft", first.requestUrl.encodedQuery)
        assertFalse(first.mediaKey.contains("token"))
        assertFalse(first.mediaKey.contains("alice"))
        assertNotEquals(first.mediaKey, target("https://nas.example/dav", "Shows/A%20B/C.mkv").mediaKey)
    }

    @Test
    fun namespacesDifferAcrossHostAccountRootAndNonDefaultPorts() {
        val server = "https://nas.example/dav"
        val file = "Shows/E1.mkv"
        val original = target(server, file)
        assertNotEquals(original.mediaKey, target("https://other.example/dav", file).mediaKey)
        assertNotEquals(original.mediaKey, target("https://nas.example:8443/dav", file).mediaKey)
        assertNotEquals(original.mediaKey, target(server, file, user = "bob").mediaKey)
        assertNotEquals(original.mediaKey, target(server, file, mediaRoot = "Shows").mediaKey)
        assertEquals(WebDavPlaybackIdentity.legacyClaim(original), WebDavPlaybackIdentity.legacyClaim(target(server, file, user = "bob")))
    }

    @Test
    fun blocksCredentialsCrossOriginAndDirectoryBoundaryEscapes() {
        val server = checkNotNull(WebDavPlaybackIdentity.directory("https://nas.example/dav"))
        val root = WebDavPlaybackIdentity.mediaRoot(server, "Shows")
        listOf(
            "https://other.example/dav/Shows/E1.mkv", "http://nas.example/dav/Shows/E1.mkv",
            "https://alice:p@nas.example/dav/Shows/E1.mkv", "ShowsBackup/E1.mkv", "Shows%2fE1.mkv",
            "Shows/%2E%2E/E1.mkv", "Shows/test%5cE1.mkv"
        ).forEach { assertNull(it, WebDavPlaybackIdentity.target(server, root, "alice", it)) }
        assertEquals("E1.mkv", target("https://nas.example/dav", "Shows/E1.mkv", mediaRoot = "Shows").title)
    }

    @Test
    fun importsLegacyPositionAndNeverOverridesNewOrCompletedRecord() {
        val target = target("https://nas.example/dav", "Shows/E1.mkv")
        val old = "http://nas.example:80/dav/Shows/%451.mkv/"
        val unrelated = "https://other.example/dav/Shows/E1.mkv"
        assertTrue(WebDavPlaybackIdentity.isLegacyAlias(old, target))
        assertFalse(WebDavPlaybackIdentity.isLegacyAlias("$old?other=1", target))
        val positions = mapOf(old to 45_000L, unrelated to 88_000L)
        assertEquals(45_000L, chooseLegacyWebDavProgress(target, positions, emptyMap())?.positionMs)
        val completed = PlaybackProgress(positionMs = 90_000, durationMs = 90_000, completed = true, updatedAt = 10)
        val result = chooseLegacyWebDavProgress(target, positions, mapOf(old to completed))
        assertEquals(completed, result?.progress)
        assertNull(result?.positionMs)
        assertNull(chooseLegacyWebDavProgress(target, positions, mapOf(target.mediaKey to PlaybackProgress())))
    }

    @Test
    fun coldResumeUsesStablePathButRejectsQueryAndBoundaryChanges() {
        val config = checkNotNull(PlayerWebDavDirectory.Config.parse("https://nas.example/dav", "Shows", "alice", "pw", false))
        val original = target("https://nas.example/dav", "Shows/E1.mkv", mediaRoot = "Shows")
        val resume = checkNotNull(WebDavPlaybackIdentity.resume(original))
        assertEquals(original.mediaKey, resume.resolve(config)?.mediaKey)
        assertNull(resume.resolve(checkNotNull(PlayerWebDavDirectory.Config.parse("https://nas.example/dav", "Other", "alice", "pw", false))))
        val forged = WebDavPlaybackIdentity.Resume(original.mediaKey, "/dav/Shows/E1.mkv?token=bad")
        assertNull(forged.resolve(config))
    }

    private fun target(serverUrl: String, file: String, user: String = "alice", mediaRoot: String = ""):
        WebDavPlaybackIdentity.Target {
        val server = checkNotNull(WebDavPlaybackIdentity.directory(serverUrl))
        return checkNotNull(WebDavPlaybackIdentity.target(server, WebDavPlaybackIdentity.mediaRoot(server, mediaRoot), user, file))
    }
}
