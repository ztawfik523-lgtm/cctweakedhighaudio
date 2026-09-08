package dev.ztawfik.cctweakedhighaudio.media;

import java.util.Objects;

/** Effective operator configuration plus non-configurable safety ceilings for finite media. */
public final class MediaLimits {
    public static final int KIB = 1024;
    public static final int MIB = 1024 * KIB;

    /** Prevents one finite item and its temporary copies from becoming an unreasonable heap allocation. */
    public static final int HARD_MAX_FILE_BYTES = 256 * MIB;
    /** Bounds the immediate CC:T argument copy made by one upload call. */
    public static final int HARD_MAX_UPLOAD_CHUNK_BYTES = 1024 * KIB;
    /** Bounds server-side incomplete-upload objects and their owner bookkeeping. */
    public static final int HARD_MAX_CONCURRENT_UPLOADS = 256;
    /** Prevents one computer/speaker pair from monopolising server upload state. */
    public static final int HARD_MAX_UPLOADS_PER_COMPUTER = 64;
    /** Prevents abandoned allocations from remaining reserved for pathologically long periods. */
    public static final int HARD_MAX_UPLOAD_TIMEOUT_SECONDS = 3600;
    /** Keeps either server aggregate/store pool below a multi-gigabyte Java heap commitment. */
    public static final int HARD_MAX_SERVER_MEMORY_BUDGET_BYTES = 1024 * MIB;
    /** Limits any one client cache/transfer/playback pool to a conservative fraction of a typical client heap. */
    public static final int HARD_MAX_CLIENT_MEMORY_BUDGET_BYTES = 512 * MIB;
    /**
     * Minecraft 1.21.1's clientbound custom-payload ceiling is 1 MiB. Leave 1 KiB for the payload id,
     * UUID, offset, and length metadata so a configured content chunk cannot approach the packet boundary.
     */
    public static final int HARD_MAX_NETWORK_CHUNK_BYTES = 1023 * KIB;
    /** Bounds client transfer objects and concurrent full-file allocations. */
    public static final int HARD_MAX_CLIENT_TRANSFERS = 64;
    /** Matches the proven total-preserving reservation of 16 Minecraft streaming sources for HighAudio. */
    public static final int HARD_MAX_CLIENT_PLAYBACKS = 16;
    /** Bounds server/client session maps even when many emitters are inside the audience. */
    public static final int HARD_MAX_SESSIONS = 4096;
    /** Prevents pathological player scans and broadcast fan-out from an extreme radius. */
    public static final int HARD_MAX_PLAYBACK_RADIUS_BLOCKS = 512;

    private static volatile Values current = defaults();

    private MediaLimits() {
    }

    public static Values current() {
        return current;
    }

    public static void install(Values values) {
        current = Objects.requireNonNull(values, "values");
    }

    public static Values defaults() {
        return new Values(
            64 * MIB,
            64 * KIB,
            4,
            64,
            60,
            256 * MIB,
            512 * MIB,
            32 * KIB,
            256 * MIB,
            128 * MIB,
            128 * MIB,
            8,
            8,
            256,
            64,
            256
        );
    }

