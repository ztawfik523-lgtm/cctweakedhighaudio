package dev.ztawfik.cctweakedhighaudio.client.audio.exp2;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

/** Mod-bus hook for SoundEngine construction/reload. */
@EventBusSubscriber(modid = HighAudio.MOD_ID, value = Dist.CLIENT)
public final class Exp2ClientModEvents {
    private Exp2ClientModEvents() {
    }

    @SubscribeEvent
    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        Exp2AudioController.onSoundEngineLoad(event);
    }
}
