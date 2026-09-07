package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/** Short deterministic PCM with silent preroll for EXP-003 sync comparison. */
public final class Exp3SyncStream implements AudioStream {
    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int DURATION_FRAMES = SAMPLE_RATE * 2;
    public static final int SILENT_PREROLL_FRAMES = SAMPLE_RATE / 10; // 100 ms

    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, BITS_PER_SAMPLE, CHANNELS, true, false);
    private static final byte[] SHARED_PCM = generatePcm();

    private int offset;
    private boolean closed;

    public static int warmUpSharedPcm() {
        return SHARED_PCM.length;
    }

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public synchronized ByteBuffer read(int capacity) {
        if (closed || offset >= SHARED_PCM.length) return null;
        var length = Math.min(capacity, SHARED_PCM.length - offset);
        var buffer = BufferUtils.createByteBuffer(length);
        buffer.put(SHARED_PCM, offset, length);
        buffer.flip();
        offset += length;
        return buffer;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private static byte[] generatePcm() {
        var bytes = new byte[DURATION_FRAMES * 2];
        var toneHz = 660.0;
        var amplitude = 0.10;

        for (var frame = SILENT_PREROLL_FRAMES; frame < DURATION_FRAMES; frame++) {
            var audibleFrame = frame - SILENT_PREROLL_FRAMES;
            var t = audibleFrame / (double) SAMPLE_RATE;
            var remaining = DURATION_FRAMES - frame;
            var fade = Math.min(1.0, Math.min(audibleFrame / 480.0, remaining / 480.0));
            var sample = (short) Math.round(Math.sin(2.0 * Math.PI * toneHz * t) * amplitude * fade * Short.MAX_VALUE);
            var byteIndex = frame * 2;
            bytes[byteIndex] = (byte) (sample & 0xFF);
            bytes[byteIndex + 1] = (byte) ((sample >>> 8) & 0xFF);
        }

        return bytes;
    }
}
