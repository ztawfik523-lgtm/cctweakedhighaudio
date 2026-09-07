package dev.ztawfik.cctweakedhighaudio.media;

/** User-facing validation failure for a finite upload session. */
public final class UploadException extends Exception {
    public UploadException(String message) {
        super(message);
    }
}
