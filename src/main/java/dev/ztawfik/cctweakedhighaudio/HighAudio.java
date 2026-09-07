package dev.ztawfik.cctweakedhighaudio;

import com.mojang.logging.LogUtils;
import dev.ztawfik.cctweakedhighaudio.integration.cct.CctIntegrationSelfCheck;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(HighAudio.MOD_ID)
public final class HighAudio {
    public static final String MOD_ID = "cctweakedhighaudio";
    public static final Logger LOGGER = LogUtils.getLogger();

    public HighAudio() {
        LOGGER.info("CC:Tweaked HighAudio bootstrap loading");
        CctIntegrationSelfCheck.verifyExp001();
        LOGGER.info("CC:Tweaked HighAudio bootstrap loaded with EXP-001 diagnostics");
    }
}
