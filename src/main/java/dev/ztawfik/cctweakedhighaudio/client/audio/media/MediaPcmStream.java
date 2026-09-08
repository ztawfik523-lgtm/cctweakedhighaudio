package dev.ztawfik.cctweakedhighaudio.client.audio.media;

import net.minecraft.client.sounds.AudioStream;
import org.lwjgl.BufferUtils;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

/** Finite decoded PCM supplied to a Minecraft-owned streaming channel. */
public final class MediaPcmStream implements AudioStream {
    private final AudioFormat format;
    private final byte[] pcm;
    private int offset;
    private boolean closed;

    public MediaPcmStream(AudioFormat format, byte[] pcm) {
        this.format = format;
        // The decoder creates this array solely for the stream; taking ownership avoids doubling the configured
        // decoded-playback allocation while the audio is active.
        this.pcm = pcm;
    }

    @Override
    public AudioFormat getFormat() {
        return format;
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

    public int totalBytes() {
        return pcm.length;
    }
}
