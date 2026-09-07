package dev.ztawfik.cctweakedhighaudio.integration.cct.mixin;

import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Narrow bridge to the placed speaker context that CC:T keeps protected. */
@Mixin(value = SpeakerPeripheral.class, remap = false)
public interface SpeakerPeripheralAccess {
    @Invoker("getLevel")
    ServerLevel highaudio$getLevel();

    @Invoker("getPosition")
    SpeakerPosition highaudio$getPosition();
}
