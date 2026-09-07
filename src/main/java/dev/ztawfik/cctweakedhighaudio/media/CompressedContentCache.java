package dev.ztawfik.cctweakedhighaudio.media;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;

/** Client-side byte-bounded LRU cache of original compressed/container bytes. */
public final class CompressedContentCache {
    private final int maximumBytes;
    private final LinkedHashMap<ContentId, byte[]> entries = new LinkedHashMap<>(16, 0.75f, true);
    private int storedBytes;

    public CompressedContentCache(int maximumBytes) {
        if (maximumBytes <= 0) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
    }

    public synchronized boolean put(ContentId id, byte[] content) {
        if (content.length > maximumBytes) return false;
        var owned = Arrays.copyOf(content, content.length);
        if (!ContentId.sha256(owned).equals(id)) throw new IllegalArgumentException("Content does not match ContentId");
        if (entries.containsKey(id)) {
            entries.get(id);
            return true;
        }

        while (storedBytes + content.length > maximumBytes && !entries.isEmpty()) {
            var eldest = entries.entrySet().iterator().next();
            storedBytes -= eldest.getValue().length;
            entries.remove(eldest.getKey());
        }
        entries.put(id, owned);
        storedBytes += owned.length;
        return true;
    }

    public synchronized Optional<byte[]> get(ContentId id) {
        var content = entries.get(id);
        return content == null ? Optional.empty() : Optional.of(Arrays.copyOf(content, content.length));
    }

    public synchronized int storedBytes() {
        return storedBytes;
    }

    public synchronized int entryCount() {
        return entries.size();
    }

    public synchronized void clear() {
        entries.clear();
        storedBytes = 0;
    }
}
