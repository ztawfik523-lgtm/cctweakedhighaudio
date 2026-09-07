package dev.ztawfik.cctweakedhighaudio.integration.cct;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.asm.GenericMethod;
import dan200.computercraft.core.asm.PeripheralMethodSupplier;
import dan200.computercraft.shared.computer.core.ServerContext;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.Set;

/**
 * EXP-001 self-checks for the GenericSource speaker augmentation path.
 */
public final class GenericSourceSelfCheck {
    private static final Set<String> REQUIRED_NATIVE_METHODS = Set.of("playNote", "playSound", "playAudio", "stop");

    private GenericSourceSelfCheck() {
    }

    /**
     * Verify the source definition itself before registration.
     */
    public static void verify(SpeakerGenericSource source) {
        var genericMethods = GenericMethod.getMethods(source).toList();
        var supplier = PeripheralMethodSupplier.create(genericMethods);
        var methods = supplier.getSelfMethods(new ProbeSpeakerPeripheral());

        var missingNative = missingNativeMethods(methods.keySet());
        if (!methods.containsKey("highAudioProbe")) {
            throw new IllegalStateException("[EXP-001] highAudioProbe was not generated for a SpeakerPeripheral subtype");
        }
        if (!missingNative.isEmpty()) {
            throw new IllegalStateException("[EXP-001] SpeakerPeripheral native methods missing from method supplier: " + missingNative);
        }

        HighAudio.LOGGER.info(
            "[EXP-001] GenericSource method-supplier self-check PASS sourceId={} probePresent=true nativeMethodsPresent={}",
            source.id(), REQUIRED_NATIVE_METHODS
        );
    }

    /**
     * Verify the actual method supplier stored in CC:T's live ServerContext.
     *
     * <p>This closes an important gap in the definition-only check above: registration timing and
     * CC:T's disabled_generic_methods filtering are applied when ServerContext is constructed.</p>
     */
    public static boolean verifyLiveServerContext(MinecraftServer server, SpeakerGenericSource source) {
        var methods = ServerContext.get(server).peripheralMethods().getSelfMethods(new ProbeSpeakerPeripheral());
        var probePresent = methods.containsKey("highAudioProbe");
        var missingNative = missingNativeMethods(methods.keySet());

        if (!probePresent || !missingNative.isEmpty()) {
            HighAudio.LOGGER.error(
                "[EXP-001] live ServerContext self-check FAIL sourceId={} probePresent={} missingNativeMethods={}. " +
                    "Check GenericSource registration timing and CC:T disabled_generic_methods configuration.",
                source.id(), probePresent, missingNative
            );
            return false;
        }

        HighAudio.LOGGER.info(
            "[EXP-001] live ServerContext self-check PASS sourceId={} probePresent=true nativeMethodsPresent={}",
            source.id(), REQUIRED_NATIVE_METHODS
        );
        return true;
    }

    private static Set<String> missingNativeMethods(Set<String> methodNames) {
        return REQUIRED_NATIVE_METHODS.stream()
            .filter(name -> !methodNames.contains(name))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
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
