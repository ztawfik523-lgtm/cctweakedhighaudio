package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import com.mojang.blaze3d.audio.Channel;
import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.client.audio.exp2.GeneratedPcmStream;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** Part A of EXP-003: measure real Minecraft-owned streaming-channel capacity without raw OpenAL access. */
public final class Exp3CapacityController {
    private static final int[] ALLOWED_COUNTS = {1, 4, 8, 16};

    private static final List<Exp3CapacitySound> sounds = new ArrayList<>();
    private static final Map<Exp3CapacitySound, Channel> channels = new IdentityHashMap<>();

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
        if (!isAllowed(count)) return "EXP-003 capacity count must be one of 1, 4, 8, 16";

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return "EXP-003: no client player/world is active";

        clearExisting();

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
            var sound = new Exp3CapacitySound(i, position, new GeneratedPcmStream());
            sounds.add(sound);
            minecraft.getSoundManager().play(sound);
        }

        HighAudio.LOGGER.info(
            "[EXP-003] capacity requested={} position=({}, {}, {}) engineGeneration={} soundDebug={} usedHeapBytes={}",
            count, player.getX(), player.getY(), player.getZ(), engineGeneration,
            safeDebugString(), baselineUsedHeap
        );

        return "EXP-003 capacity probe requested " + count + " Minecraft-owned streams";
    }

    public static String stop() {
        if (sounds.isEmpty()) return "EXP-003: no active capacity probe";
        Minecraft.getInstance().getSoundManager().stop(Exp3CapacitySound.class.cast(sounds.getFirst()));
        // Stop every sound individually: SoundManager has no class-filtered stop operation.
        for (var sound : sounds) Minecraft.getInstance().getSoundManager().stop(sound);
        lastOutcome = "stop-requested";
        HighAudio.LOGGER.info("[EXP-003] capacity stop requested requested={} captures={}", requestedCount, captures);
        return "EXP-003 capacity stop requested";
    }

    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        if (!(event.getSound() instanceof Exp3CapacitySound sound)) return;
        if (!sounds.contains(sound)) {
            HighAudio.LOGGER.info("[EXP-003] ignored stale capacity channel index={}", sound.index());
            return;
        }

        captures++;
        channels.put(sound, event.getChannel());
        lastOutcome = "channel-captured:" + captures + "/" + requestedCount;

        HighAudio.LOGGER.info(
            "[EXP-003] capacity channel captured index={} channelIdentity=0x{} captures={}/{} engineGeneration={}",
            sound.index(), Integer.toHexString(System.identityHashCode(event.getChannel())),
            captures, requestedCount, engineGeneration
        );
    }

    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        engineGeneration++;
        var hadProbe = !sounds.isEmpty();
        var priorRequested = requestedCount;
        var priorCaptures = captures;
        clearStateOnly();
        lastOutcome = "sound-engine-load";

        HighAudio.LOGGER.info(
            "[EXP-003] SoundEngineLoadEvent generation={} hadCapacityProbe={} priorRequested={} priorCaptures={} engineIdentity=0x{}",
            engineGeneration, hadProbe, priorRequested, priorCaptures,
            Integer.toHexString(System.identityHashCode(event.getEngine()))
        );
    }

    public static void tick() {
        if (sounds.isEmpty()) return;
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
        if (!finalLogged && active == 0 && ticksSinceRequest >= 5) {
            finalLogged = true;
            logSnapshot("final");
            lastOutcome = "finished:" + captures + "/" + requestedCount + " captured";
            clearStateOnly();
        }
    }

    public static String status() {
        return "EXP-003 capacity status: requested=" + requestedCount
            + ", captures=" + captures
            + ", active=" + activeSoundCount()
            + ", runningChannels=" + runningChannelCount()
            + ", stoppedChannels=" + stoppedChannelCount()
            + ", engineGeneration=" + engineGeneration
            + ", last=" + lastOutcome
            + ", soundDebug=" + safeDebugString();
    }

    private static void logSnapshot(String phase) {
        var used = usedHeap();
        HighAudio.LOGGER.info(
            "[EXP-003] capacity snapshot phase={} requested={} captures={} activeSounds={} runningChannels={} stoppedChannels={} closedStreams={} heapDeltaBytes={} soundDebug={}",
            phase, requestedCount, captures, activeSoundCount(), runningChannelCount(), stoppedChannelCount(),
            closedStreamCount(), used - baselineUsedHeap, safeDebugString()
        );
    }

    private static int activeSoundCount() {
        var manager = Minecraft.getInstance().getSoundManager();
        var active = 0;
        for (var sound : sounds) if (manager.isActive(sound)) active++;
        return active;
    }

    private static int runningChannelCount() {
        var running = 0;
        for (var channel : channels.values()) if (!channel.stopped()) running++;
        return running;
    }

    private static int stoppedChannelCount() {
        var stopped = 0;
        for (var channel : channels.values()) if (channel.stopped()) stopped++;
        return stopped;
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

    private static boolean isAllowed(int count) {
        for (var allowed : ALLOWED_COUNTS) if (allowed == count) return true;
        return false;
    }

    private static void clearExisting() {
        var manager = Minecraft.getInstance().getSoundManager();
        for (var sound : sounds) manager.stop(sound);
        clearStateOnly();
    }

    private static void clearStateOnly() {
        sounds.clear();
        channels.clear();
        requestedCount = 0;
        captures = 0;
        ticksSinceRequest = 0;
        summary5 = false;
        summary20 = false;
        summary40 = false;
        finalLogged = false;
        baselineUsedHeap = 0;
    }
}
