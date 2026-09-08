package dev.ztawfik.cctweakedhighaudio.client.audio.media;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.media.CompressedContentCache;
import dev.ztawfik.cctweakedhighaudio.media.ContentId;
import dev.ztawfik.cctweakedhighaudio.media.MediaLimits;
import dev.ztawfik.cctweakedhighaudio.media.WavFormatException;
import dev.ztawfik.cctweakedhighaudio.media.WavPcmDecoder;
import dev.ztawfik.cctweakedhighaudio.network.MediaPayloads;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Client-only bounded content assembly, cache, decode, playback, and stop state. */
public final class MediaClientController {
    private static final MediaLimits.Values LIMITS = MediaLimits.current();
    private static final CompressedContentCache CACHE = new CompressedContentCache(LIMITS.clientContentCacheBytes());
    private static final LinkedHashMap<UUID, UUID> SOURCE_SESSIONS = new LinkedHashMap<>();
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static final Map<UUID, Transfer> TRANSFERS = new HashMap<>();
    private static final LinkedHashMap<UUID, Active> ACTIVE = new LinkedHashMap<>();

    private static long transferBytes;
    private static long playbackBytes;

    private MediaClientController() {
    }

    public static void handlePlay(MediaPayloads.Play payload) {
        if (payload.contentLength() <= 0 || payload.contentLength() > LIMITS.maxFileBytes()) {
            HighAudio.LOGGER.warn("Rejected HighAudio play with invalid content length {}", payload.contentLength());
            return;
        }

        stopSource(payload.sourceId());
        while (SOURCE_SESSIONS.size() >= LIMITS.clientSessionCap()) {
            stopSource(SOURCE_SESSIONS.entrySet().iterator().next().getKey());
        }

        var pending = new Pending(
            payload.sourceId(),
            payload.sessionId(),
            payload.contentId(),
            payload.contentLength(),
            new Vec3(payload.x(), payload.y(), payload.z())
        );
        SOURCE_SESSIONS.put(payload.sourceId(), payload.sessionId());
        PENDING.put(payload.sessionId(), pending);

        var cached = CACHE.get(payload.contentId());
        if (cached.isPresent()) {
            start(pending, cached.get());
        } else {
            PacketDistributor.sendToServer(
                new MediaPayloads.ContentRequest(payload.sourceId(), payload.sessionId(), payload.contentId())
            );
        }
    }

    public static void handleStop(MediaPayloads.Stop payload) {
        var current = SOURCE_SESSIONS.get(payload.sourceId());
        if (payload.sessionId().equals(current)) stopSource(payload.sourceId());
    }

    public static void handleContentBegin(MediaPayloads.ContentBegin payload) {
        var pending = PENDING.get(payload.sessionId());
        if (pending == null) return;
        if (!pending.contentId.equals(payload.contentId())
            || pending.contentLength != payload.contentLength()
            || payload.contentLength() <= 0
            || payload.contentLength() > LIMITS.maxFileBytes()) {
            failSession(payload.sessionId(), "content metadata did not match the authoritative play request");
            return;
        }

        removeTransfer(payload.sessionId());
        if (TRANSFERS.size() >= LIMITS.concurrentClientTransfers()
            || payload.contentLength() > LIMITS.clientTransferMemoryBytes() - transferBytes) {
            failSession(payload.sessionId(), "configured client transfer limit is exhausted (count "
                + TRANSFERS.size() + "/" + LIMITS.concurrentClientTransfers() + ", reserved " + transferBytes
                + "/" + LIMITS.clientTransferMemoryBytes() + " bytes)");
            return;
        }
        TRANSFERS.put(payload.sessionId(), new Transfer(payload.contentId(), payload.contentLength()));
        transferBytes += payload.contentLength();
    }

    public static void handleContentChunk(MediaPayloads.ContentChunk payload) {
        var transfer = TRANSFERS.get(payload.sessionId());
        if (transfer == null) return;
        var chunk = payload.bytes();
        if (chunk.length == 0
            || chunk.length > LIMITS.networkTransferChunkBytes()
            || payload.offset() != transfer.received
            || chunk.length > transfer.bytes.length - transfer.received) {
            failSession(payload.sessionId(), "malformed or out-of-order content chunk");
            return;
        }
        System.arraycopy(chunk, 0, transfer.bytes, transfer.received, chunk.length);
        transfer.received += chunk.length;
    }

    public static void handleContentEnd(MediaPayloads.ContentEnd payload) {
        var transfer = TRANSFERS.get(payload.sessionId());
        var pending = PENDING.get(payload.sessionId());
        if (transfer == null || pending == null) return;
        if (transfer.received != transfer.bytes.length
            || !transfer.contentId.equals(payload.contentId())
            || !ContentId.sha256(transfer.bytes).equals(payload.contentId())) {
            failSession(payload.sessionId(), "incomplete content or SHA-256 mismatch");
            return;
        }

        var bytes = transfer.bytes;
        removeTransfer(payload.sessionId());
        if (!CACHE.put(payload.contentId(), bytes)) {
            failSession(payload.sessionId(), "content does not fit the bounded client cache");
            return;
        }
        start(pending, bytes);
    }

