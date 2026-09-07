# TEST-BATCH-003 timing-mode addendum

**Status:** C CAPABILITY AUTO PASS — SCHEDULED TRIAL HARNESS BUILDING  
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
| `scheduled` | start at an explicit HighAudio/session timeline point | capability-gated device-clock `alSourcePlayAtTimevSOFT` |

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
4. query the current device clock;
5. provide a primitive capable of scheduling an existing Minecraft-owned source group at `deviceClock + lead`;
6. do not create/delete OpenAL sources/devices/contexts;
7. do not add production media/session/network behavior.

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

and the `ALC_DEVICE_CLOCK_SOFT` query returns successfully. The CI `No Output`/null OpenAL device reports an initial clock value of `0`, which is valid for a newly initialized device and is not treated as an audible-device timing measurement.

A first version of the probe incorrectly read LWJGL's process/router `ALCCapabilities` and therefore reported `deviceClock=false` despite querying a real device handle. Re-evaluation corrected the check to call `alcIsExtensionPresent(currentDevice, "ALC_SOFT_device_clock")` on Minecraft's actual device. This is the canonical capability check.

Automatic capability conclusion:

- exact 1.21.1/LWJGL/NeoForge target can address the scheduled-start and device-clock bindings;
- the private Minecraft accessors apply through real client initialization;
- the target OpenAL Soft implementation exposes the required capabilities on the actual device;
- the device clock can be queried from Minecraft's sound thread;
- no manual Minecraft launch was required to establish this capability boundary.

## Scheduled trial harness — current work

The branch now includes diagnostic-only `Exp3ScheduledSound` / `Exp3ScheduledController` code. It reuses Minecraft-owned channels and the same 100 ms silent-preroll PCM used by the earlier vector experiment, pauses/rewinds each captured source, then calls the capability-gated device-clock scheduled-start primitive once the required group is ready.

The diagnostic records:

- capture count and pre-pause/post-rewind offsets;
- scheduled target device-clock timestamp;
- OpenAL error/call duration;
- source state and relative sample-offset spread while still before the target, near the target, after the target, and later in playback;
- current device clock and target-minus-current-clock delta;
- natural/explicit cleanup.

The diagnostic command is an EXP-003 implementation hook only. It does **not** make scheduled playback the default and does not define the production Lua API.

## Semantics carried forward

- A normal one-speaker SFX remains `immediate` and gets no artificial synchronization delay.
- A one-member `together` group should collapse to `immediate` because there is nothing local to synchronize against.
- A one-member `scheduled` request remains meaningful if it targets a session/external timeline.
- Scheduled lead time is adaptive; 100 ms is an example safety budget, not a fixed requirement.
- If a silent preparation preroll is used, a public/session target time must describe **audible media sample zero**, not the beginning of the silent preroll. The renderer must compensate for any preroll internally.
- If precise scheduled timing is requested but timed-start capability is unavailable, HighAudio should report that precise scheduling is unavailable rather than silently claiming B met a timestamp guarantee. A future public API may allow explicit fallback.
- Initial start synchronization does not solve later streaming underruns/drift. Ongoing timeline/drift handling remains later work.

## Minimum-manual-testing rule

No standalone user launch is justified merely to re-check C capability. Scheduled playback should be folded into a later consolidated M3 runtime gate only if real-device behavior remains architecture-blocking after automatic validation. A standalone capability-discovery launch is explicitly disallowed by `docs/TESTING.md`'s minimum-manual-testing policy.
