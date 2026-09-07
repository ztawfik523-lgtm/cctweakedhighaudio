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
    /** Diagnostic target for audible media sample zero, not for the hidden silent preroll. */
    private static final long DIAGNOSTIC_MEDIA_ZERO_LEAD_NANOS = 250_000_000L;
    private static final long SILENT_PREROLL_NANOS = framesToNanos(Exp3SyncStream.SILENT_PREROLL_FRAMES);
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
            "[EXP-003] scheduled trial requested token={} count={} mediaZeroLeadNanos={} silentPrerollFrames={} silentPrerollNanos={} soundDebug={}",
            token, count, DIAGNOSTIC_MEDIA_ZERO_LEAD_NANOS, Exp3SyncStream.SILENT_PREROLL_FRAMES,
            SILENT_PREROLL_NANOS, safeDebugString()
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
            armScheduledTrial(trial, event.getEngine());
            measureAndEnqueue(trial, "armed");
        }
    }

    /** Must run on Minecraft's sound thread. */
    private static void armScheduledTrial(ActiveTrial trial, SoundEngine engine) {
        var ids = orderedSourceIds(trial);
        var capability = Exp3TimingPrimitives.inspect(engine);

        Exp3TimingPrimitives.StartResult result;
        long sourceStartClockNs = -1L;
        long mediaZeroRenderClockNs = -1L;
        long estimatedMediaZeroOutputClockNs = -1L;

        if (!capability.available()) {
            result = new Exp3TimingPrimitives.StartResult(
                false, AL10.AL_INVALID_OPERATION, 0L, -1L,
                "scheduled-unavailable:" + capability.detail()
            );
        } else {
            try {
                mediaZeroRenderClockNs = Math.addExact(
                    capability.deviceClockNs(), DIAGNOSTIC_MEDIA_ZERO_LEAD_NANOS
                );
                sourceStartClockNs = Math.subtractExact(mediaZeroRenderClockNs, SILENT_PREROLL_NANOS);
                estimatedMediaZeroOutputClockNs = Math.addExact(
                    mediaZeroRenderClockNs, Math.max(0L, capability.deviceLatencyNs())
                );

                if (sourceStartClockNs <= capability.deviceClockNs()) {
                    result = new Exp3TimingPrimitives.StartResult(
                        false, AL10.AL_INVALID_VALUE, 0L, sourceStartClockNs,
                        "insufficient-lead-for-preroll"
                    );
                } else {
                    result = Exp3TimingPrimitives.startScheduledAt(engine, ids, sourceStartClockNs);
                }
            } catch (ArithmeticException exception) {
                result = new Exp3TimingPrimitives.StartResult(
                    false, AL10.AL_INVALID_VALUE, 0L, -1L, "clock-overflow"
                );
            }
        }

        trial.sourceStartDeviceClockNs = sourceStartClockNs;
        trial.mediaZeroRenderClockNs = mediaZeroRenderClockNs;
        trial.estimatedMediaZeroOutputClockNs = estimatedMediaZeroOutputClockNs;
        trial.deviceLatencyNs = capability.deviceLatencyNs();
        trial.armed = result.success();

        pendingEvents.add(new ArmEvent(
            trial.token(), result.success(), result.alError(), result.callNanos(),
            sourceStartClockNs, mediaZeroRenderClockNs, estimatedMediaZeroOutputClockNs,
            capability.deviceLatencyNs(), result.detail(), Thread.currentThread().getName()
        ));
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
                scheduleMeasurement(trial, "near-media-zero");
            }
            if (!samplePostTargetScheduled && sinceArm >= 7) {
                samplePostTargetScheduled = true;
                scheduleMeasurement(trial, "post-media-zero");
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
            + ", sourceStartDeviceClockNs=" + trial.sourceStartDeviceClockNs
            + ", mediaZeroRenderClockNs=" + trial.mediaZeroRenderClockNs
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

        var rawOffsets = new double[ids.length];
        var mediaOffsets = new double[ids.length];
        var sourceClocks = new long[ids.length];
        var valid = new boolean[ids.length];
        var playing = 0;
        var paused = 0;
        var initial = 0;
        var stopped = 0;
        var validSamples = 0;
        var referenceClockNs = Long.MIN_VALUE;

        for (var i = 0; i < ids.length; i++) {
            var sample = Exp3TimingPrimitives.sampleOffsetClock(ids[i]);
            valid[i] = sample.success();
            if (sample.success()) {
                rawOffsets[i] = sample.sampleOffsetFrames();
                mediaOffsets[i] = rawOffsets[i] - Exp3SyncStream.SILENT_PREROLL_FRAMES;
                sourceClocks[i] = sample.deviceClockNs();
                referenceClockNs = Math.max(referenceClockNs, sample.deviceClockNs());
                validSamples++;
            } else {
                rawOffsets[i] = Double.NaN;
                mediaOffsets[i] = Double.NaN;
                sourceClocks[i] = -1L;
            }

            var state = AL10.alGetSourcei(ids[i], AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_PLAYING) playing++;
            else if (state == AL10.AL_PAUSED) paused++;
            else if (state == AL10.AL_INITIAL) initial++;
            else if (state == AL10.AL_STOPPED) stopped++;
        }

        var compensatedMediaOffsets = new double[ids.length];
        Arrays.fill(compensatedMediaOffsets, Double.NaN);
        var minCompensated = Double.POSITIVE_INFINITY;
        var maxCompensated = Double.NEGATIVE_INFINITY;
        if (referenceClockNs != Long.MIN_VALUE) {
            for (var i = 0; i < ids.length; i++) {
                if (!valid[i]) continue;
                var elapsedFramesToReference = (referenceClockNs - sourceClocks[i])
                    * (double) Exp3SyncStream.SAMPLE_RATE / 1_000_000_000.0;
                var compensated = mediaOffsets[i] + elapsedFramesToReference;
                compensatedMediaOffsets[i] = compensated;
                minCompensated = Math.min(minCompensated, compensated);
                maxCompensated = Math.max(maxCompensated, compensated);
            }
        }

        var spread = validSamples == ids.length && validSamples > 0
            ? maxCompensated - minCompensated
            : -1.0;
        var mediaZeroTargetDeltaNs = referenceClockNs == Long.MIN_VALUE || trial.mediaZeroRenderClockNs < 0
            ? Long.MIN_VALUE
            : trial.mediaZeroRenderClockNs - referenceClockNs;

        pendingEvents.add(new SampleEvent(
            trial.token(), stage, validSamples, spread,
            playing, paused, initial, stopped,
            referenceClockNs == Long.MIN_VALUE ? -1L : referenceClockNs,
            mediaZeroTargetDeltaNs,
            trial.deviceLatencyNs,
            Arrays.toString(rawOffsets), Arrays.toString(compensatedMediaOffsets),
            Thread.currentThread().getName()
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
                    "[EXP-003] scheduled arm token={} count={} success={} alError={} callNanos={} sourceStartDeviceClockNs={} mediaZeroRenderClockNs={} estimatedMediaZeroOutputClockNs={} deviceLatencyNs={} detail={} thread={}",
                    trial.token(), trial.count(), arm.success(), arm.alError(), arm.callNanos(),
                    arm.sourceStartDeviceClockNs(), arm.mediaZeroRenderClockNs(),
                    arm.estimatedMediaZeroOutputClockNs(), arm.deviceLatencyNs(), arm.detail(), arm.thread()
                );
            } else if (event instanceof SampleEvent sample) {
                HighAudio.LOGGER.info(
                    "[EXP-003] scheduled sample token={} count={} stage={} validClockSamples={}/{} clockCompensatedSpreadSamples={} spreadMs={} statesPlaying={} statesPaused={} statesInitial={} statesStopped={} referenceDeviceClockNs={} mediaZeroTargetDeltaNs={} deviceLatencyNs={} rawOffsetsFrames={} compensatedMediaOffsetsFrames={} thread={}",
                    trial.token(), trial.count(), sample.stage(), sample.validClockSamples(), trial.count(),
                    sample.clockCompensatedSpreadSamples(), samplesToMs(sample.clockCompensatedSpreadSamples()),
                    sample.playing(), sample.paused(), sample.initial(), sample.stopped(),
                    sample.referenceDeviceClockNs(), sample.mediaZeroTargetDeltaNs(), sample.deviceLatencyNs(),
                    sample.rawOffsetsFrames(), sample.compensatedMediaOffsetsFrames(), sample.thread()
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
            "[EXP-003] scheduled trial final token={} count={} captures={}/{} armed={} sourceStartDeviceClockNs={} mediaZeroRenderClockNs={} estimatedMediaZeroOutputClockNs={} deviceLatencyNs={} maxPrePauseOffsetSamples={} maxPostRewindOffsetSamples={} closedStreams={} soundDebug={}",
            trial.token(), trial.count(), captures, trial.count(), trial.armed,
            trial.sourceStartDeviceClockNs, trial.mediaZeroRenderClockNs, trial.estimatedMediaZeroOutputClockNs,
            trial.deviceLatencyNs, maxPrePauseOffset, maxPostRewindOffset, closedStreamCount(), safeDebugString()
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

    private static double samplesToMs(double samples) {
        return samples < 0 ? -1.0 : samples * 1000.0 / Exp3SyncStream.SAMPLE_RATE;
    }

    private static long framesToNanos(long frames) {
        return Math.round(frames * 1_000_000_000.0 / Exp3SyncStream.SAMPLE_RATE);
    }

    private static final class ActiveTrial {
        private final long token;
        private final int count;
        private final Map<Integer, Integer> sourceByIndex = new ConcurrentHashMap<>();
        private final AtomicInteger captureCount = new AtomicInteger();
        private volatile SoundEngine engine;
        private volatile boolean armed;
        private volatile long sourceStartDeviceClockNs = -1L;
        private volatile long mediaZeroRenderClockNs = -1L;
        private volatile long estimatedMediaZeroOutputClockNs = -1L;
        private volatile long deviceLatencyNs = -1L;

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
        long token,
        boolean success,
        int alError,
        long callNanos,
        long sourceStartDeviceClockNs,
        long mediaZeroRenderClockNs,
        long estimatedMediaZeroOutputClockNs,
        long deviceLatencyNs,
        String detail,
        String thread
    ) implements DiagnosticEvent {}

    private record SampleEvent(
        long token,
        String stage,
        int validClockSamples,
        double clockCompensatedSpreadSamples,
        int playing,
        int paused,
        int initial,
        int stopped,
        long referenceDeviceClockNs,
        long mediaZeroTargetDeltaNs,
        long deviceLatencyNs,
        String rawOffsetsFrames,
        String compensatedMediaOffsetsFrames,
        String thread
    ) implements DiagnosticEvent {}

    private record FailureEvent(long token, String stage, String detail) implements DiagnosticEvent {}
}
