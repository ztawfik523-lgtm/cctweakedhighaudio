package dev.ztawfik.cctweakedhighaudio.media;

/** Clear rejection reason for a WAV file outside the initial PCM subset. */
public final class WavFormatException extends Exception {
    public WavFormatException(String message) {
        super(message);
    }
}
