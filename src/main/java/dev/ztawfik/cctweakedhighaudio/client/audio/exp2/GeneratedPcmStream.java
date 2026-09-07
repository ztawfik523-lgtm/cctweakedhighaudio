package dev.ztawfik.cctweakedhighaudio.client.audio.exp2;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/**
 * Finite deterministic PCM used only by EXP-002.
 *
 * <p>This deliberately contains no codec, file IO, network, cache, or session logic. It exists only to prove
 * that arbitrary HighAudio-owned PCM can flow through Minecraft's normal SoundManager/AudioStream path.</p>
 */
public final class GeneratedPcmStream implements AudioStream {
    public static final int SAMPLE_RATE = 48_000;
    public static final int CHANNELS = 1;
    public static final int BITS_PER_SAMPLE = 16;
    public static final int DURATION_FRAMES = SAMPLE_RATE * 2;

    private static final AudioFormat FORMAT = new AudioFormat(SAMPLE_RATE, BITS_PER_SAMPLE, CHANNELS, true, false);
    private static final double START_HZ = 220.0;
    private static final double END_HZ = 880.0;
    private static final double AMPLITUDE = 0.30;

    private final byte[] pcm = generateChirp();
    private int offset;
    private boolean closed;

    @Override
    public AudioFormat getFormat() {
        return FORMAT;
    }

    @Override
    public synchronized ByteBuffer read(int capacity) {
        if (closed || offset >= pcm.length) return null;

        var length = Math.min(capacity, pcm.length - offset);
        var buffer = BufferUtils.createByteBuffer(length);
        buffer.put(pcm, offset, length);
        buffer.flip();
        offset += length;
        return buffer;
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    public synchronized int bytesRead() {
        return offset;
    }

    public int totalBytes() {
        return pcm.length;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    private static byte[] generateChirp() {
        var bytes = new byte[DURATION_FRAMES * 2];
        var durationSeconds = DURATION_FRAMES / (double) SAMPLE_RATE;

        for (var frame = 0; frame < DURATION_FRAMES; frame++) {
            var t = frame / (double) SAMPLE_RATE;
            var progress = t / durationSeconds;

            // Linear-frequency chirp with a Hann-like amplitude envelope to avoid hard clicks at either end.
            var phase = 2.0 * Math.PI * (START_HZ * t + 0.5 * (END_HZ - START_HZ) * t * progress);
            var envelope = Math.sin(Math.PI * progress);
            envelope *= envelope;

            var sample = (short) Math.round(Math.sin(phase) * envelope * AMPLITUDE * Short.MAX_VALUE);
            var byteIndex = frame * 2;
            bytes[byteIndex] = (byte) (sample & 0xFF);
            bytes[byteIndex + 1] = (byte) ((sample >>> 8) & 0xFF);
        }

        return bytes;
    }
}
