package dev.ztawfik.cctweakedhighaudio.media;

import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavPcmDecoderTest {
    @Test
    void decodesFiniteSixteenBitMonoPcm() throws Exception {
        var pcm = new byte[]{0, 0, 1, 0, -1, -1, 0, 0};
        var decoded = WavPcmDecoder.decode(wav(1, 8_000, 16, pcm));

        assertEquals(AudioFormat.Encoding.PCM_SIGNED, decoded.format().getEncoding());
        assertEquals(8_000.0f, decoded.format().getSampleRate());
        assertEquals(1, decoded.format().getChannels());
        assertEquals(16, decoded.format().getSampleSizeInBits());
        assertArrayEquals(pcm, decoded.bytes());
    }

    @Test
    void decodesEightBitMonoAsUnsigned() throws Exception {
        var decoded = WavPcmDecoder.decode(wav(1, 48_000, 8, new byte[]{0, -1, 10, 20}));
        assertEquals(AudioFormat.Encoding.PCM_UNSIGNED, decoded.format().getEncoding());
        assertEquals(1, decoded.format().getChannels());
    }

    @Test
    void rejectsStereoBecauseThePlacedEmitterMustRemainPositional() throws Exception {
        var error = assertThrows(
            WavFormatException.class,
            () -> WavPcmDecoder.decode(wav(2, 48_000, 16, new byte[]{0, 0, 0, 0}))
        );
        assertTrue(error.getMessage().contains("requires mono"));
    }

    @Test
    void rejectsUnsupportedEncodingAndMissingData() throws Exception {
        var floatingPoint = wav(1, 8_000, 16, new byte[]{0, 0});
        floatingPoint[20] = 3;
        assertTrue(assertThrows(WavFormatException.class, () -> WavPcmDecoder.decode(floatingPoint)).getMessage().contains("encoding"));

        var headerOnly = new byte[]{'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'A', 'V', 'E'};
        assertTrue(assertThrows(WavFormatException.class, () -> WavPcmDecoder.decode(headerOnly)).getMessage().contains("fmt"));
    }

    @Test
    void rejectsTruncatedAndMisalignedPcm() throws Exception {
        var truncated = wav(1, 8_000, 16, new byte[]{0, 0});
        truncated[40] = 10;
        assertThrows(WavFormatException.class, () -> WavPcmDecoder.decode(truncated));
        assertThrows(WavFormatException.class, () -> WavPcmDecoder.decode(wav(1, 8_000, 16, new byte[]{0})));
    }

    private static byte[] wav(int channels, int sampleRate, int bits, byte[] pcm) throws IOException {
        var output = new ByteArrayOutputStream();
        var blockAlign = channels * bits / 8;
        writeTag(output, "RIFF");
        writeInt(output, 36 + pcm.length);
        writeTag(output, "WAVE");
        writeTag(output, "fmt ");
        writeInt(output, 16);
        writeShort(output, 1);
        writeShort(output, channels);
        writeInt(output, sampleRate);
        writeInt(output, sampleRate * blockAlign);
        writeShort(output, blockAlign);
        writeShort(output, bits);
        writeTag(output, "data");
        writeInt(output, pcm.length);
        output.write(pcm);
        return output.toByteArray();
    }

    private static void writeTag(ByteArrayOutputStream output, String value) {
        for (var i = 0; i < value.length(); i++) output.write(value.charAt(i));
    }

    private static void writeShort(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
    }

    private static void writeInt(ByteArrayOutputStream output, int value) {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }
}
