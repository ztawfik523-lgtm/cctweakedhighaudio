package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

/** Mod-bus sound-engine lifecycle hook for EXP-003 diagnostics. */
@EventBusSubscriber(modid = HighAudio.MOD_ID, value = Dist.CLIENT)
public final class Exp3ClientModEvents {
    private Exp3ClientModEvents() {
    }

    @SubscribeEvent
    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        Exp3CapacityController.onSoundEngineLoad(event);
        Exp3SyncController.onSoundEngineLoad(event);
        Exp3TimedStartDiagnostics.onSoundEngineLoad(event);
    }
}
