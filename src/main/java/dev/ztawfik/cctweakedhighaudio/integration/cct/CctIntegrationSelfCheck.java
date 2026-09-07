package dev.ztawfik.cctweakedhighaudio.integration.cct;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dev.ztawfik.cctweakedhighaudio.HighAudio;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Startup diagnostics for the exact CC:T 1.120.0 integration surface used by EXP-001.
 */
public final class CctIntegrationSelfCheck {
    public static final String SPEAKER_PERIPHERAL_CLASS = "dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral";

    private static final Set<String> REQUIRED_NATIVE_METHODS = Set.of("playNote", "playSound", "playAudio", "stop");

    private CctIntegrationSelfCheck() {
    }

    public static void verifyExp001() {
        try {
            var target = Class.forName(SPEAKER_PERIPHERAL_CLASS, false, CctIntegrationSelfCheck.class.getClassLoader());
            var probe = target.getMethod("highAudioProbe", IComputerAccess.class);

            if (probe.getDeclaringClass() != target) {
                throw new IllegalStateException("highAudioProbe was not merged directly into " + SPEAKER_PERIPHERAL_CLASS);
            }
            if (!Modifier.isPublic(probe.getModifiers()) || !Modifier.isFinal(probe.getModifiers())) {
                throw new IllegalStateException("highAudioProbe must be public final for CC:T Lua discovery");
            }
            if (probe.getAnnotation(LuaFunction.class) == null) {
                throw new IllegalStateException("highAudioProbe is missing its runtime @LuaFunction annotation");
            }

            var methodNames = Arrays.stream(target.getMethods()).map(Method::getName).collect(Collectors.toSet());
            var missingNativeMethods = REQUIRED_NATIVE_METHODS.stream().filter(name -> !methodNames.contains(name)).sorted().toList();
            if (!missingNativeMethods.isEmpty()) {
                throw new IllegalStateException("SpeakerPeripheral lost native methods: " + missingNativeMethods);
            }

            HighAudio.LOGGER.info(
                "[EXP-001] SpeakerPeripheral mixin self-check PASS target={} probeDeclaringClass={} nativeMethodsPresent={}",
                target.getName(), probe.getDeclaringClass().getName(), REQUIRED_NATIVE_METHODS
            );
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "[EXP-001] SpeakerPeripheral mixin self-check failed for exact target " + SPEAKER_PERIPHERAL_CLASS,
                e
            );
        }
    }
}
