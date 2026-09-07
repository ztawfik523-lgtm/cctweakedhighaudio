package dev.ztawfik.cctweakedhighaudio.media;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Owns bounded incomplete uploads and commits complete files into a {@link ContentStore}. */
public final class UploadManager {
    public record Owner(UUID speakerId, int computerId) {
    }

    private final ContentStore store;
    private final LongSupplier clock;
    private final Map<UUID, Upload> uploads = new HashMap<>();

    public UploadManager(ContentStore store, LongSupplier clock) {
        this.store = store;
        this.clock = clock;
    }

    public synchronized UUID begin(Owner owner, int expectedBytes) throws UploadException {
        expire();
        if (expectedBytes <= 0) throw new UploadException("Upload size must be positive");
        if (expectedBytes > MediaLimits.MAX_FILE_BYTES) {
            throw new UploadException("Upload exceeds the 2 MiB maximum file size");
        }
        if (uploads.size() >= MediaLimits.MAX_CONCURRENT_UPLOADS) {
            throw new UploadException("Server already has the maximum of 16 incomplete uploads");
        }
        var ownedCount = uploads.values().stream().filter(upload -> upload.owner.equals(owner)).count();
        if (ownedCount >= MediaLimits.MAX_UPLOADS_PER_COMPUTER) {
            throw new UploadException("This computer already has the maximum of 2 incomplete uploads for this speaker");
        }

        UUID id;
        do {
            id = UUID.randomUUID();
        } while (uploads.containsKey(id));
        uploads.put(id, new Upload(owner, expectedBytes, clock.getAsLong()));
        return id;
    }

    public synchronized int write(Owner owner, UUID uploadId, byte[] chunk) throws UploadException {
        expire();
        var upload = requireOwned(owner, uploadId);
        if (chunk.length == 0) throw fail(uploadId, "Upload chunks must not be empty");
        if (chunk.length > MediaLimits.MAX_UPLOAD_CHUNK_BYTES) {
            throw fail(uploadId, "Upload chunk exceeds the 16 KiB limit");
        }
        if (chunk.length > upload.bytes.length - upload.offset) {
            throw fail(uploadId, "Upload contains more bytes than declared at begin");
        }

        System.arraycopy(chunk, 0, upload.bytes, upload.offset, chunk.length);
        upload.offset += chunk.length;
        upload.lastTouchedNanos = clock.getAsLong();
        return upload.offset;
    }

    public synchronized ContentId finish(Owner owner, UUID uploadId) throws UploadException {
        expire();
        var upload = requireOwned(owner, uploadId);
        uploads.remove(uploadId);
        if (upload.offset != upload.bytes.length) {
            throw new UploadException("Upload is incomplete: received " + upload.offset + " of " + upload.bytes.length + " bytes");
        }
        return store.put(upload.bytes);
    }

    public synchronized boolean abort(Owner owner, UUID uploadId) throws UploadException {
        expire();
        var upload = uploads.get(uploadId);
        if (upload == null) return false;
        if (!upload.owner.equals(owner)) throw new UploadException("Upload belongs to a different computer or speaker");
        uploads.remove(uploadId);
        return true;
    }

    public synchronized int abortOwner(Owner owner) {
        var before = uploads.size();
        uploads.entrySet().removeIf(entry -> entry.getValue().owner.equals(owner));
        return before - uploads.size();
    }

    public synchronized int expire() {
        var cutoff = clock.getAsLong() - MediaLimits.UPLOAD_TIMEOUT_NANOS;
        var before = uploads.size();
        uploads.entrySet().removeIf(entry -> entry.getValue().lastTouchedNanos <= cutoff);
        return before - uploads.size();
    }

    public synchronized int incompleteCount() {
        return uploads.size();
    }

    public synchronized void clear() {
        uploads.clear();
    }

    private Upload requireOwned(Owner owner, UUID uploadId) throws UploadException {
        var upload = uploads.get(uploadId);
        if (upload == null) throw new UploadException("Upload is unavailable, expired, aborted, or already finished");
        if (!upload.owner.equals(owner)) throw new UploadException("Upload belongs to a different computer or speaker");
        return upload;
    }

    private UploadException fail(UUID uploadId, String message) {
        uploads.remove(uploadId);
        return new UploadException(message + "; the upload was aborted");
    }

    private static final class Upload {
        private final Owner owner;
        private final byte[] bytes;
        private int offset;
        private long lastTouchedNanos;

        private Upload(Owner owner, int expectedBytes, long nowNanos) {
            this.owner = owner;
            bytes = new byte[expectedBytes];
            lastTouchedNanos = nowNanos;
        }
    }
}
