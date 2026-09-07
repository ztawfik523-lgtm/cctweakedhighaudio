package dev.ztawfik.cctweakedhighaudio.server;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.media.ContentId;
import dev.ztawfik.cctweakedhighaudio.media.ContentStore;
import dev.ztawfik.cctweakedhighaudio.media.MediaLimits;
import dev.ztawfik.cctweakedhighaudio.media.UploadException;
import dev.ztawfik.cctweakedhighaudio.media.UploadManager;
import dev.ztawfik.cctweakedhighaudio.network.MediaPayloads;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Server-authoritative ownership for M4 uploads, content, and one session per speaker. */
public final class MediaServerRuntime {
    private static final ContentStore CONTENT = new ContentStore(MediaLimits.SERVER_CONTENT_STORE_BYTES);
    private static final UploadManager UPLOADS = new UploadManager(CONTENT, System::nanoTime);
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<Delivery> DELIVERED = new HashSet<>();

    private static MinecraftServer server;
    private static int expiryTick;

    private MediaServerRuntime() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        clear();
        server = event.getServer();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
        server = null;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++expiryTick < 20) return;
        expiryTick = 0;
        var expired = UPLOADS.expire();
        if (expired > 0) HighAudio.LOGGER.info("Expired {} incomplete HighAudio upload(s)", expired);
    }

    public static UUID beginUpload(UploadManager.Owner owner, int expectedBytes) throws UploadException {
        return UPLOADS.begin(owner, expectedBytes);
    }

    public static int writeUpload(UploadManager.Owner owner, UUID uploadId, byte[] copiedChunk) throws UploadException {
        return UPLOADS.write(owner, uploadId, copiedChunk);
    }

    public static ContentId finishUpload(UploadManager.Owner owner, UUID uploadId) throws UploadException {
        return UPLOADS.finish(owner, uploadId);
    }

    public static boolean abortUpload(UploadManager.Owner owner, UUID uploadId) throws UploadException {
        return UPLOADS.abort(owner, uploadId);
    }

    public static UUID play(UploadManager.Owner owner, ServerLevel level, Vec3 position, ContentId contentId) throws UploadException {
        var content = CONTENT.get(contentId).orElseThrow(() -> new UploadException("Content is unavailable or was evicted from the server store"));
        var sourceId = owner.speakerId();
        var prior = SESSIONS.remove(sourceId);
        if (prior != null) {
            clearDeliveries(prior.sessionId);
            sendStop(prior);
        } else if (SESSIONS.size() >= MediaLimits.MAX_SERVER_SESSIONS) {
            throw new UploadException("Server already has the maximum of 256 HighAudio sessions");
        }

        var session = new Session(
            sourceId,
            UUID.randomUUID(),
            contentId,
            content.length,
            level.dimension(),
            position,
            owner
        );
        SESSIONS.put(sourceId, session);
        PacketDistributor.sendToPlayersNear(
            level,
            null,
            position.x,
            position.y,
            position.z,
            MediaLimits.PLAYBACK_BROADCAST_RADIUS,
            new MediaPayloads.Play(
                session.sourceId,
                session.sessionId,
                session.contentId,
                session.contentLength,
                position.x,
                position.y,
                position.z
            )
        );
        return session.sessionId;
    }

    public static boolean stop(UUID sourceId) {
        var session = SESSIONS.remove(sourceId);
        if (session == null) return false;
        clearDeliveries(session.sessionId);
        sendStop(session);
        return true;
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        DELIVERED.removeIf(delivery -> delivery.playerId.equals(event.getEntity().getUUID()));
    }

    public static void onComputerDetached(UUID sourceId, int computerId) {
        var owner = new UploadManager.Owner(sourceId, computerId);
        var aborted = UPLOADS.abortOwner(owner);
        var session = SESSIONS.get(sourceId);
        var stopped = session != null && session.owner.equals(owner) && stop(sourceId);
        if (aborted > 0 || stopped) {
            HighAudio.LOGGER.info(
                "Cleaned HighAudio state after computer detach source={} computerId={} uploads={} stopped={}",
                sourceId,
                computerId,
                aborted,
                stopped
            );
        }
    }

    public static void handleContentRequest(MediaPayloads.ContentRequest request, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        var session = SESSIONS.get(request.sourceId());
        if (session == null
            || !session.sessionId.equals(request.sessionId())
            || !session.contentId.equals(request.contentId())
            || !player.level().dimension().equals(session.dimension)
            || player.position().distanceToSqr(session.position) > MediaLimits.PLAYBACK_BROADCAST_RADIUS * MediaLimits.PLAYBACK_BROADCAST_RADIUS) {
            context.reply(new MediaPayloads.ContentUnavailable(request.sessionId(), "Session is no longer available to this player"));
            return;
        }

        var content = CONTENT.get(session.contentId);
        if (content.isEmpty()) {
            context.reply(new MediaPayloads.ContentUnavailable(request.sessionId(), "Content was evicted from the server store"));
            return;
        }

        var delivery = new Delivery(player.getUUID(), session.sessionId);
        if (!DELIVERED.add(delivery)) return;

        sendContent(player, session, content.get());
    }

    private static void sendContent(ServerPlayer player, Session session, byte[] content) {
        PacketDistributor.sendToPlayer(
            player,
            new MediaPayloads.ContentBegin(session.sessionId, session.contentId, content.length)
        );
        for (var offset = 0; offset < content.length; offset += MediaLimits.TRANSFER_CHUNK_BYTES) {
            var end = Math.min(content.length, offset + MediaLimits.TRANSFER_CHUNK_BYTES);
            PacketDistributor.sendToPlayer(
                player,
                new MediaPayloads.ContentChunk(session.sessionId, offset, Arrays.copyOfRange(content, offset, end))
            );
        }
        PacketDistributor.sendToPlayer(player, new MediaPayloads.ContentEnd(session.sessionId, session.contentId));
    }

    private static void sendStop(Session session) {
        if (server == null) return;
        var level = server.getLevel(session.dimension);
        if (level == null) return;
        PacketDistributor.sendToPlayersNear(
            level,
            null,
            session.position.x,
            session.position.y,
            session.position.z,
            MediaLimits.PLAYBACK_BROADCAST_RADIUS,
            new MediaPayloads.Stop(session.sourceId, session.sessionId)
        );
    }

    private static void clearDeliveries(UUID sessionId) {
        DELIVERED.removeIf(delivery -> delivery.sessionId.equals(sessionId));
    }

    private static void clear() {
        UPLOADS.clear();
        CONTENT.clear();
        SESSIONS.clear();
        DELIVERED.clear();
        expiryTick = 0;
    }

    private record Session(
        UUID sourceId,
        UUID sessionId,
        ContentId contentId,
        int contentLength,
        ResourceKey<Level> dimension,
        Vec3 position,
        UploadManager.Owner owner
    ) {
    }

    private record Delivery(UUID playerId, UUID sessionId) {
    }
}
