package dev.ztawfik.cctweakedhighaudio.media;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadManagerTest {
    private static final UploadManager.Limits LIMITS = new UploadManager.Limits(8, 4, 3, 2, 30, 10);
    private final AtomicLong clock = new AtomicLong();
    private final ContentStore store = new ContentStore(1024);
    private final UploadManager uploads = new UploadManager(store, clock::get, LIMITS);
    private final UploadManager.Owner owner = new UploadManager.Owner(UUID.randomUUID(), 7);

    @Test
    void completesExactUploadAndStoresByContentId() throws Exception {
        var upload = uploads.begin(owner, 5);
        assertEquals(2, uploads.write(owner, upload, new byte[]{1, 2}));
        assertEquals(5, uploads.write(owner, upload, new byte[]{3, 4, 5}));
        var contentId = uploads.finish(owner, upload);

        assertEquals(ContentId.sha256(new byte[]{1, 2, 3, 4, 5}), contentId);
        assertTrue(store.get(contentId).isPresent());
        assertEquals(0, uploads.incompleteCount());
        assertEquals(0, uploads.reservedBytes());
    }

    @Test
    void incompleteFinishFailsAndCleansSession() throws Exception {
        var upload = uploads.begin(owner, 5);
        uploads.write(owner, upload, new byte[]{1, 2});
        var error = assertThrows(UploadException.class, () -> uploads.finish(owner, upload));
        assertTrue(error.getMessage().contains("received 2 of 5"));
        assertEquals(0, uploads.incompleteCount());
        assertEquals(0, uploads.reservedBytes());
    }

    @Test
    void oversizedChunkAbortsUpload() throws Exception {
        var upload = uploads.begin(owner, LIMITS.maxFileBytes());
        var error = assertThrows(
            UploadException.class,
            () -> uploads.write(owner, upload, new byte[LIMITS.maxChunkBytes() + 1])
        );
        assertTrue(error.getMessage().contains("aborted"));
        assertEquals(0, uploads.incompleteCount());
        assertEquals(0, uploads.reservedBytes());
    }

    @Test
    void enforcesPerComputerConcurrencyAndAbort() throws Exception {
        var first = uploads.begin(owner, 1);
        uploads.begin(owner, 1);
        assertThrows(UploadException.class, () -> uploads.begin(owner, 1));
        assertTrue(uploads.abort(owner, first));
        assertFalse(uploads.abort(owner, first));
    }

    @Test
    void expiresIncompleteUploadsAfterConfiguredTimeout() throws Exception {
        uploads.begin(owner, 1);
        clock.set(LIMITS.timeoutNanos());
        assertEquals(1, uploads.expire());
        assertEquals(0, uploads.incompleteCount());
        assertEquals(0, uploads.reservedBytes());
    }

    @Test
    void disconnectCleanupOnlyRemovesOwnedUploads() throws Exception {
        var other = new UploadManager.Owner(UUID.randomUUID(), 8);
        uploads.begin(owner, 1);
        uploads.begin(other, 1);
        assertEquals(1, uploads.abortOwner(owner));
        assertEquals(1, uploads.incompleteCount());
        assertEquals(1, uploads.reservedBytes());
    }

    @Test
    void enforcesFileAndServerWideConcurrencyLimits() throws Exception {
        assertThrows(UploadException.class, () -> uploads.begin(owner, LIMITS.maxFileBytes() + 1));
        for (var index = 0; index < LIMITS.maxConcurrentUploads(); index++) {
            uploads.begin(new UploadManager.Owner(UUID.randomUUID(), index), 1);
        }
        assertThrows(
            UploadException.class,
            () -> uploads.begin(new UploadManager.Owner(UUID.randomUUID(), 99), 1)
        );
        assertEquals(LIMITS.maxConcurrentUploads(), uploads.incompleteCount());
    }

    @Test
    void reservesBeforeAllocationAndReleasesEveryCleanupPath() throws Exception {
        var first = uploads.begin(owner, 6);
        assertEquals(6, uploads.reservedBytes());
        assertTrue(assertThrows(UploadException.class, () -> uploads.begin(new UploadManager.Owner(UUID.randomUUID(), 8), 5))
            .getMessage().contains("only 4 bytes available"));

        assertTrue(uploads.abort(owner, first));
        assertEquals(0, uploads.reservedBytes());

        uploads.begin(owner, 4);
        uploads.clear();
        assertEquals(0, uploads.reservedBytes());
    }
}
