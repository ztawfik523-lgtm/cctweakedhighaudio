package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin.ChannelSourceAccessor;
import dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin.SoundEngineExecutorAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundEngine;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EXP-003 scheduled-start diagnostic. It proves the optional device-clock primitive on Minecraft-owned
 * streaming sources without adding production session/media behavior.
 */
public final class Exp3ScheduledController {
    private static final long DIAGNOSTIC_LEAD_NANOS = 250_000_000L;
    private static final int FINAL_INACTIVE_TICKS = 10;

    private static final List<Exp3ScheduledSound> sounds = new ArrayList<>();
    private static final ConcurrentLinkedQueue<DiagnosticEvent> pendingEvents = new ConcurrentLinkedQueue<>();

    private static volatile ActiveTrial activeTrial;
    private static long nextRunToken;
    private static int trialTicks;
    private static int armTick = -1;
    private static int inactiveTicks;
    private static int captures;
    private static int maxPrePauseOffset;
    private static int maxPostRewindOffset;
    private static boolean samplePreTargetScheduled;
    private static boolean sampleNearTargetScheduled;
    private static boolean samplePostTargetScheduled;
    private static boolean sampleLateScheduled;
    private static boolean stopAfterArmFailure;
    private static String lastOutcome = "not-run";

    private Exp3ScheduledController() {
    }

    public static boolean isBusy() {
        return activeTrial != null;
    }

    public static String run(int count) {
        if (count < 1 || count > 16) return "EXP-003 scheduled count must be 1..16";
        if (isBusy()) return "EXP-003 scheduled diagnostic is already running";
        if (Exp3CapacityController.isBusy() || Exp3SyncController.isBusy()) {
            return "EXP-003: wait for the other diagnostic to finalize first";
        }

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return "EXP-003: no client player/world is active";

        Exp3SyncStream.warmUpSharedPcm();
        sounds.clear();
        pendingEvents.clear();
        trialTicks = 0;
        armTick = -1;
        inactiveTicks = 0;
        captures = 0;
        maxPrePauseOffset = 0;
        maxPostRewindOffset = 0;
        samplePreTargetScheduled = false;
        sampleNearTargetScheduled = false;
        samplePostTargetScheduled = false;
        sampleLateScheduled = false;
        stopAfterArmFailure = false;

        var token = ++nextRunToken;
        var trial = new ActiveTrial(token, count);
        activeTrial = trial;

        var origin = player.position();
        for (var i = 0; i < count; i++) {
            var angle = Math.PI * 2.0 * i / count;
            var position = origin.add(Math.cos(angle) * 1.25, 0.0, Math.sin(angle) * 1.25);
            var sound = new Exp3ScheduledSound(token, i, position, new Exp3SyncStream());
            sounds.add(sound);
            minecraft.getSoundManager().play(sound);
        }

        lastOutcome = "scheduled-started";
        HighAudio.LOGGER.info(
            "[EXP-003] scheduled trial requested token={} count={} leadNanos={} silentPrerollFrames={} soundDebug={}",
            token, count, DIAGNOSTIC_LEAD_NANOS, Exp3SyncStream.SILENT_PREROLL_FRAMES, safeDebugString()
        );
        return "EXP-003 scheduled diagnostic started for " + count + " source(s)";
    }

    /** Called by NeoForge on Minecraft's sound thread after Minecraft attaches and starts the stream. */
    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        if (!(event.getSound() instanceof Exp3ScheduledSound sound)) return;

        var trial = activeTrial;
        if (trial == null || sound.runToken() != trial.token()) return;

        var sourceId = ((ChannelSourceAccessor) (Object) event.getChannel()).highAudio$getSource();
        if (trial.sourceByIndex().putIfAbsent(sound.index(), sourceId) != null) return;

        var prePauseOffset = sampleOffset(sourceId);
        event.getChannel().pause();
        AL10.alSourceRewind(sourceId);
        var postRewindOffset = sampleOffset(sourceId);
        trial.engine = event.getEngine();

