package dev.ztawfik.cctweakedhighaudio.media;

/** Fixed conservative resource limits for the MILESTONE-004 finite-media path. */
public final class MediaLimits {
    public static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
    public static final int MAX_UPLOAD_CHUNK_BYTES = 16 * 1024;
    public static final int MAX_CONCURRENT_UPLOADS = 16;
    public static final int MAX_UPLOADS_PER_COMPUTER = 2;
    public static final long UPLOAD_TIMEOUT_NANOS = 30_000_000_000L;

    public static final int SERVER_CONTENT_STORE_BYTES = 32 * 1024 * 1024;
    public static final int MAX_SERVER_SESSIONS = 256;
    public static final int TRANSFER_CHUNK_BYTES = 32 * 1024;
    public static final int CLIENT_CONTENT_CACHE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_CLIENT_TRANSFERS = 4;
    public static final int MAX_CLIENT_TRANSFER_BYTES = 8 * 1024 * 1024;
    public static final int MAX_CLIENT_SESSIONS = 64;
    public static final int MAX_CLIENT_PLAYBACKS = 4;
    public static final int MAX_CLIENT_PLAYBACK_BYTES = 8 * 1024 * 1024;

    public static final double PLAYBACK_BROADCAST_RADIUS = 64.0;

    private MediaLimits() {
    }
}
