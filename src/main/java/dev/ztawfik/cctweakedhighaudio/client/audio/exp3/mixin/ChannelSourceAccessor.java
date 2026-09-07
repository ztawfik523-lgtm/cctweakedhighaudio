package dev.ztawfik.cctweakedhighaudio.client.audio.exp3.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** EXP-003-only accessor for the Minecraft-owned OpenAL source id. */
@Mixin(Channel.class)
public interface ChannelSourceAccessor {
    @Accessor("source")
    int highAudio$getSource();
}
