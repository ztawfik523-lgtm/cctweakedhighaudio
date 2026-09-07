package dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin;

import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EXP-003-only accessor used to keep OpenAL measurements on Minecraft's sound thread. */
@Mixin(SoundEngine.class)
public interface SoundEngineExecutorAccessor {
    @Accessor("executor")
    SoundEngineExecutor highAudio$getExecutor();
}
