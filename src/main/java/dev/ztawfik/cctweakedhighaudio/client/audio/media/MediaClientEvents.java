package dev.ztawfik.cctweakedhighaudio.client.audio.media;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Main client event-bus cleanup for production finite playback. */
@EventBusSubscriber(modid = HighAudio.MOD_ID, value = Dist.CLIENT)
public final class MediaClientEvents {
    private MediaClientEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        MediaClientController.tick();
    }
}
