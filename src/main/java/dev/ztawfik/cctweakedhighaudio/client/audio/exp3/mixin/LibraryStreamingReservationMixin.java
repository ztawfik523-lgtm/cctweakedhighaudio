package dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin;

import com.mojang.blaze3d.audio.Library;
import dev.ztawfik.cctweakedhighaudio.HighAudio;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * EXP-003 Part A2 only: rebalance Minecraft's existing channel reservation so the
 * streaming side can reach the project's 16-source stress target without asking
 * OpenAL for any additional sources and without taking source ownership away from
 * Minecraft.
 *
 * <p>This intentionally reproduces the exact vanilla 1.21.1 reservation calculation
 * from the channel-count value before substituting the two CountingChannelPool
 * constructor arguments. If the actual constructor arguments do not match those
 * derived vanilla values, initialization fails loudly rather than silently composing
 * with an unknown audio-pool transform.</p>
 *
 * <p>The experimental rebalance is deliberately conservative on lower-capacity
 * devices: it is enabled only when vanilla already reaches its normal maximum
 * streaming reservation of eight channels. A device for which vanilla derives fewer
 * than eight streaming channels keeps the vanilla split instead of sacrificing a
 * disproportionate part of its static/SFX pool.</p>
 */
@Mixin(Library.class)
public abstract class LibraryStreamingReservationMixin {
    @Unique
    private static final int HIGHAUDIO_TARGET_STREAMING = 16;
    @Unique
    private static final int HIGHAUDIO_VANILLA_STREAMING_MIN = 2;
    @Unique
    private static final int HIGHAUDIO_VANILLA_STREAMING_MAX = 8;
    @Unique
    private static final int HIGHAUDIO_VANILLA_STATIC_MIN = 8;
    @Unique
    private static final int HIGHAUDIO_VANILLA_STATIC_MAX = 255;

    @Unique
    private int highAudio$reportedChannelCount = -1;
    @Unique
    private int highAudio$originalStatic = -1;
    @Unique
    private int highAudio$originalStreaming = -1;
    @Unique
    private int highAudio$newStatic = -1;
    @Unique
    private int highAudio$newStreaming = -1;
    @Unique
    private boolean highAudio$rebalanceApplied;

    @Shadow
    private int getChannelCount() {
        throw new AssertionError("Mixin shadow");
    }

    @Redirect(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Library;getChannelCount()I"
        )
    )
    private int highAudio$captureReservationInput(Library instance) {
        int reported = this.getChannelCount();

        // Minecraft 1.21.1 vanilla policy: reserve sqrt(count), clamped to 2..8,
        // for streaming, then reserve the clamped remainder for static playback.
        int originalStreaming = highAudio$clamp(
            (int) Math.sqrt(reported),
            HIGHAUDIO_VANILLA_STREAMING_MIN,
            HIGHAUDIO_VANILLA_STREAMING_MAX
        );
        int originalStatic = highAudio$clamp(
            reported - originalStreaming,
            HIGHAUDIO_VANILLA_STATIC_MIN,
            HIGHAUDIO_VANILLA_STATIC_MAX
        );

        int combined = originalStatic + originalStreaming;
        boolean eligibleForRebalance = originalStreaming == HIGHAUDIO_VANILLA_STREAMING_MAX;

        int newStreaming = originalStreaming;
        if (eligibleForRebalance) {
            int maximumStreamingWithoutShrinkingStaticBelowFloor = Math.max(
                originalStreaming,
                combined - HIGHAUDIO_VANILLA_STATIC_MIN
            );
            newStreaming = Math.max(
                originalStreaming,
                Math.min(HIGHAUDIO_TARGET_STREAMING, maximumStreamingWithoutShrinkingStaticBelowFloor)
            );
        }
        int newStatic = combined - newStreaming;

        this.highAudio$reportedChannelCount = reported;
        this.highAudio$originalStatic = originalStatic;
        this.highAudio$originalStreaming = originalStreaming;
        this.highAudio$newStatic = newStatic;
        this.highAudio$newStreaming = newStreaming;
        this.highAudio$rebalanceApplied = newStreaming != originalStreaming;

        return reported;
    }

    @ModifyArg(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Library$CountingChannelPool;<init>(I)V",
            ordinal = 0
        ),
        index = 0
    )
    private int highAudio$rebalanceStaticReservation(int vanillaStatic) {
        if (vanillaStatic != this.highAudio$originalStatic) {
            throw new IllegalStateException(
                "[EXP-003] Minecraft static reservation shape changed or another mod transformed it: "
                    + "derived=" + this.highAudio$originalStatic + " actual=" + vanillaStatic
            );
        }
        return this.highAudio$newStatic;
    }

    @ModifyArg(
        method = "init",
        at = @At(
            value = "INVOKE",
            target = "Lcom/mojang/blaze3d/audio/Library$CountingChannelPool;<init>(I)V",
            ordinal = 1
        ),
        index = 0
    )
    private int highAudio$rebalanceStreamingReservation(int vanillaStreaming) {
        if (vanillaStreaming != this.highAudio$originalStreaming) {
            throw new IllegalStateException(
                "[EXP-003] Minecraft streaming reservation shape changed or another mod transformed it: "
                    + "derived=" + this.highAudio$originalStreaming + " actual=" + vanillaStreaming
            );
        }
        return this.highAudio$newStreaming;
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void highAudio$logReservation(CallbackInfo ci) {
        int originalCombined = this.highAudio$originalStatic + this.highAudio$originalStreaming;
        int newCombined = this.highAudio$newStatic + this.highAudio$newStreaming;
        HighAudio.LOGGER.info(
            "[EXP-003] streaming reservation rebalance reportedChannelCount={} originalStatic={} originalStreaming={} newStatic={} newStreaming={} combinedPreserved={} rebalanceApplied={} targetStreaming={}",
            this.highAudio$reportedChannelCount,
            this.highAudio$originalStatic,
            this.highAudio$originalStreaming,
            this.highAudio$newStatic,
            this.highAudio$newStreaming,
            originalCombined == newCombined,
            this.highAudio$rebalanceApplied,
            HIGHAUDIO_TARGET_STREAMING
        );
    }

    @Unique
    private static int highAudio$clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
