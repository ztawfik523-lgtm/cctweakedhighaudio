package dev.ztawfik.cctweakedhighaudio.media;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Optional;

/** In-memory, byte-bounded, content-addressed LRU store for finite uploaded files. */
public final class ContentStore {
    private final int maximumBytes;
    private final LinkedHashMap<ContentId, byte[]> entries = new LinkedHashMap<>(16, 0.75f, true);
    private int storedBytes;

    public ContentStore(int maximumBytes) {
        if (maximumBytes <= 0) throw new IllegalArgumentException("maximumBytes must be positive");
        this.maximumBytes = maximumBytes;
    }

    public synchronized ContentId put(byte[] content) {
        if (content.length == 0) throw new IllegalArgumentException("Content must not be empty");
        if (content.length > maximumBytes) throw new IllegalArgumentException("Content exceeds store capacity");

        var owned = Arrays.copyOf(content, content.length);
        var id = ContentId.sha256(owned);
        if (entries.containsKey(id)) {
            entries.get(id);
            return id;
        }

        while (storedBytes + content.length > maximumBytes && !entries.isEmpty()) {
            var eldest = entries.entrySet().iterator().next();
            storedBytes -= eldest.getValue().length;
            entries.remove(eldest.getKey());
        }

        entries.put(id, owned);
        storedBytes += owned.length;
        return id;
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
