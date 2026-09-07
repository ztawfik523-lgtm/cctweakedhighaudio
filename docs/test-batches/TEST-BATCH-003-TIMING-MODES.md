# TEST-BATCH-003 timing-mode addendum

**Status:** TIMING PRIMITIVES AUTO PASS — GATE-003 CLOSED; REAL SCHEDULED PLAYBACK DEFERRED  
**Milestone:** MILESTONE-003 / EXP-003  
**Branch:** `milestone-003-exp-003-timing-modes`  
**Decision:** `ADR-0010` Proposed

## Why this addendum exists

The original Part B comparison established that both normal Minecraft start sequencing and a narrow Minecraft-owned `alSourcePlayv` vector start can maintain zero measured sample-offset spread through 16 sources on the tested real client. Re-evaluation after that pass identified a production-readiness gap: the test streams were immediately ready, while future media streams may resolve asynchronously.

The project therefore no longer treats synchronization as one global A-vs-B choice. Playback timing is split by intent:

| Intent | User meaning | Leading implementation |
|---|---|---|
| `immediate` | play with minimum latency | normal Minecraft-owned start |
| `together` | wait until required local participants are ready, then start together ASAP | Minecraft-owned sources + core `alSourcePlayv` |
| `scheduled` | align media sample zero to an explicit HighAudio/session timeline point | capability-gated device-clock `alSourcePlayAtTimevSOFT` |

The eventual public Lua/session names remain deferred. EXP-003 validates renderer primitives only.

## Existing evidence carried forward

### Immediate/high-level path

Real NeoForge 21.1.247 / OpenAL Soft evidence completed two full 2/4/8/16 sweeps. The high-level path measured zero relative `AL_SAMPLE_OFFSET` spread at start, t+2, t+5, and t+10 observation points. This proves the path is excellent when streams are already ready; it does not prove separately-resolving future media streams will become ready simultaneously.

### Together/vector path

The same real-client experiment completed two full 2/4/8/16 vector sweeps with:

- full captures through 16/16;
- zero measured relative sample-offset spread;
- zero vector OpenAL errors;
- `maxPrePauseOffsetSamples=0` and `maxPostRewindOffsetSamples=0`;
- clean stream closure.

Therefore the vector start is retained as a proven `together` primitive and must not be discarded merely because the high-level probe also measured zero spread.

Canonical evidence remains:

`docs/test-batches/evidence/TEST-BATCH-003-PARTB-NEOFORGE-21.1.247.md`

## Scheduled/device-clock candidate

Exact platform research supports a narrow third primitive:

- Minecraft 1.21.1 uses LWJGL 3.3.3;
- LWJGL 3.3.3 contains `SOFTSourceStartDelay.alSourcePlayAtTimevSOFT(...)`;
- it also contains `SOFTDeviceClock` device-clock queries;
- the timed-start extension requires the device-clock capability;
- Minecraft 1.21.1 `SoundEngine` owns a private `Library` and `Library` owns the current OpenAL device handle;
- HighAudio already proved it can access Minecraft-owned Channel source ids without creating/deleting sources.

The scheduled prototype remains narrow:

1. read `SoundEngine.library`;
2. read `Library.currentDevice`;
3. feature-detect `AL_SOFT_source_start_delay`, `AL_SOFT_source_latency`, and `ALC_SOFT_device_clock` on Minecraft's sound thread;
4. query the current device clock and current output latency;
5. schedule an existing Minecraft-owned source group at an explicit device-clock timestamp;
6. keep renderer media-zero timing distinct from estimated physical-output timing;
7. do not create/delete OpenAL sources/devices/contexts;
8. do not add production media/session/network behavior.

## Automatic C capability gate — PASS

Code/capability probe commit:

```text
dc1a62265f1ba4165929938a41e7f460acd70301
```

CI run:

```text
34114885331
```

Both exact NeoForge 21.1.247 and 21.1.248 jobs passed build, packaged verification, development-client sound-engine initialization, accepted M1 server regression, and packaged-JAR dedicated-server smoke.

The first `SoundEngineLoadEvent` occurs before the OpenAL library/device has been loaded and therefore intentionally reports unavailable capability. This pre-init line is not an availability result.

After Minecraft initializes its OpenAL device, both matrix clients report from the `Sound engine` thread:

```text
sourceStartDelay=true
sourceLatency=true
deviceClock=true
available=true
detail=ok
```

and the device-clock query succeeds. The CI `No Output`/null OpenAL device reports an initial clock value of `0`, which is valid for a newly initialized device and is not treated as an audible-device timing measurement.

