package dev.ztawfik.cctweakedhighaudio.media;

import javax.sound.sampled.AudioFormat;
import java.util.Arrays;

/** Strict RIFF/WAVE PCM decoder for positional mono 8-bit unsigned or 16-bit signed little-endian audio. */
public final class WavPcmDecoder {
    public record DecodedPcm(AudioFormat format, byte[] bytes) {
    }

    private WavPcmDecoder() {
    }

    public static DecodedPcm decode(byte[] wav) throws WavFormatException {
        var maximumBytes = MediaLimits.current().maxFileBytes();
        if (wav.length > maximumBytes) {
            throw new WavFormatException("WAV size " + wav.length + " bytes exceeds the configured maximum of "
                + maximumBytes + " bytes");
        }
        if (wav.length < 12 || !tag(wav, 0, "RIFF") || !tag(wav, 8, "WAVE")) {
            throw new WavFormatException("Malformed WAV: expected RIFF/WAVE header");
        }

        var riffSize = unsignedInt(wav, 4);
        if (riffSize + 8L > wav.length) throw new WavFormatException("Malformed WAV: truncated RIFF container");
        var riffEnd = (int) (riffSize + 8L);

        Format format = null;
        var dataOffset = -1;
        var dataLength = -1;
        var cursor = 12;
        while (cursor <= riffEnd - 8) {
            var chunkLengthLong = unsignedInt(wav, cursor + 4);
            if (chunkLengthLong > Integer.MAX_VALUE) throw new WavFormatException("Malformed WAV: chunk is too large");
            var chunkLength = (int) chunkLengthLong;
            var body = cursor + 8;
            if (body > riffEnd - chunkLength) throw new WavFormatException("Malformed WAV: truncated chunk");

            if (tag(wav, cursor, "fmt ")) format = parseFormat(wav, body, chunkLength);
            if (tag(wav, cursor, "data") && dataOffset < 0) {
                dataOffset = body;
                dataLength = chunkLength;
            }

            var paddedLength = chunkLength + (chunkLength & 1);
            if (body > riffEnd - paddedLength) {
                if (body + chunkLength == riffEnd) cursor = riffEnd;
                else throw new WavFormatException("Malformed WAV: missing chunk padding");
            } else {
                cursor = body + paddedLength;
            }
        }

        if (format == null) throw new WavFormatException("Malformed WAV: missing fmt chunk");
        if (dataOffset < 0 || dataLength <= 0) throw new WavFormatException("Malformed WAV: missing or empty data chunk");
        if (dataLength % format.blockAlign != 0) throw new WavFormatException("Malformed WAV: PCM data is not frame-aligned");

        var audioFormat = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            format.sampleRate,
            format.bitsPerSample,
            format.channels,
            format.blockAlign,
            format.sampleRate,
            false
        );
        if (format.bitsPerSample == 8) {
            audioFormat = new AudioFormat(
                AudioFormat.Encoding.PCM_UNSIGNED,
                format.sampleRate,
                8,
                format.channels,
                format.blockAlign,
                format.sampleRate,
                false
            );
        }
        return new DecodedPcm(audioFormat, Arrays.copyOfRange(wav, dataOffset, dataOffset + dataLength));
    }

    private static Format parseFormat(byte[] wav, int offset, int length) throws WavFormatException {
        if (length < 16) throw new WavFormatException("Malformed WAV: fmt chunk is shorter than 16 bytes");
        var encoding = unsignedShort(wav, offset);
        var channels = unsignedShort(wav, offset + 2);
        var sampleRateLong = unsignedInt(wav, offset + 4);
        var byteRateLong = unsignedInt(wav, offset + 8);
        var blockAlign = unsignedShort(wav, offset + 12);
        var bitsPerSample = unsignedShort(wav, offset + 14);

        if (encoding != 1) throw new WavFormatException("Unsupported WAV encoding: only integer PCM format 1 is supported");
        if (channels != 1) throw new WavFormatException("Unsupported WAV channel count: positional M4 playback requires mono");
        if (sampleRateLong < 8_000 || sampleRateLong > 48_000) {
            throw new WavFormatException("Unsupported WAV sample rate: expected 8000-48000 Hz");
        }
        if (bitsPerSample != 8 && bitsPerSample != 16) {
            throw new WavFormatException("Unsupported WAV sample depth: expected 8-bit or 16-bit PCM");
        }
        var expectedBlockAlign = channels * (bitsPerSample / 8);
        if (blockAlign != expectedBlockAlign) throw new WavFormatException("Malformed WAV: invalid block alignment");
        if (byteRateLong != sampleRateLong * blockAlign) throw new WavFormatException("Malformed WAV: invalid byte rate");

        return new Format(channels, (int) sampleRateLong, bitsPerSample, blockAlign);
    }

    private static boolean tag(byte[] bytes, int offset, String tag) {
        if (offset < 0 || offset > bytes.length - 4) return false;
        return bytes[offset] == tag.charAt(0)
            && bytes[offset + 1] == tag.charAt(1)
            && bytes[offset + 2] == tag.charAt(2)
            && bytes[offset + 3] == tag.charAt(3);
    }

    private static int unsignedShort(byte[] bytes, int offset) {
        return (bytes[offset] & 0xFF) | ((bytes[offset + 1] & 0xFF) << 8);
    }

    private static long unsignedInt(byte[] bytes, int offset) {
        return Integer.toUnsignedLong(
            (bytes[offset] & 0xFF)
                | ((bytes[offset + 1] & 0xFF) << 8)
                | ((bytes[offset + 2] & 0xFF) << 16)
                | ((bytes[offset + 3] & 0xFF) << 24)
        );
    }

    private record Format(int channels, int sampleRate, int bitsPerSample, int blockAlign) {
    }
}
