package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin.LibraryDeviceAccessor;
import dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin.SoundEngineLibraryAccessor;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTDeviceClock;
import org.lwjgl.openal.SOFTSourceLatency;
import org.lwjgl.openal.SOFTSourceStartDelay;

/**
 * EXP-003 timing primitives over Minecraft-owned OpenAL sources.
 *
 * <p>All methods that touch OpenAL must run on Minecraft's sound thread. This class never creates,
 * deletes, or owns an OpenAL source/device/context.</p>
 */
public final class Exp3TimingPrimitives {
    private static final double FIXED_32_32_SCALE = 4_294_967_296.0;

    private Exp3TimingPrimitives() {
    }

    public static Capability inspect(SoundEngine engine) {
        try {
            var alCaps = AL.getCapabilities();
            var library = ((SoundEngineLibraryAccessor) (Object) engine).highAudio$getLibrary();
            var device = ((LibraryDeviceAccessor) (Object) library).highAudio$getCurrentDevice();

            var sourceStartDelay = alCaps.AL_SOFT_source_start_delay;
            var sourceLatency = alCaps.AL_SOFT_source_latency;

            // ALC extension availability is device-specific. Do not use ALC.getCapabilities() here:
            // on Minecraft's sound thread it may resolve to LWJGL's process/router capabilities rather
            // than the capabilities of Library.currentDevice. Query the actual Minecraft-owned device.
            var deviceClock = device != 0L && ALC10.alcIsExtensionPresent(device, "ALC_SOFT_device_clock");
            var available = sourceStartDelay && sourceLatency && deviceClock && device != 0L;
            var clockAndLatency = new long[]{-1L, -1L};
            if (available) {
                // The extension defines this pair as an atomic measurement. Keep both values so later
                // session-time mapping can distinguish renderer time from physical output latency.
                SOFTDeviceClock.alcGetInteger64vSOFT(
                    device, SOFTDeviceClock.ALC_DEVICE_CLOCK_LATENCY_SOFT, clockAndLatency
                );
            }

            return new Capability(
                sourceStartDelay,
                sourceLatency,
                deviceClock,
                device,
                clockAndLatency[0],
                clockAndLatency[1],
                available,
                "ok"
            );
        } catch (RuntimeException exception) {
            return new Capability(false, false, false, 0L, -1L, -1L, false,
                exception.getClass().getSimpleName() + ":" + String.valueOf(exception.getMessage()));
        }
    }

    /** Core OpenAL synchronized start used by the EXP-003 `together` path. */
    public static StartResult startTogether(int[] sourceIds) {
        if (sourceIds.length == 0) return new StartResult(false, AL10.AL_INVALID_VALUE, 0L, -1L, "empty-source-set");

        AL10.alGetError();
        AL10.alSourceRewindv(sourceIds);
        var before = System.nanoTime();
        AL10.alSourcePlayv(sourceIds);
        var nanos = System.nanoTime() - before;
        var error = AL10.alGetError();
        return new StartResult(error == AL10.AL_NO_ERROR, error, nanos, -1L, "vector-now");
    }

    /**
     * Capability-gated scheduled group start at an explicit OpenAL device-clock timestamp.
     * The caller owns readiness, public/session timeline mapping, output-latency compensation and
     * media-onset/preroll policy. This method only rewinds and schedules existing Minecraft-owned sources.
     */
    public static StartResult startScheduledAt(SoundEngine engine, int[] sourceIds, long targetDeviceClockNs) {
        if (sourceIds.length == 0) return new StartResult(false, AL10.AL_INVALID_VALUE, 0L, -1L, "empty-source-set");

        var capability = inspect(engine);
        if (!capability.available()) {
            return new StartResult(false, AL10.AL_INVALID_OPERATION, 0L, -1L,
                "scheduled-unavailable:" + capability.detail());
        }
        if (targetDeviceClockNs < 0L) {
            return new StartResult(false, AL10.AL_INVALID_VALUE, 0L, -1L, "negative-target-clock");
        }

        AL10.alGetError();
        AL10.alSourceRewindv(sourceIds);
        var before = System.nanoTime();
        SOFTSourceStartDelay.alSourcePlayAtTimevSOFT(sourceIds, targetDeviceClockNs);
        var nanos = System.nanoTime() - before;
        var error = AL10.alGetError();
        return new StartResult(error == AL10.AL_NO_ERROR, error, nanos, targetDeviceClockNs, "device-clock-scheduled");
    }

    /** Convenience helper for diagnostic callers which only need a future source-start lead. */
    public static StartResult startScheduledAfter(SoundEngine engine, int[] sourceIds, long leadNanos) {
        var capability = inspect(engine);
        if (!capability.available()) {
            return new StartResult(false, AL10.AL_INVALID_OPERATION, 0L, -1L,
                "scheduled-unavailable:" + capability.detail());
        }

        final long targetClockNs;
        try {
            targetClockNs = Math.addExact(capability.deviceClockNs(), Math.max(0L, leadNanos));
        } catch (ArithmeticException exception) {
            return new StartResult(false, AL10.AL_INVALID_VALUE, 0L, -1L, "clock-overflow");
        }
        return startScheduledAt(engine, sourceIds, targetClockNs);
    }

    /**
     * Atomically samples one source's 32.32 fixed-point playback offset and the device clock used for
     * that offset. This is stronger diagnostic evidence than separately querying AL_SAMPLE_OFFSET and
     * then querying the device clock later.
     */
    public static SourceClockSample sampleOffsetClock(int sourceId) {
        try {
            var values = new long[2];
            AL10.alGetError();
            SOFTSourceLatency.alGetSourcei64vSOFT(
                sourceId, SOFTDeviceClock.AL_SAMPLE_OFFSET_CLOCK_SOFT, values
            );
            var error = AL10.alGetError();
            if (error != AL10.AL_NO_ERROR) {
                return new SourceClockSample(false, error, -1L, -1L);
            }
            return new SourceClockSample(true, error, values[0], values[1]);
        } catch (RuntimeException exception) {
            return new SourceClockSample(false, AL10.AL_INVALID_OPERATION, -1L, -1L);
        }
    }

    public record Capability(
        boolean sourceStartDelay,
        boolean sourceLatency,
        boolean deviceClock,
        long device,
        long deviceClockNs,
        long deviceLatencyNs,
        boolean available,
        String detail
    ) {
    }

    public record StartResult(boolean success, int alError, long callNanos, long targetDeviceClockNs, String detail) {
    }

    public record SourceClockSample(
        boolean success,
        int alError,
        long sampleOffsetFixed32_32,
        long deviceClockNs
    ) {
        public double sampleOffsetFrames() {
            return success ? sampleOffsetFixed32_32 / FIXED_32_32_SCALE : -1.0;
        }
    }
}
