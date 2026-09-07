package dev.ztawfik.cctweakedhighaudio.integration.cct.mixin;

import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dev.ztawfik.cctweakedhighaudio.server.MediaServerRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Aborts incomplete uploads and owned playback when a computer detaches from its speaker. */
@Mixin(value = SpeakerPeripheral.class, remap = false)
public abstract class SpeakerPeripheralLifecycleMixin {
    @Shadow
    public abstract UUID getSource();

    @Inject(method = "detach", at = @At("TAIL"))
    private void highaudio$onDetach(IComputerAccess computer, CallbackInfo callback) {
        MediaServerRuntime.onComputerDetached(getSource(), computer.getID());
    }
}