    public static void handleContentUnavailable(MediaPayloads.ContentUnavailable payload) {
        if (PENDING.containsKey(payload.sessionId())) failSession(payload.sessionId(), payload.reason());
    }

    public static void tick() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearSessions(true);
            return;
        }
        var iterator = ACTIVE.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            var active = entry.getValue();
            if (minecraft.getSoundManager().isActive(active.sound)) continue;
            playbackBytes -= active.stream.totalBytes();
            active.stream.close();
            iterator.remove();
            if (active.sessionId.equals(SOURCE_SESSIONS.get(entry.getKey()))) {
                SOURCE_SESSIONS.remove(entry.getKey());
            }
        }
    }

    public static void onSoundEngineLoad() {
        clearSessions(false);
    }

    private static void start(Pending pending, byte[] content) {
        if (!pending.sessionId.equals(SOURCE_SESSIONS.get(pending.sourceId))) return;
        try {
            var decoded = WavPcmDecoder.decode(content);
            while (ACTIVE.size() >= LIMITS.concurrentClientPlaybacks()
                || decoded.bytes().length > LIMITS.clientPlaybackMemoryBytes() - playbackBytes) {
                if (ACTIVE.isEmpty()) {
                    failSession(pending.sessionId, "decoded PCM size " + decoded.bytes().length
                        + " bytes exceeds the configured playback memory budget of "
                        + LIMITS.clientPlaybackMemoryBytes() + " bytes");
                    return;
                }
                stopSource(ACTIVE.entrySet().iterator().next().getKey());
            }

            var stream = new MediaPcmStream(decoded.format(), decoded.bytes());
            var sound = new MediaSound(pending.position, stream);
            PENDING.remove(pending.sessionId);
            removeTransfer(pending.sessionId);
            ACTIVE.put(pending.sourceId, new Active(pending.sessionId, sound, stream));
            playbackBytes += stream.totalBytes();
            Minecraft.getInstance().getSoundManager().play(sound);
            HighAudio.LOGGER.info(
                "HighAudio play requested source={} session={} content={} position=({}, {}, {})",
                pending.sourceId,
                pending.sessionId,
                pending.contentId,
                pending.position.x,
                pending.position.y,
                pending.position.z
            );
        } catch (WavFormatException error) {
            failSession(pending.sessionId, error.getMessage());
        }
    }

    private static void stopSource(UUID sourceId) {
        var sessionId = SOURCE_SESSIONS.remove(sourceId);
        if (sessionId != null) {
            PENDING.remove(sessionId);
            removeTransfer(sessionId);
        }
        var active = ACTIVE.remove(sourceId);
        if (active != null) {
            Minecraft.getInstance().getSoundManager().stop(active.sound);
            active.stream.close();
            playbackBytes -= active.stream.totalBytes();
        }
    }

    private static void failSession(UUID sessionId, String reason) {
        var pending = PENDING.remove(sessionId);
        removeTransfer(sessionId);
        if (pending != null && sessionId.equals(SOURCE_SESSIONS.get(pending.sourceId))) {
            SOURCE_SESSIONS.remove(pending.sourceId);
        }
        HighAudio.LOGGER.warn("HighAudio session {} failed: {}", sessionId, reason);
    }

    private static void removeTransfer(UUID sessionId) {
        var transfer = TRANSFERS.remove(sessionId);
        if (transfer != null) transferBytes -= transfer.bytes.length;
    }

    private static void clearSessions(boolean stopSounds) {
        if (SOURCE_SESSIONS.isEmpty() && PENDING.isEmpty() && TRANSFERS.isEmpty() && ACTIVE.isEmpty()) return;
        var soundManager = Minecraft.getInstance().getSoundManager();
        for (var active : ACTIVE.values()) {
            if (stopSounds) soundManager.stop(active.sound);
            active.stream.close();
        }
        SOURCE_SESSIONS.clear();
        PENDING.clear();
        TRANSFERS.clear();
        ACTIVE.clear();
        transferBytes = 0;
        playbackBytes = 0;
    }

    private record Pending(
        UUID sourceId,
        UUID sessionId,
        ContentId contentId,
        int contentLength,
        Vec3 position
    ) {
    }

    private static final class Transfer {
        private final ContentId contentId;
        private final byte[] bytes;
        private int received;

        private Transfer(ContentId contentId, int contentLength) {
            this.contentId = contentId;
            bytes = new byte[contentLength];
        }
    }

    private record Active(UUID sessionId, MediaSound sound, MediaPcmStream stream) {
    }
}
