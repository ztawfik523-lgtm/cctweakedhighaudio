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
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<Delivery> DELIVERED = new HashSet<>();

    private static MediaLimits.Values limits = MediaLimits.defaults();
    private static ContentStore content;
    private static UploadManager uploads;
    private static MinecraftServer server;
    private static int expiryTick;

    private MediaServerRuntime() {
    }

    public static void onServerStarted(ServerStartedEvent event) {
        clear();
        limits = MediaLimits.current();
        content = new ContentStore(limits.serverContentStoreBytes());
        uploads = new UploadManager(content, System::nanoTime, limits.uploadLimits());
        server = event.getServer();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clear();
        server = null;
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++expiryTick < 20) return;
        expiryTick = 0;
        var expired = uploads().expire();
        if (expired > 0) HighAudio.LOGGER.info("Expired {} incomplete HighAudio upload(s)", expired);
    }

    public static UUID beginUpload(UploadManager.Owner owner, int expectedBytes) throws UploadException {
        return uploads().begin(owner, expectedBytes);
    }

    public static int writeUpload(UploadManager.Owner owner, UUID uploadId, byte[] copiedChunk) throws UploadException {
        return uploads().write(owner, uploadId, copiedChunk);
    }

    public static ContentId finishUpload(UploadManager.Owner owner, UUID uploadId) throws UploadException {
        return uploads().finish(owner, uploadId);
    }

    public static boolean abortUpload(UploadManager.Owner owner, UUID uploadId) throws UploadException {
        return uploads().abort(owner, uploadId);
    }

    public static UUID play(UploadManager.Owner owner, ServerLevel level, Vec3 position, ContentId contentId) throws UploadException {
        var storedContent = content().get(contentId)
            .orElseThrow(() -> new UploadException("Content is unavailable or was evicted from the server store"));
        var sourceId = owner.speakerId();
        var prior = SESSIONS.remove(sourceId);
        if (prior != null) {
            clearDeliveries(prior.sessionId);
            sendStop(prior);
        } else if (SESSIONS.size() >= limits.serverSessionCap()) {
            throw new UploadException("Server already has the configured maximum of " + limits.serverSessionCap()
                + " HighAudio sessions");
        }

        var session = new Session(
            sourceId,
            UUID.randomUUID(),
            contentId,
            storedContent.length,
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
            limits.playbackRadiusBlocks(),
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
        var aborted = uploads().abortOwner(owner);
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
            || player.position().distanceToSqr(session.position)
                > (double) limits.playbackRadiusBlocks() * limits.playbackRadiusBlocks()) {
            context.reply(new MediaPayloads.ContentUnavailable(request.sessionId(), "Session is no longer available to this player"));
            return;
        }

        var storedContent = content().get(session.contentId);
        if (storedContent.isEmpty()) {
            context.reply(new MediaPayloads.ContentUnavailable(request.sessionId(), "Content was evicted from the server store"));
            return;
        }

        var delivery = new Delivery(player.getUUID(), session.sessionId);
        if (!DELIVERED.add(delivery)) return;

        sendContent(player, session, storedContent.get());
    }

    private static void sendContent(ServerPlayer player, Session session, byte[] content) {
        PacketDistributor.sendToPlayer(
            player,
            new MediaPayloads.ContentBegin(session.sessionId, session.contentId, content.length)
        );
        for (var offset = 0; offset < content.length; offset += limits.networkTransferChunkBytes()) {
            var end = Math.min(content.length, offset + limits.networkTransferChunkBytes());
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
            limits.playbackRadiusBlocks(),
            new MediaPayloads.Stop(session.sourceId, session.sessionId)
        );
    }

    private static void clearDeliveries(UUID sessionId) {
        DELIVERED.removeIf(delivery -> delivery.sessionId.equals(sessionId));
    }

    private static void clear() {
        if (uploads != null) uploads.clear();
        if (content != null) content.clear();
        uploads = null;
        content = null;
        SESSIONS.clear();
        DELIVERED.clear();
        expiryTick = 0;
    }

    private static UploadManager uploads() {
        if (uploads == null) throw new IllegalStateException("HighAudio server runtime is not started");
        return uploads;
    }

    private static ContentStore content() {
        if (content == null) throw new IllegalStateException("HighAudio server runtime is not started");
        return content;
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
