package dev.ztawfik.cctweakedhighaudio;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.ComputerCraftAPI;
import dev.ztawfik.cctweakedhighaudio.config.HighAudioConfig;
import dev.ztawfik.cctweakedhighaudio.integration.cct.GenericSourceSelfCheck;
import dev.ztawfik.cctweakedhighaudio.integration.cct.SpeakerGenericSource;
import dev.ztawfik.cctweakedhighaudio.network.MediaNetworking;
import dev.ztawfik.cctweakedhighaudio.server.MediaServerRuntime;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(HighAudio.MOD_ID)
public final class HighAudio {
    public static final String MOD_ID = "cctweakedhighaudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final SpeakerGenericSource speakerSource = new SpeakerGenericSource();

    public HighAudio(IEventBus modBus, ModContainer modContainer) {
        LOGGER.info("CC:Tweaked HighAudio loading");

        modContainer.registerConfig(ModConfig.Type.COMMON, HighAudioConfig.SPEC);
        modBus.addListener(HighAudioConfig::onLoad);

        // Keep the accepted MILESTONE-001 integration guarded while later milestones build on top of it.
        GenericSourceSelfCheck.verify(speakerSource);
        ComputerCraftAPI.registerGenericSource(speakerSource);
        modBus.addListener(MediaNetworking::register);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        NeoForge.EVENT_BUS.addListener(MediaServerRuntime::onServerTick);
        NeoForge.EVENT_BUS.addListener(MediaServerRuntime::onServerStopping);
        NeoForge.EVENT_BUS.addListener(MediaServerRuntime::onPlayerLoggedOut);

        LOGGER.info("[EXP-001] GenericSource registered id={}", speakerSource.id());
        LOGGER.info("CC:Tweaked HighAudio loaded");
    }

    private void onServerStarted(ServerStartedEvent event) {
        MediaServerRuntime.onServerStarted(event);
        GenericSourceSelfCheck.verifyLiveServerContext(event.getServer(), speakerSource);
    }
}
