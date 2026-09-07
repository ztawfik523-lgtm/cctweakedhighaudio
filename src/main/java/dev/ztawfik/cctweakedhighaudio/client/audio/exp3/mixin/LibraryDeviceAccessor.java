package dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin;

import com.mojang.blaze3d.audio.Library;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EXP-003-only read accessor for Minecraft's current OpenAL device handle. */
@Mixin(Library.class)
public interface LibraryDeviceAccessor {
    @Accessor("currentDevice")
    long highAudio$getCurrentDevice();
}
