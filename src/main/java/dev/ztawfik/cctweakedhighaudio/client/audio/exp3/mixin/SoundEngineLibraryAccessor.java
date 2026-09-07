package dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin;

import com.mojang.blaze3d.audio.Library;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EXP-003-only read accessor for Minecraft's existing audio Library. */
@Mixin(SoundEngine.class)
public interface SoundEngineLibraryAccessor {
    @Accessor("library")
    Library highAudio$getLibrary();
}
