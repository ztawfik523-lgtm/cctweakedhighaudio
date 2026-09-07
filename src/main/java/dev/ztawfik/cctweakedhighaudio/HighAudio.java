package dev.ztawfik.cctweakedhighaudio;

import com.mojang.logging.LogUtils;
import dan200.computercraft.api.ComputerCraftAPI;
import dev.ztawfik.cctweakedhighaudio.integration.cct.GenericSourceSelfCheck;
import dev.ztawfik.cctweakedhighaudio.integration.cct.SpeakerGenericSource;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(HighAudio.MOD_ID)
public final class HighAudio {
    public static final String MOD_ID = "cctweakedhighaudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HighAudio() {
        LOGGER.info("CC:Tweaked HighAudio bootstrap loading MILESTONE-001 / EXP-001 GenericSource probe");

        var source = new SpeakerGenericSource();
        GenericSourceSelfCheck.verify(source);
        ComputerCraftAPI.registerGenericSource(source);

        LOGGER.info("[EXP-001] GenericSource registered id={}", source.id());
        LOGGER.info("CC:Tweaked HighAudio bootstrap loaded MILESTONE-001 / EXP-001 GenericSource probe");
    }
}
