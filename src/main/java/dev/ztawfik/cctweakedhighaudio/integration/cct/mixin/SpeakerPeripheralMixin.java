package dev.ztawfik.cctweakedhighaudio.integration.cct.mixin;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.integration.cct.CctIntegrationSelfCheck;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * EXP-001 only: add one diagnostic Lua method to CC:T 1.120.0's existing speaker peripheral.
 *
 * <p>This deliberately does not replace native methods, attach/detach, equality, or peripheral ownership.</p>
 */
@Mixin(targets = CctIntegrationSelfCheck.SPEAKER_PERIPHERAL_CLASS, remap = false)
public abstract class SpeakerPeripheralMixin {
    @Shadow
    public abstract UUID getSource();

    @LuaFunction
    public final Map<String, Object> highAudioProbe(IComputerAccess computer) {
        var runtimeClass = getClass().getName();
        var emitterKind = "speaker";
        if (runtimeClass.equals("dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral")) {
            emitterKind = "block";
        } else if (runtimeClass.equals("dan200.computercraft.shared.turtle.upgrades.TurtleSpeaker$Peripheral")) {
            emitterKind = "turtle";
        } else if (runtimeClass.equals("dan200.computercraft.shared.pocket.peripherals.PocketSpeakerPeripheral")) {
            emitterKind = "pocket";
        }

        var nativeSource = getSource().toString();
        var identityHash = "0x" + Integer.toHexString(System.identityHashCode(this));
        var attachmentName = computer.getAttachmentName();
        var computerId = computer.getID();
        var threadName = Thread.currentThread().getName();

        var result = new LinkedHashMap<String, Object>();
        result.put("experiment", "EXP-001");
        result.put("probeVersion", 1);
        result.put("mixinTarget", CctIntegrationSelfCheck.SPEAKER_PERIPHERAL_CLASS);
        result.put("emitterKind", emitterKind);
        result.put("runtimeClass", runtimeClass);
        result.put("nativeSource", nativeSource);
        result.put("identityHash", identityHash);
        result.put("computerId", computerId);
        result.put("attachment", attachmentName);
        result.put("thread", threadName);

        HighAudio.LOGGER.info(
            "[EXP-001] highAudioProbe computerId={} attachment={} emitterKind={} runtimeClass={} nativeSource={} identityHash={} thread={}",
            computerId, attachmentName, emitterKind, runtimeClass, nativeSource, identityHash, threadName
        );

        return result;
    }
}
