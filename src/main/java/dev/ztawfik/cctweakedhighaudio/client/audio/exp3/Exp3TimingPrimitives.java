package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin.LibraryDeviceAccessor;
import dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin.SoundEngineLibraryAccessor;
import net.minecraft.client.sounds.SoundEngine;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.SOFTDeviceClock;
import org.lwjgl.openal.SOFTSourceStartDelay;

/**
 * EXP-003 timing primitives over Minecraft-owned OpenAL sources.
 *
 * <p>All methods that touch OpenAL must run on Minecraft's sound thread. This class never creates,
 * deletes, or owns an OpenAL source/device/context.</p>
 */
public final class Exp3TimingPrimitives {
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
            var clockNs = available
                ? SOFTDeviceClock.alcGetInteger64vSOFT(device, SOFTDeviceClock.ALC_DEVICE_CLOCK_SOFT)
                : -1L;

            return new Capability(sourceStartDelay, sourceLatency, deviceClock, device, clockNs, available, "ok");
        } catch (RuntimeException exception) {
            return new Capability(false, false, false, 0L, -1L, false,
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
     * Capability-gated scheduled group start at the current device clock plus {@code leadNanos}.
     * The caller owns readiness and media-onset/preroll policy; this method only rewinds and schedules
     * existing Minecraft-owned sources.
     */
    public static StartResult startScheduled(SoundEngine engine, int[] sourceIds, long leadNanos) {
        if (sourceIds.length == 0) return new StartResult(false, AL10.AL_INVALID_VALUE, 0L, -1L, "empty-source-set");

        var capability = inspect(engine);
        if (!capability.available()) {
            return new StartResult(false, AL10.AL_INVALID_OPERATION, 0L, -1L,
                "scheduled-unavailable:" + capability.detail());
        }

        var nonNegativeLead = Math.max(0L, leadNanos);
        final long targetClockNs;
        try {
            targetClockNs = Math.addExact(capability.deviceClockNs(), nonNegativeLead);
        } catch (ArithmeticException exception) {
            return new StartResult(false, AL10.AL_INVALID_VALUE, 0L, -1L, "clock-overflow");
        }

        AL10.alGetError();
        AL10.alSourceRewindv(sourceIds);
        var before = System.nanoTime();
        SOFTSourceStartDelay.alSourcePlayAtTimevSOFT(sourceIds, targetClockNs);
        var nanos = System.nanoTime() - before;
        var error = AL10.alGetError();
        return new StartResult(error == AL10.AL_NO_ERROR, error, nanos, targetClockNs, "device-clock-scheduled");
    }

    public record Capability(
        boolean sourceStartDelay,
        boolean sourceLatency,
        boolean deviceClock,
        long device,
        long deviceClockNs,
        boolean available,
        String detail
    ) {
    }

    public record StartResult(boolean success, int alError, long callNanos, long targetDeviceClockNs, String detail) {
    }
}
