package dev.ztawfik.cctweakedhighaudio.media;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

/** Owns bounded incomplete uploads and commits complete files into a {@link ContentStore}. */
public final class UploadManager {
    public record Limits(
        int maxFileBytes,
        int maxChunkBytes,
        int maxConcurrentUploads,
        int maxUploadsPerOwner,
        long timeoutNanos,
        long maxReservedBytes
    ) {
        public Limits {
            if (maxFileBytes <= 0 || maxChunkBytes <= 0 || maxConcurrentUploads <= 0
                || maxUploadsPerOwner <= 0 || timeoutNanos <= 0 || maxReservedBytes < maxFileBytes) {
                throw new IllegalArgumentException("Invalid upload limits");
            }
        }
    }

    public record Owner(UUID speakerId, int computerId) {
    }

    private final ContentStore store;
    private final LongSupplier clock;
    private final Limits limits;
    private final Map<UUID, Upload> uploads = new HashMap<>();
    private long reservedBytes;

    public UploadManager(ContentStore store, LongSupplier clock, Limits limits) {
        this.store = store;
        this.clock = clock;
        this.limits = limits;
    }

    public synchronized UUID begin(Owner owner, int expectedBytes) throws UploadException {
        expire();
        if (expectedBytes <= 0) throw new UploadException("Upload size must be positive");
        if (expectedBytes > limits.maxFileBytes) {
            throw new UploadException("Upload size " + expectedBytes + " bytes exceeds the configured maximum of "
                + limits.maxFileBytes + " bytes");
        }
        if (uploads.size() >= limits.maxConcurrentUploads) {
            throw new UploadException("Server already has the configured maximum of " + limits.maxConcurrentUploads
                + " incomplete uploads");
        }
        var ownedCount = uploads.values().stream().filter(upload -> upload.owner.equals(owner)).count();
        if (ownedCount >= limits.maxUploadsPerOwner) {
            throw new UploadException("This computer/speaker already has the configured maximum of "
                + limits.maxUploadsPerOwner + " incomplete uploads");
        }
        if (expectedBytes > limits.maxReservedBytes - reservedBytes) {
            throw new UploadException("Upload needs " + expectedBytes + " bytes but the configured aggregate in-flight "
                + "upload memory budget has only " + (limits.maxReservedBytes - reservedBytes) + " bytes available");
        }

        UUID id;
        do {
            id = UUID.randomUUID();
        } while (uploads.containsKey(id));
        var upload = new Upload(owner, expectedBytes, clock.getAsLong());
        uploads.put(id, upload);
        reservedBytes += expectedBytes;
        return id;
    }

    public synchronized int write(Owner owner, UUID uploadId, byte[] chunk) throws UploadException {
        expire();
        var upload = requireOwned(owner, uploadId);
        if (chunk.length == 0) throw fail(uploadId, "Upload chunks must not be empty");
        if (chunk.length > limits.maxChunkBytes) {
            throw fail(uploadId, "Upload chunk size " + chunk.length + " bytes exceeds the configured maximum of "
                + limits.maxChunkBytes + " bytes");
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
        remove(uploadId);
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
        remove(uploadId);
        return true;
    }

    public synchronized int abortOwner(Owner owner) {
        var removed = 0;
        var iterator = uploads.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (!entry.getValue().owner.equals(owner)) continue;
            reservedBytes -= entry.getValue().bytes.length;
            iterator.remove();
            removed++;
        }
        return removed;
    }

    public synchronized int expire() {
        var cutoff = clock.getAsLong() - limits.timeoutNanos;
        var removed = 0;
        var iterator = uploads.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getValue().lastTouchedNanos > cutoff) continue;
            reservedBytes -= entry.getValue().bytes.length;
            iterator.remove();
            removed++;
        }
        return removed;
    }

    public synchronized int incompleteCount() {
        return uploads.size();
    }

    public synchronized long reservedBytes() {
        return reservedBytes;
    }

    public synchronized void clear() {
        uploads.clear();
        reservedBytes = 0;
    }

    private Upload requireOwned(Owner owner, UUID uploadId) throws UploadException {
        var upload = uploads.get(uploadId);
        if (upload == null) throw new UploadException("Upload is unavailable, expired, aborted, or already finished");
        if (!upload.owner.equals(owner)) throw new UploadException("Upload belongs to a different computer or speaker");
        return upload;
    }

    private UploadException fail(UUID uploadId, String message) {
        remove(uploadId);
        return new UploadException(message + "; the upload was aborted");
    }

    private Upload remove(UUID uploadId) {
        var removed = uploads.remove(uploadId);
        if (removed != null) reservedBytes -= removed.bytes.length;
        return removed;
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
