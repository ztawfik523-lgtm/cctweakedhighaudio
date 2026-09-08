package dev.ztawfik.cctweakedhighaudio.media;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MediaLimitsTest {
    @Test
    void defaultsMatchTheDocumentedOperationalPolicy() {
        var limits = MediaLimits.defaults();
        assertEquals(64 * MediaLimits.MIB, limits.maxFileBytes());
        assertEquals(64 * MediaLimits.KIB, limits.uploadChunkBytes());
        assertEquals(4, limits.uploadsPerComputer());
        assertEquals(64, limits.concurrentServerUploads());
        assertEquals(60, limits.uploadTimeoutSeconds());
        assertEquals(256 * MediaLimits.MIB, limits.inFlightUploadMemoryBytes());
        assertEquals(512 * MediaLimits.MIB, limits.serverContentStoreBytes());
        assertEquals(32 * MediaLimits.KIB, limits.networkTransferChunkBytes());
        assertEquals(256 * MediaLimits.MIB, limits.clientContentCacheBytes());
        assertEquals(128 * MediaLimits.MIB, limits.clientTransferMemoryBytes());
        assertEquals(128 * MediaLimits.MIB, limits.clientPlaybackMemoryBytes());
        assertEquals(8, limits.concurrentClientTransfers());
        assertEquals(8, limits.concurrentClientPlaybacks());
        assertEquals(256, limits.serverSessionCap());
        assertEquals(64, limits.playbackRadiusBlocks());
        assertEquals(256, limits.clientSessionCap());
    }

    @Test
    void rejectsEveryImpossibleMaximumFileRelationshipWithNamedValues() {
        assertRelationshipFailure(
            () -> MediaLimits.defaults().withInFlightUploadMemoryBytes(32 * MediaLimits.MIB),
            "server.inFlightUploadMemoryMiB=32 MiB", "server.maxFiniteFileMiB=64 MiB");
        assertRelationshipFailure(
            () -> MediaLimits.defaults().withServerContentStoreBytes(32 * MediaLimits.MIB),
            "server.contentStoreMiB=32 MiB", "server.maxFiniteFileMiB=64 MiB");
        assertRelationshipFailure(
            () -> MediaLimits.defaults().withClientTransferMemoryBytes(32 * MediaLimits.MIB),
            "client.transferMemoryMiB=32 MiB", "server.maxFiniteFileMiB=64 MiB");
        assertRelationshipFailure(
            () -> MediaLimits.defaults().withClientContentCacheBytes(32 * MediaLimits.MIB),
            "client.contentCacheMiB=32 MiB", "server.maxFiniteFileMiB=64 MiB");
        assertRelationshipFailure(
            () -> MediaLimits.defaults().withClientPlaybackMemoryBytes(32 * MediaLimits.MIB),
            "client.decodedPlaybackMemoryMiB=32 MiB", "server.maxFiniteFileMiB=64 MiB");
    }

    @Test
    void rejectsScalarValuesBeyondInternalSafetyCeilings() {
        var defaults = MediaLimits.defaults();
        var error = assertThrows(IllegalArgumentException.class, () -> new MediaLimits.Values(
            defaults.maxFileBytes(),
            MediaLimits.HARD_MAX_UPLOAD_CHUNK_BYTES + 1,
            defaults.uploadsPerComputer(),
            defaults.concurrentServerUploads(),
            defaults.uploadTimeoutSeconds(),
            defaults.inFlightUploadMemoryBytes(),
            defaults.serverContentStoreBytes(),
            defaults.networkTransferChunkBytes(),
            defaults.clientContentCacheBytes(),
            defaults.clientTransferMemoryBytes(),
            defaults.clientPlaybackMemoryBytes(),
            defaults.concurrentClientTransfers(),
            defaults.concurrentClientPlaybacks(),
            defaults.serverSessionCap(),
            defaults.playbackRadiusBlocks(),
            defaults.clientSessionCap()
        ));
        assertTrue(error.getMessage().contains("server.uploadChunkKiB"));
    }

    private static void assertRelationshipFailure(
        org.junit.jupiter.api.function.Executable executable,
        String first,
        String second
    ) {
        var error = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(error.getMessage().contains(first));
        assertTrue(error.getMessage().contains(second));
    }
}
