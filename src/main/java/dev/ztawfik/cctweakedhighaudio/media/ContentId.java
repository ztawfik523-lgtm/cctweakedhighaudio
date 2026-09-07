package dev.ztawfik.cctweakedhighaudio.media;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** SHA-256 identity of the original uploaded file bytes. */
public record ContentId(String value) {
    private static final int HEX_LENGTH = 64;

    public ContentId {
        if (value == null || value.length() != HEX_LENGTH) {
            throw new IllegalArgumentException("ContentId must be exactly 64 hexadecimal characters");
        }
        value = value.toLowerCase(Locale.ROOT);
        for (var i = 0; i < value.length(); i++) {
            var character = value.charAt(i);
            if (!((character >= '0' && character <= '9') || (character >= 'a' && character <= 'f'))) {
                throw new IllegalArgumentException("ContentId contains a non-hexadecimal character");
            }
        }
    }

    public static ContentId sha256(byte[] bytes) {
        try {
            return new ContentId(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", impossible);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
