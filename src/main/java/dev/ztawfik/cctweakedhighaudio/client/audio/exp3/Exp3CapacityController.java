package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Part A of EXP-003: measure real Minecraft-owned streaming-channel capacity without raw OpenAL access. */
public final class Exp3CapacityController {
    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 16;

    /** Sound-thread -> render-thread event handoff. No OpenAL/channel methods are invoked outside the event callback. */
    private static final ConcurrentLinkedQueue<ChannelCapture> pendingCaptures = new ConcurrentLinkedQueue<>();

    /** Render-thread-owned live state. */
    private static final List<Exp3CapacitySound> sounds = new ArrayList<>();
    private static final Map<Exp3CapacitySound, Integer> capturedChannelIdentities = new IdentityHashMap<>();

    private static long nextRunToken;
    private static long activeRunToken;
    private static int requestedCount;
    private static int captures;
    private static int ticksSinceRequest;
    private static int engineGeneration;
    private static long baselineUsedHeap;
    private static boolean summary5;
    private static boolean summary20;
    private static boolean summary40;
    private static boolean finalLogged;
    private static String lastOutcome = "not-run";

    private Exp3CapacityController() {
    }

    public static String runCapacity(int count) {
        if (count < MIN_COUNT || count > MAX_COUNT) {
            return "EXP-003 capacity count must be between " + MIN_COUNT + " and " + MAX_COUNT;
        }

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return "EXP-003: no client player/world is active";

        drainCaptures();

        // Keep each measurement isolated. Sound/channel release crosses the sound thread, so do not silently stop
        // a previous run and immediately consume the same streaming pool for the next one. A new run becomes legal
        // only after tick() has observed the previous one fully inactive and logged its final snapshot.
        if (!sounds.isEmpty() && !finalLogged) {
            return "EXP-003: previous capacity probe has not finalized yet; wait for phase=final (or stop it and wait) before starting another count";
        }
        if (!sounds.isEmpty()) clearStateOnly();

        activeRunToken = ++nextRunToken;
        requestedCount = count;
        captures = 0;
        ticksSinceRequest = 0;
        summary5 = false;
        summary20 = false;
        summary40 = false;
        finalLogged = false;
        baselineUsedHeap = usedHeap();
        lastOutcome = "capacity-requested:" + count;

        var origin = player.position();
        for (var i = 0; i < count; i++) {
            var angle = count == 1 ? 0.0 : (Math.PI * 2.0 * i / count);
            var position = origin.add(Math.cos(angle) * 1.25, 0.0, Math.sin(angle) * 1.25);
            var sound = new Exp3CapacitySound(activeRunToken, i, position, new Exp3CapacityStream());
            sounds.add(sound);
            minecraft.getSoundManager().play(sound);
        }

        HighAudio.LOGGER.info(
            "[EXP-003] capacity requested={} runToken={} position=({}, {}, {}) engineGeneration={} soundDebug={} usedHeapBytes={}",
            count, activeRunToken, player.getX(), player.getY(), player.getZ(), engineGeneration,
            safeDebugString(), baselineUsedHeap
        );

        return "EXP-003 capacity probe requested " + count + " Minecraft-owned streams";
    }

    public static String stop() {
        if (sounds.isEmpty()) return "EXP-003: no capacity probe state";
        if (finalLogged) return "EXP-003: capacity probe already finalized";

        for (var sound : sounds) Minecraft.getInstance().getSoundManager().stop(sound);
        lastOutcome = "stop-requested";
        HighAudio.LOGGER.info(
            "[EXP-003] capacity stop requested runToken={} requested={} captures={}",
            activeRunToken, requestedCount, captures
        );
        return "EXP-003 capacity stop requested; wait for phase=final before starting another count";
    }

    /** Called on Minecraft's sound thread by NeoForge. Keep this callback non-blocking and side-effect-light. */
    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        if (!(event.getSound() instanceof Exp3CapacitySound sound)) return;

