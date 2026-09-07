package dev.ztawfik.cctweakedhighaudio.integration.cct;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.asm.GenericMethod;
import dan200.computercraft.core.asm.PeripheralMethodSupplier;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * EXP-001 startup self-check for the GenericSource speaker augmentation path.
 */
public final class GenericSourceSelfCheck {
    private static final Set<String> REQUIRED_NATIVE_METHODS = Set.of("playNote", "playSound", "playAudio", "stop");

    private GenericSourceSelfCheck() {
    }

    public static void verify(SpeakerGenericSource source) {
        var genericMethods = GenericMethod.getMethods(source).toList();
        var supplier = PeripheralMethodSupplier.create(genericMethods);
        var methods = supplier.getSelfMethods(new ProbeSpeakerPeripheral());

        if (!methods.containsKey("highAudioProbe")) {
            throw new IllegalStateException("[EXP-001] highAudioProbe was not generated for a SpeakerPeripheral subtype");
        }

        var missingNative = REQUIRED_NATIVE_METHODS.stream().filter(name -> !methods.containsKey(name)).sorted().toList();
        if (!missingNative.isEmpty()) {
            throw new IllegalStateException("[EXP-001] SpeakerPeripheral native methods missing from method supplier: " + missingNative);
        }

        HighAudio.LOGGER.info(
            "[EXP-001] GenericSource method-supplier self-check PASS sourceId={} probePresent=true nativeMethodsPresent={}",
            source.id(), REQUIRED_NATIVE_METHODS
        );
    }

    /**
     * Runtime class used only for exact CC:T method discovery. World-facing methods are never invoked here.
     */
    private static final class ProbeSpeakerPeripheral extends SpeakerPeripheral {
        @Override
        protected ServerLevel getLevel() {
            return null;
        }

        @Override
        protected SpeakerPosition getPosition() {
            return null;
        }

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }
}