    public record Values(
        int maxFileBytes,
        int uploadChunkBytes,
        int uploadsPerComputer,
        int concurrentServerUploads,
        int uploadTimeoutSeconds,
        int inFlightUploadMemoryBytes,
        int serverContentStoreBytes,
        int networkTransferChunkBytes,
        int clientContentCacheBytes,
        int clientTransferMemoryBytes,
        int clientPlaybackMemoryBytes,
        int concurrentClientTransfers,
        int concurrentClientPlaybacks,
        int serverSessionCap,
        int playbackRadiusBlocks,
        int clientSessionCap
    ) {
        public Values {
            requireRange("server.maxFiniteFileMiB", maxFileBytes, MIB, HARD_MAX_FILE_BYTES);
            requireRange("server.uploadChunkKiB", uploadChunkBytes, KIB, HARD_MAX_UPLOAD_CHUNK_BYTES);
            requireRange("server.uploadsPerComputer", uploadsPerComputer, 1, HARD_MAX_UPLOADS_PER_COMPUTER);
            requireRange("server.concurrentUploads", concurrentServerUploads, 1, HARD_MAX_CONCURRENT_UPLOADS);
            requireRange("server.uploadTimeoutSeconds", uploadTimeoutSeconds, 1, HARD_MAX_UPLOAD_TIMEOUT_SECONDS);
            requireRange("server.inFlightUploadMemoryMiB", inFlightUploadMemoryBytes, MIB, HARD_MAX_SERVER_MEMORY_BUDGET_BYTES);
            requireRange("server.contentStoreMiB", serverContentStoreBytes, MIB, HARD_MAX_SERVER_MEMORY_BUDGET_BYTES);
            requireRange("network.transferChunkKiB", networkTransferChunkBytes, KIB, HARD_MAX_NETWORK_CHUNK_BYTES);
            requireRange("client.contentCacheMiB", clientContentCacheBytes, MIB, HARD_MAX_CLIENT_MEMORY_BUDGET_BYTES);
            requireRange("client.transferMemoryMiB", clientTransferMemoryBytes, MIB, HARD_MAX_CLIENT_MEMORY_BUDGET_BYTES);
            requireRange("client.decodedPlaybackMemoryMiB", clientPlaybackMemoryBytes, MIB, HARD_MAX_CLIENT_MEMORY_BUDGET_BYTES);
            requireRange("client.concurrentTransfers", concurrentClientTransfers, 1, HARD_MAX_CLIENT_TRANSFERS);
            requireRange("client.concurrentPlaybacks", concurrentClientPlaybacks, 1, HARD_MAX_CLIENT_PLAYBACKS);
            requireRange("server.sessionCap", serverSessionCap, 1, HARD_MAX_SESSIONS);
            requireRange("server.playbackRadiusBlocks", playbackRadiusBlocks, 1, HARD_MAX_PLAYBACK_RADIUS_BLOCKS);
            requireRange("client.sessionCap", clientSessionCap, 1, HARD_MAX_SESSIONS);

            requireAtLeast("server.inFlightUploadMemoryMiB", inFlightUploadMemoryBytes, "server.maxFiniteFileMiB", maxFileBytes,
                "one maximum-size upload must fit in the aggregate in-flight upload budget");
            requireAtLeast("server.contentStoreMiB", serverContentStoreBytes, "server.maxFiniteFileMiB", maxFileBytes,
                "one supported file must fit in the server content store");
            requireAtLeast("client.transferMemoryMiB", clientTransferMemoryBytes, "server.maxFiniteFileMiB", maxFileBytes,
                "one maximum-size file must fit in the client transfer budget");
            requireAtLeast("client.contentCacheMiB", clientContentCacheBytes, "server.maxFiniteFileMiB", maxFileBytes,
                "one supported file must fit in the required client cache");
            requireAtLeast("client.decodedPlaybackMemoryMiB", clientPlaybackMemoryBytes, "server.maxFiniteFileMiB", maxFileBytes,
                "one maximum-size valid PCM WAV must fit in the decoded playback budget");
        }

        public long uploadTimeoutNanos() {
            return uploadTimeoutSeconds * 1_000_000_000L;
        }

        public UploadManager.Limits uploadLimits() {
            return new UploadManager.Limits(
                maxFileBytes,
                uploadChunkBytes,
                concurrentServerUploads,
                uploadsPerComputer,
                uploadTimeoutNanos(),
                inFlightUploadMemoryBytes
            );
        }

        public Values withInFlightUploadMemoryBytes(int value) {
            return copy(maxFileBytes, value, clientTransferMemoryBytes, clientContentCacheBytes, clientPlaybackMemoryBytes);
        }

        public Values withClientTransferMemoryBytes(int value) {
            return copy(maxFileBytes, inFlightUploadMemoryBytes, value, clientContentCacheBytes, clientPlaybackMemoryBytes);
        }

        public Values withServerContentStoreBytes(int value) {
            return new Values(maxFileBytes, uploadChunkBytes, uploadsPerComputer, concurrentServerUploads,
                uploadTimeoutSeconds, inFlightUploadMemoryBytes, value, networkTransferChunkBytes,
                clientContentCacheBytes, clientTransferMemoryBytes, clientPlaybackMemoryBytes, concurrentClientTransfers,
                concurrentClientPlaybacks, serverSessionCap, playbackRadiusBlocks, clientSessionCap);
        }

        public Values withClientContentCacheBytes(int value) {
            return copy(maxFileBytes, inFlightUploadMemoryBytes, clientTransferMemoryBytes, value, clientPlaybackMemoryBytes);
        }

        public Values withClientPlaybackMemoryBytes(int value) {
            return copy(maxFileBytes, inFlightUploadMemoryBytes, clientTransferMemoryBytes, clientContentCacheBytes, value);
        }

        private Values copy(int fileBytes, int uploadMemoryBytes, int transferMemoryBytes, int cacheBytes, int playbackMemoryBytes) {
            return new Values(fileBytes, uploadChunkBytes, uploadsPerComputer, concurrentServerUploads,
                uploadTimeoutSeconds, uploadMemoryBytes, serverContentStoreBytes, networkTransferChunkBytes,
                cacheBytes, transferMemoryBytes, playbackMemoryBytes, concurrentClientTransfers,
                concurrentClientPlaybacks, serverSessionCap, playbackRadiusBlocks, clientSessionCap);
        }
    }

    private static void requireRange(String setting, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException("Invalid HighAudio configuration: " + setting + "=" + value
                + " is outside the supported range " + minimum + ".." + maximum);
        }
    }

    private static void requireAtLeast(
        String largerSetting,
        int largerBytes,
        String smallerSetting,
        int smallerBytes,
        String reason
    ) {
        if (largerBytes < smallerBytes) {
            throw new IllegalArgumentException("Invalid HighAudio configuration: " + largerSetting + "="
                + toMiB(largerBytes) + " MiB must be >= " + smallerSetting + "=" + toMiB(smallerBytes)
                + " MiB; " + reason);
        }
    }

    private static int toMiB(int bytes) {
        return bytes / MIB;
    }
}
