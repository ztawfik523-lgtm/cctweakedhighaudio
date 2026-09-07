package dev.ztawfik.cctweakedhighaudio.media;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UploadManagerTest {
    private final AtomicLong clock = new AtomicLong();
    private final ContentStore store = new ContentStore(1024);
    private final UploadManager uploads = new UploadManager(store, clock::get);
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
    }

    @Test
    void incompleteFinishFailsAndCleansSession() throws Exception {
        var upload = uploads.begin(owner, 5);
        uploads.write(owner, upload, new byte[]{1, 2});
        var error = assertThrows(UploadException.class, () -> uploads.finish(owner, upload));
        assertTrue(error.getMessage().contains("received 2 of 5"));
        assertEquals(0, uploads.incompleteCount());
    }

    @Test
    void oversizedChunkAbortsUpload() throws Exception {
        var upload = uploads.begin(owner, MediaLimits.MAX_FILE_BYTES);
        var error = assertThrows(
            UploadException.class,
            () -> uploads.write(owner, upload, new byte[MediaLimits.MAX_UPLOAD_CHUNK_BYTES + 1])
        );
        assertTrue(error.getMessage().contains("aborted"));
        assertEquals(0, uploads.incompleteCount());
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
    void expiresIncompleteUploadsAfterThirtySeconds() throws Exception {
        uploads.begin(owner, 1);
        clock.set(MediaLimits.UPLOAD_TIMEOUT_NANOS);
        assertEquals(1, uploads.expire());
        assertEquals(0, uploads.incompleteCount());
    }

    @Test
    void disconnectCleanupOnlyRemovesOwnedUploads() throws Exception {
        var other = new UploadManager.Owner(UUID.randomUUID(), 8);
        uploads.begin(owner, 1);
        uploads.begin(other, 1);
        assertEquals(1, uploads.abortOwner(owner));
        assertEquals(1, uploads.incompleteCount());
    }

    @Test
    void enforcesFileAndServerWideConcurrencyLimits() throws Exception {
        assertThrows(UploadException.class, () -> uploads.begin(owner, MediaLimits.MAX_FILE_BYTES + 1));
        for (var index = 0; index < MediaLimits.MAX_CONCURRENT_UPLOADS; index++) {
            uploads.begin(new UploadManager.Owner(UUID.randomUUID(), index), 1);
        }
        assertThrows(
            UploadException.class,
            () -> uploads.begin(new UploadManager.Owner(UUID.randomUUID(), 99), 1)
        );
        assertEquals(MediaLimits.MAX_CONCURRENT_UPLOADS, uploads.incompleteCount());
    }
}
