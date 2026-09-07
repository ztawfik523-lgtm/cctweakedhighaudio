package dev.ztawfik.cctweakedhighaudio.integration.cct;

import dan200.computercraft.api.lua.GenericSource;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dev.ztawfik.cctweakedhighaudio.HighAudio;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EXP-001 comparison only: add a diagnostic method through CC:T's GenericSource method system.
 *
 * <p>The registration API is public, but the target type is intentionally the exact CC:T 1.120.0
 * implementation class so the method is exposed only on speakers rather than every IPeripheral.</p>
 */
public final class SpeakerGenericSource implements GenericSource {
    @Override
    public String id() {
        return "cctweakedhighaudio:speaker";
    }

    @LuaFunction
    public static Map<String, Object> highAudioProbe(SpeakerPeripheral speaker, IComputerAccess computer) {
        var runtimeClass = speaker.getClass().getName();
        var emitterKind = "speaker";
        if (runtimeClass.equals("dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral")) {
            emitterKind = "block";
        } else if (runtimeClass.equals("dan200.computercraft.shared.turtle.upgrades.TurtleSpeaker$Peripheral")) {
            emitterKind = "turtle";
        } else if (runtimeClass.equals("dan200.computercraft.shared.pocket.peripherals.PocketSpeakerPeripheral")) {
            emitterKind = "pocket";
        }

        var nativeSource = speaker.getSource().toString();
        var identityHash = "0x" + Integer.toHexString(System.identityHashCode(speaker));
        var attachmentName = computer.getAttachmentName();
        var computerId = computer.getID();
        var threadName = Thread.currentThread().getName();

        var result = new LinkedHashMap<String, Object>();
        result.put("experiment", "EXP-001-GENERICSOURCE");
        result.put("probeVersion", 1);
        result.put("integration", "generic_source");
        result.put("emitterKind", emitterKind);
        result.put("runtimeClass", runtimeClass);
        result.put("nativeSource", nativeSource);
        result.put("identityHash", identityHash);
        result.put("computerId", computerId);
        result.put("attachment", attachmentName);
        result.put("thread", threadName);

        HighAudio.LOGGER.info(
            "[EXP-001-GENERIC] highAudioProbe computerId={} attachment={} emitterKind={} runtimeClass={} nativeSource={} identityHash={} thread={}",
            computerId, attachmentName, emitterKind, runtimeClass, nativeSource, identityHash, threadName
        );

        return result;
    }
}
