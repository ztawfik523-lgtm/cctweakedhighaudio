# TEST-BATCH-003 timing C automatic evidence

**Status:** PASS — capability + packaged scheduled harness; audible scheduled playback not manually run  
**Date:** 2026-09-07  
**Milestone:** MILESTONE-003 / EXP-003  
**Branch:** `milestone-003-exp-003-timing-modes`

## Decision context

ADR-0010 separates timing by intent:

- `immediate`: normal Minecraft-owned lowest-latency start;
- `together`: readiness barrier + core `alSourcePlayv` group start;
- `scheduled`: readiness barrier + capability-gated `alSourcePlayAtTimevSOFT` device-clock start.

This evidence covers the automatic feasibility boundary for `scheduled`. It does not claim audible real-device scheduled timing has been manually measured yet.

## Capability probe

Canonical corrected capability commit:

```text
dc1a62265f1ba4165929938a41e7f460acd70301
```

CI run:

```text
34114885331
```

NeoForge 21.1.247: PASS  
NeoForge 21.1.248: PASS

Both exact target development clients produced an early pre-library `SoundEngineLoadEvent` before an OpenAL device/capability context existed. That early unavailable line is expected and is not the final capability result.

After Minecraft initialized the OpenAL device, both exact targets logged from the `Sound engine` thread:

```text
sourceStartDelay=true
sourceLatency=true
deviceClock=true
available=true
detail=ok
```

and successfully queried `ALC_DEVICE_CLOCK_SOFT`. The CI OpenAL `No Output`/null backend returned an initial device-clock value of `0`; the extension permits an implementation-defined non-negative initial clock and this is treated only as successful API/device access, not as an audible-device timing measurement.

### Corrected ALC capability boundary

The first implementation incorrectly read `ALC.getCapabilities()` from the sound thread. LWJGL's `ALCCapabilities` are device-specific, while `ALC.getCapabilities()` may fall back to process/router capabilities on a thread without the device's TLS capabilities. That produced a false `deviceClock=false` result despite a valid Minecraft device handle.

The corrected implementation queries the actual Minecraft-owned device directly:

```text
alcIsExtensionPresent(currentDevice, "ALC_SOFT_device_clock")
```

and then uses `SOFTDeviceClock.alcGetInteger64vSOFT(...)` only when the required AL/ALC capabilities are present.

## Scheduled harness candidate

Code candidate including the diagnostic scheduled-start harness:

```text
dc2fbaad0f383bbbbe9d17350c003b4e0a53a62f
```

CI run:

```text
34115260138
```

Both exact NeoForge targets passed:

- Java 21 / MC 1.21.1 / CC:T 1.120.0 compilation;
- packaged M3 verification;
- real development-client sound-engine initialization;
- timed-start capability observation after OpenAL initialization;
- accepted M1 GenericSource server regression;
- packaged-JAR dedicated-server startup;
- artifact upload.

Both matrix artifacts contained byte-identical HighAudio JARs:

```text
SHA-256 31829658ff85ad9153cd8a807d14a9520f331ff2297a59fa51ef339bcab3abba
```

Packaged timing classes include:

- `Exp3TimingPrimitives`;
- `Exp3TimedStartDiagnostics`;
- `Exp3ScheduledSound`;
- `Exp3ScheduledController`;
- `SoundEngineLibraryAccessor`;
- `LibraryDeviceAccessor`.

Direct compiled-bytecode inspection confirmed expected timing operations:

```text
AL10.alSourcePlayv
SOFTSourceStartDelay.alSourcePlayAtTimevSOFT
ALC_SOFT_device_clock
```

and found none of the forbidden ownership operations in the new timing layer:

```text
alGenSources
alDeleteSources
alcOpenDevice
alcCloseDevice
alcCreateContext
alcDestroyContext
```

Therefore HighAudio is still controlling timing of **Minecraft-owned** sources rather than introducing an independent OpenAL source/device/context lifecycle.

## Diagnostic behavior implemented

The scheduled harness can arm 1..16 Minecraft-owned generated-PCM streams with a diagnostic future device-clock target. It records:

- per-source capture;
- pre-pause and post-rewind sample offsets;
- scheduled target device clock;
- OpenAL call error and duration;
- source state/offset spread before, near, and after the target;
- current device clock and target delta;
- stream cleanup.

The diagnostic lead is intentionally a test constant, not the future public timing policy. Public/session scheduled time must refer to the meaningful media onset; any silent preparation preroll must be compensated internally.

## Conclusion

Scheduled device-clock playback is **compile-time and client-runtime feasible on the exact target stack**, and the harness required to measure it is packaged and clean. No user launch was needed to discover the capability boundary.

Remaining evidence before ADR-0010 can call scheduled playback proven:

- actual scheduled-source behavior on a real audio device (preferably bundled into a later consolidated M3 runtime gate rather than a standalone launch);
- audible-preparation/no-leak confirmation under that consolidated gate;
- final public fallback/error semantics remain deferred to the session/media API design.
