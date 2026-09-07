package dev.ztawfik.cctweakedhighaudio.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentIdAndStoreTest {
    @Test
    void computesSha256OverOriginalBytes() {
        var id = ContentId.sha256("abc".getBytes(StandardCharsets.UTF_8));
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", id.value());
    }

    @Test
    void validatesAndNormalizesIds() {
        var uppercase = "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD";
        assertEquals(uppercase.toLowerCase(), new ContentId(uppercase).value());
        assertThrows(IllegalArgumentException.class, () -> new ContentId("not-a-content-id"));
    }

    @Test
    void storeDeduplicatesCopiesAndEvictsLeastRecentlyUsed() {
        var store = new ContentStore(6);
        var first = store.put(new byte[]{1, 2});
        var second = store.put(new byte[]{3, 4, 5});
        assertEquals(first, store.put(new byte[]{1, 2}));
        assertEquals(5, store.storedBytes());

        assertTrue(store.get(first).isPresent());
        var third = store.put(new byte[]{6, 7, 8, 9});
        assertFalse(store.get(second).isPresent());
        assertArrayEquals(new byte[]{1, 2}, store.get(first).orElseThrow());
        assertArrayEquals(new byte[]{6, 7, 8, 9}, store.get(third).orElseThrow());
        assertEquals(6, store.storedBytes());
    }

    @Test
    void clientCacheRejectsWrongHashAndRemainsByteBounded() {
        var cache = new CompressedContentCache(4);
        var one = new byte[]{1, 2, 3};
        var two = new byte[]{4, 5};
        var oneId = ContentId.sha256(one);
        var twoId = ContentId.sha256(two);

        assertTrue(cache.put(oneId, one));
        assertThrows(IllegalArgumentException.class, () -> cache.put(oneId, two));
        assertTrue(cache.put(twoId, two));
        assertFalse(cache.get(oneId).isPresent());
        assertEquals(2, cache.storedBytes());
    }
}
