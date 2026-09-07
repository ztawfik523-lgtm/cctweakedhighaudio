package dev.ztawfik.cctweakedhighaudio.integration.cct;

import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.ObjectArguments;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.asm.GenericMethod;
import dan200.computercraft.core.asm.PeripheralMethodSupplier;
import dan200.computercraft.shared.computer.core.ServerContext;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.Set;

/**
 * EXP-001 self-checks for the GenericSource speaker augmentation path.
 */
public final class GenericSourceSelfCheck {
    private static final Set<String> REQUIRED_NATIVE_METHODS = Set.of("playNote", "playSound", "playAudio", "stop");
    private static final int SELF_CHECK_COMPUTER_ID = 9001;
    private static final String SELF_CHECK_ATTACHMENT = "exp001-selfcheck";

    private GenericSourceSelfCheck() {
    }

    /**
     * Verify the source definition itself before registration, including one invocation through CC:T's generated wrapper.
     */
    public static void verify(SpeakerGenericSource source) {
        var genericMethods = GenericMethod.getMethods(source).toList();
        var supplier = PeripheralMethodSupplier.create(genericMethods);
        var target = new ProbeSpeakerPeripheral();
        var methods = supplier.getSelfMethods(target);

        var missingNative = missingNativeMethods(methods.keySet());
        var probe = methods.get("highAudioProbe");
        if (probe == null) {
            throw new IllegalStateException("[EXP-001] highAudioProbe was not generated for a SpeakerPeripheral subtype");
        }
        if (!missingNative.isEmpty()) {
            throw new IllegalStateException("[EXP-001] SpeakerPeripheral native methods missing from method supplier: " + missingNative);
        }
        if (supplier.getSelfMethods(new UnrelatedPeripheral()).containsKey("highAudioProbe")) {
            throw new IllegalStateException("[EXP-001] highAudioProbe leaked onto an unrelated IPeripheral");
        }

        try {
            var result = probe.apply(target, fakeLuaContext(), fakeComputerAccess(), new ObjectArguments());
            var values = result.getResult();
            if (result.getCallback() != null || values == null || values.length != 1 || !(values[0] instanceof Map<?, ?> map)) {
                throw new IllegalStateException("highAudioProbe generated wrapper did not return one immediate map result");
            }
            if (!"EXP-001".equals(map.get("experiment"))
                || !"generic_source".equals(map.get("integration"))
                || !Integer.valueOf(SELF_CHECK_COMPUTER_ID).equals(map.get("computerId"))
                || !SELF_CHECK_ATTACHMENT.equals(map.get("attachment"))) {
                throw new IllegalStateException("highAudioProbe generated wrapper returned unexpected diagnostics: " + map);
            }
        } catch (Exception e) {
            throw new IllegalStateException("[EXP-001] generated highAudioProbe invocation self-check failed", e);
        }

        HighAudio.LOGGER.info(
            "[EXP-001] GenericSource method-supplier self-check PASS sourceId={} probePresent=true probeInvocation=true speakerOnly=true nativeMethodsPresent={}",
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
        var supplier = ServerContext.get(server).peripheralMethods();
        var methods = supplier.getSelfMethods(new ProbeSpeakerPeripheral());
        var probePresent = methods.containsKey("highAudioProbe");
        var missingNative = missingNativeMethods(methods.keySet());
        var leakedToUnrelatedPeripheral = supplier.getSelfMethods(new UnrelatedPeripheral()).containsKey("highAudioProbe");

        if (!probePresent || !missingNative.isEmpty() || leakedToUnrelatedPeripheral) {
            HighAudio.LOGGER.error(
                "[EXP-001] live ServerContext self-check FAIL sourceId={} probePresent={} missingNativeMethods={} leakedToUnrelatedPeripheral={}. " +
                    "Check GenericSource registration timing, target matching, and CC:T disabled_generic_methods configuration.",
                source.id(), probePresent, missingNative, leakedToUnrelatedPeripheral
            );
            return false;
        }

        HighAudio.LOGGER.info(
            "[EXP-001] live ServerContext self-check PASS sourceId={} probePresent=true speakerOnly=true nativeMethodsPresent={}",
            source.id(), REQUIRED_NATIVE_METHODS
        );
        return true;
    }

    private static Set<String> missingNativeMethods(Set<String> methodNames) {
        return REQUIRED_NATIVE_METHODS.stream()
            .filter(name -> !methodNames.contains(name))
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static ILuaContext fakeLuaContext() {
        return (ILuaContext) Proxy.newProxyInstance(
            ILuaContext.class.getClassLoader(),
            new Class<?>[]{ ILuaContext.class },
            (proxy, method, args) -> proxyObjectMethod(proxy, method.getName(), args)
        );
    }

    private static IComputerAccess fakeComputerAccess() {
        return (IComputerAccess) Proxy.newProxyInstance(
            IComputerAccess.class.getClassLoader(),
            new Class<?>[]{ IComputerAccess.class },
            (proxy, method, args) -> switch (method.getName()) {
                case "getID" -> SELF_CHECK_COMPUTER_ID;
                case "getAttachmentName" -> SELF_CHECK_ATTACHMENT;
                default -> proxyObjectMethod(proxy, method.getName(), args);
            }
        );
    }

    private static Object proxyObjectMethod(Object proxy, String methodName, Object[] args) {
        return switch (methodName) {
            case "toString" -> "EXP-001 self-check proxy";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == (args == null || args.length == 0 ? null : args[0]);
            default -> throw new UnsupportedOperationException("Unexpected self-check proxy method: " + methodName);
        };
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

    private static final class UnrelatedPeripheral implements IPeripheral {
        @Override
        public String getType() {
            return "exp001_unrelated";
        }

        @Override
        public boolean equals(IPeripheral other) {
            return this == other;
        }
    }
}