        var captureCount = trial.captureCount().incrementAndGet();
        pendingEvents.add(new CaptureEvent(
            trial.token(), sound.index(), captureCount, prePauseOffset, postRewindOffset,
            Thread.currentThread().getName()
        ));

        if (captureCount == trial.count()) {
            var ids = orderedSourceIds(trial);
            var result = Exp3TimingPrimitives.startScheduled(event.getEngine(), ids, DIAGNOSTIC_LEAD_NANOS);
            trial.targetDeviceClockNs = result.targetDeviceClockNs();
            trial.armed = result.success();
            pendingEvents.add(new ArmEvent(
                trial.token(), result.success(), result.alError(), result.callNanos(),
                result.targetDeviceClockNs(), result.detail(), Thread.currentThread().getName()
            ));
            measureAndEnqueue(trial, "armed");
        }
    }

    public static void tick() {
        drainEvents();
        var trial = activeTrial;
        if (trial == null) return;

        trialTicks++;

        if (stopAfterArmFailure) {
            stopAfterArmFailure = false;
            for (var sound : sounds) Minecraft.getInstance().getSoundManager().stop(sound);
        }

        if (trial.armed && armTick >= 0) {
            var sinceArm = trialTicks - armTick;
            if (!samplePreTargetScheduled && sinceArm >= 2) {
                samplePreTargetScheduled = true;
                scheduleMeasurement(trial, "pre-target");
            }
            if (!sampleNearTargetScheduled && sinceArm >= 5) {
                sampleNearTargetScheduled = true;
                scheduleMeasurement(trial, "near-target");
            }
            if (!samplePostTargetScheduled && sinceArm >= 7) {
                samplePostTargetScheduled = true;
                scheduleMeasurement(trial, "post-target");
            }
            if (!sampleLateScheduled && sinceArm >= 12) {
                sampleLateScheduled = true;
                scheduleMeasurement(trial, "late");
            }
        }

        var active = activeSoundCount();
        if (active == 0) inactiveTicks++;
        else inactiveTicks = 0;

        if (trialTicks >= 5 && inactiveTicks >= FINAL_INACTIVE_TICKS) {
            drainEvents();
            finalizeTrial(trial);
        }
    }

    public static String stop() {
        var trial = activeTrial;
        if (trial == null) return "EXP-003: no scheduled diagnostic is running";
        for (var sound : sounds) Minecraft.getInstance().getSoundManager().stop(sound);
        lastOutcome = "scheduled-stop-requested";
        return "EXP-003 scheduled diagnostic stop requested";
    }

    public static String status() {
        drainEvents();
        var trial = activeTrial;
        if (trial == null) return "EXP-003 scheduled status: idle, last=" + lastOutcome;
        return "EXP-003 scheduled status: count=" + trial.count()
            + ", captures=" + captures + "/" + trial.count()
            + ", armed=" + trial.armed
            + ", targetDeviceClockNs=" + trial.targetDeviceClockNs
            + ", active=" + activeSoundCount();
    }

    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        var trial = activeTrial;
        if (trial == null) return;
        sounds.clear();
        pendingEvents.clear();
        activeTrial = null;
        lastOutcome = "scheduled-aborted:sound-engine-load";
        HighAudio.LOGGER.info(
            "[EXP-003] scheduled trial aborted by SoundEngineLoadEvent priorToken={} engineIdentity=0x{}",
            trial.token(), Integer.toHexString(System.identityHashCode(event.getEngine()))
        );
    }

    private static void scheduleMeasurement(ActiveTrial trial, String stage) {
        var engine = trial.engine;
        if (engine == null) return;
        try {
            var executor = ((SoundEngineExecutorAccessor) (Object) engine).highAudio$getExecutor();
            executor.execute(() -> {
                if (activeTrial != trial) return;
                measureAndEnqueue(trial, stage);
            });
        } catch (RuntimeException exception) {
            pendingEvents.add(new FailureEvent(
                trial.token(), stage, exception.getClass().getSimpleName() + ":" + exception.getMessage()
            ));
        }
    }

    /** Must run on Minecraft's sound thread. */
    private static void measureAndEnqueue(ActiveTrial trial, String stage) {
        var ids = orderedSourceIds(trial);
        if (ids.length != trial.count()) return;

        var offsets = new int[ids.length];
        var playing = 0;
        var paused = 0;
        var initial = 0;
        var stopped = 0;
        var min = Integer.MAX_VALUE;
        var max = Integer.MIN_VALUE;

        for (var i = 0; i < ids.length; i++) {
            var offset = sampleOffset(ids[i]);
            offsets[i] = offset;
            if (offset >= 0) {
                min = Math.min(min, offset);
                max = Math.max(max, offset);
            }
            var state = AL10.alGetSourcei(ids[i], AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_PLAYING) playing++;
            else if (state == AL10.AL_PAUSED) paused++;
            else if (state == AL10.AL_INITIAL) initial++;
            else if (state == AL10.AL_STOPPED) stopped++;
        }

        var capability = trial.engine == null ? null : Exp3TimingPrimitives.inspect(trial.engine);
        var clockNs = capability == null ? -1L : capability.deviceClockNs();
        var targetDeltaNs = clockNs < 0 || trial.targetDeviceClockNs < 0
            ? Long.MIN_VALUE
            : trial.targetDeviceClockNs - clockNs;
        var spread = min == Integer.MAX_VALUE ? -1 : max - min;

        pendingEvents.add(new SampleEvent(
            trial.token(), stage,
            min == Integer.MAX_VALUE ? -1 : min,
            max == Integer.MIN_VALUE ? -1 : max,
            spread, playing, paused, initial, stopped,
            clockNs, targetDeltaNs, Arrays.toString(offsets), Thread.currentThread().getName()
        ));
    }

    private static void drainEvents() {
        DiagnosticEvent event;
        while ((event = pendingEvents.poll()) != null) {
            var trial = activeTrial;
            if (trial == null || event.token() != trial.token()) continue;

            if (event instanceof CaptureEvent capture) {
                captures = Math.max(captures, capture.captureCount());
                if (capture.prePauseOffset() >= 0) maxPrePauseOffset = Math.max(maxPrePauseOffset, capture.prePauseOffset());
                if (capture.postRewindOffset() >= 0) maxPostRewindOffset = Math.max(maxPostRewindOffset, capture.postRewindOffset());
                HighAudio.LOGGER.info(
                    "[EXP-003] scheduled capture token={} index={} captures={}/{} prePauseOffsetSamples={} postRewindOffsetSamples={} thread={}",
                    trial.token(), capture.index(), captures, trial.count(),
                    capture.prePauseOffset(), capture.postRewindOffset(), capture.thread()
                );
            } else if (event instanceof ArmEvent arm) {
                armTick = trialTicks;
                if (!arm.success()) stopAfterArmFailure = true;
                HighAudio.LOGGER.info(
                    "[EXP-003] scheduled arm token={} count={} success={} alError={} callNanos={} targetDeviceClockNs={} detail={} thread={}",
                    trial.token(), trial.count(), arm.success(), arm.alError(), arm.callNanos(),
                    arm.targetDeviceClockNs(), arm.detail(), arm.thread()
                );
            } else if (event instanceof SampleEvent sample) {
                HighAudio.LOGGER.info(
                    "[EXP-003] scheduled sample token={} count={} stage={} minOffsetSamples={} maxOffsetSamples={} spreadSamples={} spreadMs={} statesPlaying={} statesPaused={} statesInitial={} statesStopped={} deviceClockNs={} targetDeltaNs={} offsets={} thread={}",
                    trial.token(), trial.count(), sample.stage(), sample.minOffset(), sample.maxOffset(),
                    sample.spread(), samplesToMs(sample.spread()), sample.playing(), sample.paused(),
                    sample.initial(), sample.stopped(), sample.deviceClockNs(), sample.targetDeltaNs(),
                    sample.offsets(), sample.thread()
                );
            } else if (event instanceof FailureEvent failure) {
                HighAudio.LOGGER.warn(
                    "[EXP-003] scheduled measurement failure token={} stage={} detail={}",
                    trial.token(), failure.stage(), failure.detail()
                );
            }
        }
    }

    private static void finalizeTrial(ActiveTrial trial) {
        if (activeTrial != trial) return;
        HighAudio.LOGGER.info(
            "[EXP-003] scheduled trial final token={} count={} captures={}/{} armed={} targetDeviceClockNs={} maxPrePauseOffsetSamples={} maxPostRewindOffsetSamples={} closedStreams={} soundDebug={}",
            trial.token(), trial.count(), captures, trial.count(), trial.armed, trial.targetDeviceClockNs,
            maxPrePauseOffset, maxPostRewindOffset, closedStreamCount(), safeDebugString()
        );
        lastOutcome = trial.armed ? "scheduled-complete" : "scheduled-arm-failed";
        sounds.clear();
        pendingEvents.clear();
        activeTrial = null;
    }

    private static int[] orderedSourceIds(ActiveTrial trial) {
        var ids = new int[trial.count()];
        for (var i = 0; i < ids.length; i++) {
            var source = trial.sourceByIndex().get(i);
            if (source == null) return new int[0];
            ids[i] = source;
        }
        return ids;
    }

    private static int sampleOffset(int sourceId) {
        try {
            return AL10.alGetSourcei(sourceId, AL11.AL_SAMPLE_OFFSET);
        } catch (RuntimeException exception) {
            return -1;
        }
    }

    private static int activeSoundCount() {
        var manager = Minecraft.getInstance().getSoundManager();
        var count = 0;
        for (var sound : sounds) if (manager.isActive(sound)) count++;
        return count;
    }

    private static int closedStreamCount() {
        var count = 0;
        for (var sound : sounds) if (sound.stream().isClosed()) count++;
        return count;
    }

    private static String safeDebugString() {
        try {
            return Minecraft.getInstance().getSoundManager().getDebugString();
        } catch (RuntimeException exception) {
            return "unavailable:" + exception.getClass().getSimpleName();
        }
    }

    private static double samplesToMs(int samples) {
        return samples < 0 ? -1.0 : samples * 1000.0 / Exp3SyncStream.SAMPLE_RATE;
    }

    private static final class ActiveTrial {
        private final long token;
        private final int count;
        private final Map<Integer, Integer> sourceByIndex = new ConcurrentHashMap<>();
        private final AtomicInteger captureCount = new AtomicInteger();
        private volatile SoundEngine engine;
        private volatile boolean armed;
        private volatile long targetDeviceClockNs = -1L;

        private ActiveTrial(long token, int count) {
            this.token = token;
            this.count = count;
        }

        long token() { return token; }
        int count() { return count; }
        Map<Integer, Integer> sourceByIndex() { return sourceByIndex; }
        AtomicInteger captureCount() { return captureCount; }
    }

    private sealed interface DiagnosticEvent permits CaptureEvent, ArmEvent, SampleEvent, FailureEvent {
        long token();
    }

    private record CaptureEvent(
        long token, int index, int captureCount, int prePauseOffset, int postRewindOffset, String thread
    ) implements DiagnosticEvent {}

    private record ArmEvent(
        long token, boolean success, int alError, long callNanos, long targetDeviceClockNs, String detail, String thread
    ) implements DiagnosticEvent {}

    private record SampleEvent(
        long token, String stage, int minOffset, int maxOffset, int spread,
        int playing, int paused, int initial, int stopped,
        long deviceClockNs, long targetDeltaNs, String offsets, String thread
    ) implements DiagnosticEvent {}

    private record FailureEvent(long token, String stage, String detail) implements DiagnosticEvent {}
}
