package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin.SoundEngineExecutorAccessor;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

/** Automatic EXP-003 capability probe for the optional device-clock scheduled-start path. */
public final class Exp3TimedStartDiagnostics {
    private static volatile Exp3TimingPrimitives.Capability lastCapability;

    private Exp3TimedStartDiagnostics() {
    }

    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        try {
            var executor = ((SoundEngineExecutorAccessor) (Object) event.getEngine()).highAudio$getExecutor();
            executor.execute(() -> {
                var capability = Exp3TimingPrimitives.inspect(event.getEngine());
                lastCapability = capability;
                HighAudio.LOGGER.info(
                    "[EXP-003] timed-start capability sourceStartDelay={} sourceLatency={} deviceClock={} device=0x{} deviceClockNs={} available={} detail={} thread={}",
                    capability.sourceStartDelay(), capability.sourceLatency(), capability.deviceClock(),
                    Long.toHexString(capability.device()), capability.deviceClockNs(), capability.available(),
                    capability.detail(), Thread.currentThread().getName()
                );
            });
        } catch (RuntimeException exception) {
            lastCapability = new Exp3TimingPrimitives.Capability(false, false, false, 0L, -1L, false,
                exception.getClass().getSimpleName() + ":" + String.valueOf(exception.getMessage()));
            HighAudio.LOGGER.warn("[EXP-003] timed-start capability scheduling failed", exception);
        }
    }

    public static Exp3TimingPrimitives.Capability lastCapability() {
        return lastCapability;
    }
}
