package dev.ztawfik.cctweakedhighaudio;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.ComputerCraftAPI;
import dev.ztawfik.cctweakedhighaudio.integration.cct.GenericSourceSelfCheck;
import dev.ztawfik.cctweakedhighaudio.integration.cct.SpeakerGenericSource;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

@Mod(HighAudio.MOD_ID)
public final class HighAudio {
    public static final String MOD_ID = "cctweakedhighaudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    private final SpeakerGenericSource speakerSource = new SpeakerGenericSource();

    public HighAudio() {
        LOGGER.info("CC:Tweaked HighAudio bootstrap loading MILESTONE-003 / EXP-003 capacity and synchronization probe");

        // Keep the accepted MILESTONE-001 integration guarded while later milestones build on top of it.
        GenericSourceSelfCheck.verify(speakerSource);
        ComputerCraftAPI.registerGenericSource(speakerSource);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);

        LOGGER.info("[EXP-001] GenericSource registered id={}", speakerSource.id());
        LOGGER.info("CC:Tweaked HighAudio bootstrap loaded MILESTONE-003 / EXP-003 capacity and synchronization probe");
    }

    private void onServerStarted(ServerStartedEvent event) {
        GenericSourceSelfCheck.verifyLiveServerContext(event.getServer(), speakerSource);
    }
}
