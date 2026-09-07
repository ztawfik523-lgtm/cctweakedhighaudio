package dev.ztawfik.cctweakedhighaudio.client.audio.media;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

/** Clears renderer-local session state when Minecraft rebuilds its sound engine. */
@EventBusSubscriber(modid = HighAudio.MOD_ID, value = Dist.CLIENT)
public final class MediaClientModEvents {
    private MediaClientModEvents() {
    }

    @SubscribeEvent
    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        MediaClientController.onSoundEngineLoad();
    }
}
