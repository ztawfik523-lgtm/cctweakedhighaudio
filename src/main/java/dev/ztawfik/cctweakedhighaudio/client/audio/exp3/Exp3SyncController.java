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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * EXP-003 Part B: one-command comparison of normal Minecraft start sequencing vs a narrow vector start of
 * Minecraft-owned OpenAL sources. All OpenAL operations happen on Minecraft's sound thread.
 */
public final class Exp3SyncController {
    private static final int FINAL_INACTIVE_TICKS = 10;
    private static final int NEXT_TRIAL_COOLDOWN_TICKS = 4;
    private static final int[] COUNTS = {2, 4, 8, 16};

    private static final List<TrialSpec> SEQUENCE = buildSequence();
    private static final ConcurrentLinkedQueue<SyncEvent> pendingEvents = new ConcurrentLinkedQueue<>();
    private static final List<Exp3SyncSound> sounds = new ArrayList<>();
    private static final List<TrialResult> results = new ArrayList<>();

    private static volatile ActiveTrial activeTrial;
    private static long nextRunToken;
    private static int sequenceIndex;
    private static int trialTicks;
    private static int inactiveTicks;
    private static int cooldownTicks;
    private static int trialCaptures;
    private static int maxPrePauseOffset;
    private static int maxPostRewindOffset;
    private static long vectorCallNanos;
    private static int vectorError;
    private static final Map<String, Integer> spreads = new LinkedHashMap<>();
    private static boolean sample2Scheduled;
    private static boolean sample5Scheduled;
    private static boolean sample10Scheduled;
    private static boolean sequenceRunning;
    private static boolean cancelRequested;
    private static String lastOutcome = "not-run";

    private Exp3SyncController() {
    }

    public static boolean isBusy() {
        return sequenceRunning;
    }

    public static String startCompare() {
        if (sequenceRunning) return "EXP-003 sync comparison is already running";
        if (Exp3CapacityController.isBusy()) return "EXP-003: wait for the capacity probe to finalize before sync_compare";

        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return "EXP-003: no client player/world is active";

        Exp3SyncStream.warmUpSharedPcm();
        pendingEvents.clear();
        results.clear();
        sounds.clear();
        activeTrial = null;
        sequenceIndex = 0;
        cooldownTicks = 0;
        cancelRequested = false;
        sequenceRunning = true;
        lastOutcome = "sync-compare-started";
        startNextTrial();

        return "EXP-003 sync comparison started; it will run 2/4/8/16 sources in both modes automatically";
    }

    public static String stop() {
        if (!sequenceRunning) return "EXP-003: no sync comparison is running";
        cancelRequested = true;
        for (var sound : sounds) Minecraft.getInstance().getSoundManager().stop(sound);
        lastOutcome = "sync-stop-requested";
        return "EXP-003 sync comparison stop requested";
    }

    public static String status() {
        drainEvents();
        var trial = activeTrial;
        if (!sequenceRunning && trial == null) {
            return "EXP-003 sync status: idle, last=" + lastOutcome + ", completedTrials=" + results.size() + "/" + SEQUENCE.size();
        }
        if (trial == null) {
            return "EXP-003 sync status: between trials, completedTrials=" + results.size() + "/" + SEQUENCE.size();
        }
        return "EXP-003 sync status: mode=" + trial.spec().mode().label
            + ", count=" + trial.spec().count()
            + ", captures=" + trialCaptures + "/" + trial.spec().count()
            + ", active=" + activeSoundCount()
            + ", trial=" + (sequenceIndex + 1) + "/" + SEQUENCE.size()
            + ", spreads=" + spreads;
    }

    /** Called by NeoForge on Minecraft's sound thread. */
    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        if (!(event.getSound() instanceof Exp3SyncSound sound)) return;

        var trial = activeTrial;
        if (trial == null || sound.runToken() != trial.token()) return;

        var sourceId = ((ChannelSourceAccessor) (Object) event.getChannel()).highAudio$getSource();
        if (trial.sourceByIndex().putIfAbsent(sound.index(), sourceId) != null) return;

        var prePauseOffset = -1;
        var postRewindOffset = -1;
        if (trial.spec().mode() == SyncMode.VECTOR) {
            prePauseOffset = sampleOffset(sourceId);
            event.getChannel().pause();
            AL10.alSourceRewind(sourceId);
            postRewindOffset = sampleOffset(sourceId);
        }