A first version of the probe incorrectly read LWJGL's process/router `ALCCapabilities` and therefore reported `deviceClock=false` despite querying a real device handle. Re-evaluation corrected the check to call `alcIsExtensionPresent(currentDevice, "ALC_SOFT_device_clock")` on Minecraft's actual device. This is the canonical capability check.

Automatic capability conclusion:

- exact 1.21.1/LWJGL/NeoForge target can address the scheduled-start and device-clock bindings;
- the private Minecraft accessors apply through real client initialization;
- the target OpenAL Soft implementation exposes the required capabilities on the actual device;
- the device clock can be queried from Minecraft's sound thread;
- no manual Minecraft launch was required to establish this capability boundary.

## Scheduled trial harness — automatic PASS

Initial harness candidate:

```text
dc2fbaad0f383bbbbe9d17350c003b4e0a53a62f
```

CI run:

```text
34115260138
```

Both NeoForge 21.1.247 and 21.1.248 passed compilation/package checks, real client sound-engine initialization, accepted M1 server regression, packaged-JAR dedicated-server startup, and artifact upload with the diagnostic scheduled-start harness present.

The branch includes diagnostic-only `Exp3ScheduledSound` / `Exp3ScheduledController` code. It reuses Minecraft-owned channels and the same 100 ms silent-preroll PCM used by the earlier vector experiment, pauses/rewinds each captured source, then calls the capability-gated device-clock scheduled-start primitive once the required group is ready.

## Media-zero and clock/latency hardening

Later branch work corrected the scheduled diagnostic so its timestamps mean what the eventual session architecture needs them to mean.

The diagnostic distinguishes:

```text
source-start device clock
media-zero renderer clock
estimated media-zero physical-output clock
```

The 100 ms silent preroll is an internal preparation artifact. The renderer schedules source start one preroll earlier so the requested renderer target corresponds to the first media sample rather than the beginning of hidden silence. Current device output latency is recorded separately and is only used for an estimated physical-output timestamp.

For source alignment measurement, the diagnostic uses `AL_SAMPLE_OFFSET_CLOCK_SOFT` so one OpenAL call returns a source's 32.32 fixed-point sample offset and the matching device-clock timestamp atomically. Because multiple sources still have to be queried sequentially, each sample is normalized to a common reference device clock before relative group spread is calculated.

The final correction is target-aware. A future-scheduled source may already report `AL_PLAYING` while its sample offset remains frozen until the scheduled source-start device clock. Therefore query-to-reference elapsed time is converted to advancing sample frames only for the interval after that source-start clock.

Current hardened timing code candidate:

```text
commit: ecb6c9d8a787184033f08082f888a0283b1d6ec5
CI run: 34121266402
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
```

The full automatic build/package/client/server matrix passed. The packaged bytecode audit checks the actual LWJGL method calls rather than Java enum constant names, which `javac` may inline numerically. It requires:

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

`SoundEngineLoadEvent` also aborts an in-flight scheduled diagnostic so no old source/device state is carried across renderer reconstruction.

Canonical automatic evidence:

`docs/test-batches/evidence/TEST-BATCH-003-TIMING-C-AUTOMATIC.md`

## Semantics carried forward

- A normal one-speaker SFX remains `immediate` and gets no artificial synchronization delay.
- A one-member `together` group should collapse to `immediate` because there is nothing local to synchronize against.
- A one-member `scheduled` request remains meaningful if it targets a session/external timeline.
- Scheduled lead time is adaptive; the current diagnostic lead is an internal safety budget, not a public fixed delay.
- If a silent preparation preroll is used, a public/session target time must describe **audible media sample zero**, not the beginning of the silent preroll. The renderer compensates for any preroll internally.
- Output latency is not renderer start time. A future public concept such as "heard at session time T" must account for local output latency without exposing raw OpenAL clocks to Lua.
- If precise scheduled timing is requested but timed-start capability is unavailable, HighAudio should report that precise scheduling is unavailable rather than silently claiming a timestamp guarantee. A future public API may allow explicit fallback.
- Initial start synchronization does not solve later streaming underruns/drift. Ongoing timeline/drift handling remains later work.

## GATE-003 relationship and minimum-manual-testing result

No standalone user launch is justified merely to re-check scheduled capability or the presence of the OpenAL timing bindings. More importantly, after the real Part A/Part A2/Part B evidence, real-device scheduled playback is no longer architecture-blocking for MILESTONE-003.

GATE-003 closes using the measured capacity and local synchronized-start evidence plus the automatic scheduled feasibility proof. `ADR-0010` remains Proposed so the project does not claim a real-device scheduled timing guarantee which has not been run.

If a later production session/timeline milestone needs exact `scheduled` semantics, that later gate should use one consolidated self-measuring real-device test which exercises the production readiness/timeline path. It should not resurrect a ladder of EXP-003 capability-only launches.
