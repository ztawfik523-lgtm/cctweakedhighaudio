# TEST-BATCH-003 timing C automatic evidence

**Status:** PASS — capability + hardened packaged scheduled harness; real-device scheduled playback deferred beyond GATE-003  
**Date:** 2026-09-07  
**Milestone:** MILESTONE-003 / EXP-003  
**Branch:** `milestone-003-exp-003-timing-modes`

## Decision context

ADR-0010 separates timing by intent:

- `immediate`: normal Minecraft-owned lowest-latency start;
- `together`: readiness barrier + core `alSourcePlayv` group start;
- `scheduled`: readiness barrier + capability-gated `alSourcePlayAtTimevSOFT` device-clock start.

This evidence covers the automatic feasibility and implementation-integrity boundary for `scheduled`. It does not claim audible real-device scheduled timing has been manually measured.

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

and successfully queried the device clock. The CI OpenAL `No Output`/null backend returned an initial device-clock value of `0`; this is treated only as successful API/device access, not as an audible-device timing measurement.

### Corrected ALC capability boundary

The first implementation incorrectly read `ALC.getCapabilities()` from the sound thread. LWJGL's `ALCCapabilities` are device-specific, while `ALC.getCapabilities()` may fall back to process/router capabilities on a thread without the device's TLS capabilities. That produced a false `deviceClock=false` result despite a valid Minecraft device handle.

The corrected implementation queries the actual Minecraft-owned device directly:

```text
alcIsExtensionPresent(currentDevice, "ALC_SOFT_device_clock")
```

and then uses `SOFTDeviceClock.alcGetInteger64vSOFT(...)` only when the required AL/ALC capabilities are present.

## Initial scheduled harness candidate

Code candidate including the first diagnostic scheduled-start harness:

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

## Media-zero / clock / latency corrections

The initial harness was subsequently strengthened before any real scheduled-device gate.

The current diagnostic distinguishes:

```text
1. source-start device time
2. media-zero renderer time
3. estimated physical-output media-zero time
```

The generated sync stream contains a 100 ms silent preroll because NeoForge exposes the `Channel` only after Minecraft has already initiated playback. That preroll is an internal preparation mechanism, not a user-facing delay. The scheduled controller subtracts the preroll from the requested renderer media-zero target so the public/session concept can remain "media sample zero at T".

The current OpenAL device output latency is sampled separately. It is not folded into the renderer media-zero target; it is used only to record an estimated physical-output time.

For relative group measurement, the diagnostic now queries:

```text
AL_SAMPLE_OFFSET_CLOCK_SOFT
```

which returns the source sample offset and its matching device-clock timestamp atomically. Each source is still queried sequentially, so the sampled offsets are normalized to a common reference device clock before spread is calculated.

The final correction is target-aware. A future-scheduled source can report `AL_PLAYING` while its offset is still frozen before the requested source-start clock. Compensation therefore converts query-to-reference elapsed time into advancing frames only for the interval after the scheduled source-start clock.

## Hardened timing candidate — PASS

Current timing code candidate:

```text
ecb6c9d8a787184033f08082f888a0283b1d6ec5
```

CI run:

```text
34121266402
```

Result:

```text
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
workflow conclusion: success
```

The full automatic matrix passes compilation, packaged verification, development-client sound-engine initialization, accepted server regressions, packaged-JAR dedicated-server startup, and artifact production.

The hardened package/bytecode audit deliberately checks the actual method calls because `javac` inlines static-final OpenAL enum values. It requires:

```text
alSourcePlayv
alSourcePlayAtTimevSOFT
alcGetInteger64vSOFT
alGetSourcei64vSOFT
```

and rejects timing-layer ownership operations:

```text
alGenSources
alDeleteSources
alcOpenDevice
alcCloseDevice
alcCreateContext
alcDestroyContext
```

The harness also aborts any active scheduled trial on `SoundEngineLoadEvent`, preventing old source/device state from surviving renderer reconstruction.

Therefore HighAudio is still controlling timing of **Minecraft-owned** sources rather than introducing an independent OpenAL source/device/context lifecycle.

## Diagnostic behavior implemented

The scheduled harness can arm 1..16 Minecraft-owned generated-PCM streams with a diagnostic future device-clock target. It records:

- per-source capture;
- pre-pause and post-rewind sample offsets;
- source-start device clock;
- renderer media-zero target clock;
- estimated physical-output media-zero clock;
- current device output latency;
- OpenAL call error and duration;
- atomic source offset/device-clock samples;
- target-aware clock-compensated relative group spread;
- source states before/near/after media zero;
- stream cleanup;
- reload abort behavior.

The diagnostic lead is intentionally a test constant, not the future public timing policy. Public/session scheduled time must refer to the meaningful media onset; any preparation preroll is compensated internally.

## Conclusion

Scheduled device-clock playback is **compile-time and initialized-client feasible on the exact target stack**, and the hardened harness/measurement machinery is packaged and automatically green. No user launch was needed to establish that capability and ownership boundary.

A real-device scheduled timing guarantee is still **not claimed**. `ADR-0010` remains Proposed until a later production/session timeline gate needs that guarantee and validates it on a real device.

This remaining scheduled evidence is **not a GATE-003 blocker**. MILESTONE-003 already has real-device evidence for the architecture-selecting 16-stream capacity policy and the local `together` vector primitive. Closing M3 without another capability-only launch is therefore consistent with `docs/TESTING.md`'s minimum-manual-testing policy.
