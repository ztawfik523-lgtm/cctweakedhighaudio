# TEST-BATCH-003 timing-mode addendum

**Status:** AUTOMATIC C-CAPABILITY PROTOTYPE IN PROGRESS  
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

The scheduled prototype must remain narrow:

1. read `SoundEngine.library`;
2. read `Library.currentDevice`;
3. feature-detect `AL_SOFT_source_start_delay`, `AL_SOFT_source_latency`, and `ALC_SOFT_device_clock` on Minecraft's sound thread;
4. query the current device clock;
5. provide a primitive capable of scheduling an existing Minecraft-owned source group at `deviceClock + lead`;
6. do not create/delete OpenAL sources/devices/contexts;
7. do not add production media/session/network behavior.

## Automatic gate before any user test

No manual Minecraft launch is justified merely to discover whether C is compile/runtime-addressable.

The exact NeoForge 21.1.247 and 21.1.248 matrix must first prove:

- Java compilation against the exact target stack;
- Mixin accessors for `SoundEngine.library` and `Library.currentDevice` apply during real client initialization;
- LWJGL timed-start/device-clock symbols link successfully;
- capability inspection executes on Minecraft's sound thread;
- client diagnostics report the actual capability booleans and a non-error device-clock query when available;
- existing 16-stream reservation patch and M1/server regressions remain green;
- the timed-start helper contains no `alGenSources`, `alDeleteSources`, device creation, or context creation.

Only if this automatic gate is clean should scheduled playback be folded into a later consolidated manual M3 test. A standalone user launch for capability discovery is explicitly disallowed by `docs/TESTING.md`'s minimum-manual-testing policy.

## Semantics under consideration

- A normal one-speaker SFX remains `immediate` and gets no artificial synchronization delay.
- A one-member `together` group should collapse to `immediate` because there is nothing local to synchronize against.
- A one-member `scheduled` request remains meaningful if it targets a session/external timeline.
- Scheduled lead time is adaptive; 100 ms is an example safety budget, not a fixed requirement.
- If precise scheduled timing is requested but timed-start capability is unavailable, HighAudio should report that precise scheduling is unavailable rather than silently claiming B met a timestamp guarantee. A future public API may allow explicit fallback.
- Initial start synchronization does not solve later streaming underruns/drift. Ongoing timeline/drift handling remains later work.