        trial.engine = event.getEngine();
        var captureCount = trial.captureCount().incrementAndGet();
        pendingEvents.add(new CaptureEvent(
            trial.token(), sound.index(), sourceId, captureCount,
            prePauseOffset, postRewindOffset, Thread.currentThread().getName()
        ));

        if (captureCount == trial.spec().count()) {
            var ids = orderedSourceIds(trial);
            var operationNanos = 0L;
            var error = AL10.AL_NO_ERROR;

            if (trial.spec().mode() == SyncMode.VECTOR) {
                // Clear a stale diagnostic error, reset all already-paused Minecraft-owned sources to the same offset,
                // then use one OpenAL vector operation to start them together. No source is created/deleted here.
                AL10.alGetError();
                AL10.alSourceRewindv(ids);
                var before = System.nanoTime();
                AL10.alSourcePlayv(ids);
                operationNanos = System.nanoTime() - before;
                error = AL10.alGetError();
            }

            trial.allCaptured = true;
            pendingEvents.add(new ArmEvent(trial.token(), operationNanos, error));
            measureAndEnqueue(trial, "start");
        }
    }

    public static void tick() {
        drainEvents();
        if (!sequenceRunning) return;

        var trial = activeTrial;
        if (trial == null) {
            if (cooldownTicks > 0) {
                cooldownTicks--;
                return;
            }
            if (cancelRequested || sequenceIndex >= SEQUENCE.size()) {
                finishSequence(cancelRequested ? "cancelled" : "complete");
            } else {
                startNextTrial();
            }
            return;
        }

        trialTicks++;
        if (trial.allCaptured) {
            if (!sample2Scheduled && trialTicks >= 2) {
                sample2Scheduled = true;
                scheduleMeasurement(trial, "t+2ticks");
            }
            if (!sample5Scheduled && trialTicks >= 5) {
                sample5Scheduled = true;
                scheduleMeasurement(trial, "t+5ticks");
            }
            if (!sample10Scheduled && trialTicks >= 10) {
                sample10Scheduled = true;
                scheduleMeasurement(trial, "t+10ticks");
            }
        }

        var active = activeSoundCount();
        if (active == 0) {
            inactiveTicks++;
        } else {
            inactiveTicks = 0;
        }

        if (trialTicks >= 5 && inactiveTicks >= FINAL_INACTIVE_TICKS) {
            drainEvents();
            finalizeTrial(trial);
        }
    }

    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        if (!sequenceRunning && activeTrial == null) return;

        var prior = activeTrial;
        sounds.clear();
        pendingEvents.clear();
        activeTrial = null;
        sequenceRunning = false;
        cancelRequested = false;
        lastOutcome = "aborted:sound-engine-load";
        HighAudio.LOGGER.info(
            "[EXP-003] sync comparison aborted by SoundEngineLoadEvent priorToken={} completedTrials={} engineIdentity=0x{}",
            prior == null ? 0 : prior.token(), results.size(), Integer.toHexString(System.identityHashCode(event.getEngine()))
        );
    }

    private static void startNextTrial() {
        if (!sequenceRunning || sequenceIndex >= SEQUENCE.size()) return;

        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) {
            finishSequence("aborted:no-player");
            return;
        }

        sounds.clear();
        spreads.clear();
        trialTicks = 0;
        inactiveTicks = 0;
        trialCaptures = 0;
        maxPrePauseOffset = 0;
        maxPostRewindOffset = 0;
        vectorCallNanos = 0;
        vectorError = AL10.AL_NO_ERROR;
        sample2Scheduled = false;
        sample5Scheduled = false;
        sample10Scheduled = false;

        var spec = SEQUENCE.get(sequenceIndex);
        var token = ++nextRunToken;
        var trial = new ActiveTrial(token, spec);
        activeTrial = trial;

        var origin = player.position();
        for (var i = 0; i < spec.count(); i++) {
            var angle = Math.PI * 2.0 * i / spec.count();
            var position = origin.add(Math.cos(angle) * 1.25, 0.0, Math.sin(angle) * 1.25);
            var sound = new Exp3SyncSound(token, i, position, new Exp3SyncStream());
            sounds.add(sound);
            minecraft.getSoundManager().play(sound);
        }

        HighAudio.LOGGER.info(
            "[EXP-003] sync trial requested token={} trial={}/{} mode={} count={} silentPrerollFrames={} soundDebug={}",
            token, sequenceIndex + 1, SEQUENCE.size(), spec.mode().label, spec.count(),
            Exp3SyncStream.SILENT_PREROLL_FRAMES, safeDebugString()
        );
    }

    private static void finalizeTrial(ActiveTrial trial) {
        if (activeTrial != trial) return;

        var result = new TrialResult(
            trial.spec().mode(), trial.spec().count(), trialCaptures,
            maxPrePauseOffset, maxPostRewindOffset,
            spreads.getOrDefault("start", -1),
            spreads.getOrDefault("t+2ticks", -1),
            spreads.getOrDefault("t+5ticks", -1),
            spreads.getOrDefault("t+10ticks", -1),
            vectorCallNanos, vectorError
        );
        results.add(result);

        HighAudio.LOGGER.info(
            "[EXP-003] sync trial final token={} mode={} count={} captures={}/{} maxPrePauseOffsetSamples={} maxPostRewindOffsetSamples={} startSpreadSamples={} t2SpreadSamples={} t5SpreadSamples={} t10SpreadSamples={} maxMeasuredSpreadSamples={} maxMeasuredSpreadMs={} vectorCallNanos={} vectorError={} closedStreams={} soundDebug={}",
            trial.token(), trial.spec().mode().label, trial.spec().count(), trialCaptures, trial.spec().count(),
            maxPrePauseOffset, maxPostRewindOffset,
            result.startSpread(), result.t2Spread(), result.t5Spread(), result.t10Spread(),
            result.maxMeasuredSpread(), samplesToMs(result.maxMeasuredSpread()), vectorCallNanos, vectorError,
            closedStreamCount(), safeDebugString()
        );

        activeTrial = null;
        sounds.clear();
        sequenceIndex++;
        cooldownTicks = NEXT_TRIAL_COOLDOWN_TICKS;
    }

    private static void finishSequence(String outcome) {
        sequenceRunning = false;
        activeTrial = null;
        sounds.clear();
        pendingEvents.clear();
        lastOutcome = "sync-compare-" + outcome;

        var highLevel = new StringBuilder();
        var vector = new StringBuilder();
        var vectorMaxPrePause = 0;
        for (var result : results) {
            var target = result.mode() == SyncMode.HIGH_LEVEL ? highLevel : vector;
            if (!target.isEmpty()) target.append(',');
            target.append(result.count()).append(':').append(result.maxMeasuredSpread());
            if (result.mode() == SyncMode.VECTOR) vectorMaxPrePause = Math.max(vectorMaxPrePause, result.maxPrePauseOffset());
        }

        HighAudio.LOGGER.info(
            "[EXP-003] sync comparison {} completedTrials={}/{} highLevelMaxSpreadSamples=[{}] vectorMaxSpreadSamples=[{}] vectorMaxPrePauseOffsetSamples={} sampleRate={} note=max spread uses t+2/t+5/t+10 sound-thread AL_SAMPLE_OFFSET samples",
            outcome, results.size(), SEQUENCE.size(), highLevel, vector, vectorMaxPrePause, Exp3SyncStream.SAMPLE_RATE
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
            pendingEvents.add(new FailureEvent(trial.token(), stage, exception.getClass().getSimpleName() + ":" + exception.getMessage()));
        }
    }

    /** Must run on Minecraft's sound thread. */
    private static void measureAndEnqueue(ActiveTrial trial, String stage) {
        var ids = orderedSourceIds(trial);
        if (ids.length != trial.spec().count()) return;

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

        var spread = min == Integer.MAX_VALUE ? -1 : max - min;
        pendingEvents.add(new SampleEvent(
            trial.token(), stage, min == Integer.MAX_VALUE ? -1 : min,
            max == Integer.MIN_VALUE ? -1 : max, spread,
            playing, paused, initial, stopped, Arrays.toString(offsets), Thread.currentThread().getName()
        ));
    }

    private static int[] orderedSourceIds(ActiveTrial trial) {
        var ids = new int[trial.spec().count()];
        for (var i = 0; i < ids.length; i++) {
            var id = trial.sourceByIndex().get(i);
            if (id == null) return new int[0];
            ids[i] = id;
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

    private static void drainEvents() {
        SyncEvent event;
        while ((event = pendingEvents.poll()) != null) {
            var trial = activeTrial;
            if (trial == null || event.token() != trial.token()) continue;

            if (event instanceof CaptureEvent capture) {
                trialCaptures = Math.max(trialCaptures, capture.captureCount());
                if (capture.prePauseOffset() >= 0) maxPrePauseOffset = Math.max(maxPrePauseOffset, capture.prePauseOffset());
                if (capture.postRewindOffset() >= 0) maxPostRewindOffset = Math.max(maxPostRewindOffset, capture.postRewindOffset());
                HighAudio.LOGGER.info(
                    "[EXP-003] sync capture token={} mode={} index={} sourceId={} captures={}/{} prePauseOffsetSamples={} postRewindOffsetSamples={} eventThread={}",
                    trial.token(), trial.spec().mode().label, capture.index(), capture.sourceId(), capture.captureCount(),
                    trial.spec().count(), capture.prePauseOffset(), capture.postRewindOffset(), capture.thread()
                );
            } else if (event instanceof ArmEvent arm) {
                vectorCallNanos = arm.vectorCallNanos();
                vectorError = arm.openAlError();
            } else if (event instanceof SampleEvent sample) {
                spreads.put(sample.stage(), sample.spread());
                HighAudio.LOGGER.info(
                    "[EXP-003] sync sample token={} mode={} count={} stage={} minOffsetSamples={} maxOffsetSamples={} spreadSamples={} spreadMs={} statesPlaying={} statesPaused={} statesInitial={} statesStopped={} offsets={} sampleThread={}",
                    trial.token(), trial.spec().mode().label, trial.spec().count(), sample.stage(), sample.min(), sample.max(),
                    sample.spread(), samplesToMs(sample.spread()), sample.playing(), sample.paused(), sample.initial(),
                    sample.stopped(), sample.offsets(), sample.thread()
                );
            } else if (event instanceof FailureEvent failure) {
                HighAudio.LOGGER.error(
                    "[EXP-003] sync measurement failure token={} mode={} stage={} error={}",
                    trial.token(), trial.spec().mode().label, failure.stage(), failure.error()
                );
            }
        }
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

    private static double samplesToMs(int samples) {
        return samples < 0 ? -1.0 : samples * 1000.0 / Exp3SyncStream.SAMPLE_RATE;
    }

    private static List<TrialSpec> buildSequence() {
        var sequence = new ArrayList<TrialSpec>();
        for (var count : COUNTS) {
            sequence.add(new TrialSpec(SyncMode.HIGH_LEVEL, count));
            sequence.add(new TrialSpec(SyncMode.VECTOR, count));
        }
        return List.copyOf(sequence);
    }

    private enum SyncMode {
        HIGH_LEVEL("high-level"),
        VECTOR("vector");

        private final String label;

        SyncMode(String label) {
            this.label = label;
        }
    }

    private record TrialSpec(SyncMode mode, int count) {
    }

    private static final class ActiveTrial {
        private final long token;
        private final TrialSpec spec;
        private final ConcurrentHashMap<Integer, Integer> sourceByIndex = new ConcurrentHashMap<>();
        private final AtomicInteger captureCount = new AtomicInteger();
        private volatile SoundEngine engine;
        private volatile boolean allCaptured;

        private ActiveTrial(long token, TrialSpec spec) {
            this.token = token;
            this.spec = spec;
        }

        long token() {
            return token;
        }

        TrialSpec spec() {
            return spec;
        }

        ConcurrentHashMap<Integer, Integer> sourceByIndex() {
            return sourceByIndex;
        }

        AtomicInteger captureCount() {
            return captureCount;
        }
    }

    private record TrialResult(
        SyncMode mode,
        int count,
        int captures,
        int maxPrePauseOffset,
        int maxPostRewindOffset,
        int startSpread,
        int t2Spread,
        int t5Spread,
        int t10Spread,
        long vectorCallNanos,
        int vectorError
    ) {
        int maxMeasuredSpread() {
            return Math.max(Math.max(t2Spread, t5Spread), t10Spread);
        }
    }

    private sealed interface SyncEvent permits CaptureEvent, ArmEvent, SampleEvent, FailureEvent {
        long token();
    }

    private record CaptureEvent(
        long token, int index, int sourceId, int captureCount,
        int prePauseOffset, int postRewindOffset, String thread
    ) implements SyncEvent {
    }

    private record ArmEvent(long token, long vectorCallNanos, int openAlError) implements SyncEvent {
    }

    private record SampleEvent(
        long token, String stage, int min, int max, int spread,
        int playing, int paused, int initial, int stopped, String offsets, String thread
    ) implements SyncEvent {
    }

    private record FailureEvent(long token, String stage, String error) implements SyncEvent {
    }
}