        pendingCaptures.add(new ChannelCapture(
            sound,
            sound.runToken(),
            sound.index(),
            System.identityHashCode(event.getChannel()),
            Thread.currentThread().getName()
        ));
    }

    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        engineGeneration++;
        var hadProbe = !sounds.isEmpty();
        var priorRequested = requestedCount;
        var priorCaptures = captures;
        clearStateOnly();
        pendingCaptures.clear();
        lastOutcome = "sound-engine-load";

        HighAudio.LOGGER.info(
            "[EXP-003] SoundEngineLoadEvent generation={} hadCapacityProbe={} priorRequested={} priorCaptures={} engineIdentity=0x{}",
            engineGeneration, hadProbe, priorRequested, priorCaptures,
            Integer.toHexString(System.identityHashCode(event.getEngine()))
        );
    }

    public static void tick() {
        drainCaptures();
        if (sounds.isEmpty() || finalLogged) return;
        ticksSinceRequest++;

        if (!summary5 && ticksSinceRequest >= 5) {
            summary5 = true;
            logSnapshot("t+5ticks");
        }
        if (!summary20 && ticksSinceRequest >= 20) {
            summary20 = true;
            logSnapshot("t+20ticks");
        }
        if (!summary40 && ticksSinceRequest >= 40) {
            summary40 = true;
            logSnapshot("t+40ticks");
        }

        var active = activeSoundCount();
        if (active == 0 && ticksSinceRequest >= 5) {
            // Drain once more before freezing the result in case the final event arrived between the first drain and
            // SoundManager's inactive observation on this tick.
            drainCaptures();
            finalLogged = true;
            logSnapshot("final");
            lastOutcome = "finished:" + captures + "/" + requestedCount + " captured";
        }
    }

    public static String status() {
        drainCaptures();
        return "EXP-003 capacity status: requested=" + requestedCount
            + ", captures=" + captures
            + ", active=" + activeSoundCount()
            + ", closedStreams=" + closedStreamCount()
            + ", finalized=" + finalLogged
            + ", engineGeneration=" + engineGeneration
            + ", last=" + lastOutcome
            + ", soundDebug=" + safeDebugString();
    }

    private static void drainCaptures() {
        ChannelCapture capture;
        while ((capture = pendingCaptures.poll()) != null) {
            if (capture.runToken() != activeRunToken || !sounds.contains(capture.sound())) {
                HighAudio.LOGGER.info(
                    "[EXP-003] ignored stale capacity channel event runToken={} activeRunToken={} index={} channelIdentity=0x{}",
                    capture.runToken(), activeRunToken, capture.index(), Integer.toHexString(capture.channelIdentity())
                );
                continue;
            }

            if (capturedChannelIdentities.putIfAbsent(capture.sound(), capture.channelIdentity()) != null) continue;

            captures++;
            lastOutcome = "channel-captured:" + captures + "/" + requestedCount;
            HighAudio.LOGGER.info(
                "[EXP-003] capacity channel captured runToken={} index={} channelIdentity=0x{} captures={}/{} eventThread={}",
                activeRunToken, capture.index(), Integer.toHexString(capture.channelIdentity()),
                captures, requestedCount, capture.eventThread()
            );
        }
    }

    private static void logSnapshot(String phase) {
        var used = usedHeap();
        HighAudio.LOGGER.info(
            "[EXP-003] capacity snapshot phase={} runToken={} requested={} captures={} activeSounds={} closedStreams={} heapDeltaBytes={} soundDebug={}",
            phase, activeRunToken, requestedCount, captures, activeSoundCount(), closedStreamCount(),
            used - baselineUsedHeap, safeDebugString()
        );
    }

    private static int activeSoundCount() {
        var manager = Minecraft.getInstance().getSoundManager();
        var active = 0;
        for (var sound : sounds) if (manager.isActive(sound)) active++;
        return active;
    }

    private static int closedStreamCount() {
        var closed = 0;
        for (var sound : sounds) if (sound.stream().isClosed()) closed++;
        return closed;
    }

    private static String safeDebugString() {
        try {
            return Minecraft.getInstance().getSoundManager().getDebugString();
        } catch (RuntimeException exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    private static long usedHeap() {
        var runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void clearStateOnly() {
        sounds.clear();
        capturedChannelIdentities.clear();
        activeRunToken = 0;
        requestedCount = 0;
        captures = 0;
        ticksSinceRequest = 0;
        summary5 = false;
        summary20 = false;
        summary40 = false;
        finalLogged = false;
        baselineUsedHeap = 0;
    }

    private record ChannelCapture(
        Exp3CapacitySound sound,
        long runToken,
        int index,
        int channelIdentity,
        String eventThread
    ) {
    }
}
