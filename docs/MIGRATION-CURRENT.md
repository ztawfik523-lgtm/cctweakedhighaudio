# HighAudio current-chat migration

**Status:** CURRENT — read this first in the next chat  
**Prepared:** 2026-09-07  
**Project:** `ztawfik523-lgtm/cctweakedhighaudio`  
**Current branch:** `milestone-003-exp-003-timing-modes`  
**Branch HEAD before this migration file:** `9bee45c8b3b4c78b442fce72925f924cb9797322`  
**Milestone:** MILESTONE-003 / EXP-003

This file exists because the conversation hit the UI length limit. It is the authoritative migration note for the newest M3 work and **supersedes stale M3/next-step wording in `docs/HANDOFF.md` where they conflict**. Older detailed evidence remains authoritative for the facts it records.

## Read order in the next chat

1. `docs/MIGRATION-CURRENT.md` — newest state and next action.
2. `docs/TESTING.md` — minimum-manual-testing policy.
3. `docs/decisions/ADR-0010-playback-timing-intents.md` — current timing design.
4. `docs/test-batches/TEST-BATCH-003-TIMING-MODES.md` — timing-mode experiment status.
5. `docs/test-batches/evidence/TEST-BATCH-003-TIMING-C-AUTOMATIC.md` — automatic C capability/harness evidence.
6. `docs/test-batches/evidence/TEST-BATCH-003-PARTB-NEOFORGE-21.1.247.md` — real A/B sync evidence.
7. `docs/test-batches/evidence/TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md` — real 16-stream rebalance evidence.
8. Existing `docs/HANDOFF.md` only for older M0-M2/full-history detail; its old M3 next-step section is superseded here.

## User/process rule — important

The user explicitly asked for **minimum testing to mean minimum** after too many Minecraft relaunches. Do not make the user test every implementation step.

Default workflow:

- research exact source/API behavior first;
- compile/package/bytecode inspect automatically;
- use real dev-client initialization in CI where it can answer the question;
- combine competing modes into one self-measuring diagnostic when practical;
- ask for one Minecraft launch only when the result is architecture-blocking and cannot be established automatically;
- batch all remaining useful observations into that one launch.

Do not block current progress on broad SPR testing; exact SPR acoustic correctness belongs later unless a current design specifically requires a coexistence check.

## Fixed exact target

Do not silently substitute newer versions:

```text
Minecraft:       1.21.1
Java:            21
CC:Tweaked:      1.120.0
CC:T source tag: v1.21.1-1.120.0
CC:T source commit prefix: 98f3a71
NeoForge base:   21.1.247
Compatibility:   21.1.248
SPR later exact: 1.21.1-1.5.1
```

One NeoForge mod, CC:T required. User-facing physical devices remain normal `computercraft:speaker`. HighAudio must not create a replacement speaker ecosystem. CC:T internal coupling stays under `integration/cct`.

## Completed milestones

### MILESTONE-000

Complete / GATE-000 passed.

### MILESTONE-001

Complete / GATE-001 passed. Accepted mechanism is targeted CC:T `GenericSource` (`ADR-0008`).

Frozen source candidate:

```text
93a72cbb13357cd9d9906478998604835e0931b0
CI 34082746562
JAR SHA-256 0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

### MILESTONE-002

Complete / GATE-002 passed. Arbitrary HighAudio-owned generated PCM renders through Minecraft's normal sound lifecycle.

Frozen source candidate:

```text
4e31bbd08cc8c4e314637d857098c02232f41ff4
CI 34088822441
JAR SHA-256 515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Important carried facts:

- stream `bytesRead` is not audible playhead;
- Java `SoundEngine` object identity is not a renderer-generation identity across reload;
- effective Minecraft volume zero can prevent channel allocation and is not a guaranteed arming mechanism.

## MILESTONE-003 capacity result

### Vanilla baseline

Real NeoForge 21.1.247 client measured Minecraft's streaming reservation plateau at **8**:

```text
1 -> 1
2 -> 2
4 -> 4
6 -> 6
8 -> 8
10 -> 8
12 -> 8
16 -> 8
```

Minecraft debug reached `... + 8/8`.

### Reservation rebalance

A narrow Minecraft-owned reservation rebalance was implemented instead of building a static backend or independent OpenAL engine.

Frozen conservative candidate:

```text
commit 82c195637de3987463c864c8f8493e9194410094
CI 34103604455
JAR SHA-256 f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

On the tested 255-source runtime it changes:

```text
247 static + 8 streaming
->
239 static + 16 streaming
```

while preserving the total source reservation. It only opts in when vanilla already derives its normal 8-stream layout; weaker layouts remain vanilla. HighAudio does not own `alGenSources`/`alDeleteSources`.

Real Windows/OpenAL runtime passed:

```text
4  -> 4
8  -> 8
12 -> 12
16 -> 16
16 -> 16
16 -> 16 explicit stop
16 -> 16 explicit stop
16 -> 16
```

So 16/16 succeeded five times. Cleanup was clean. Static sounds were still observed while `16/16` streaming slots were occupied. This meets the project's current 16-speaker capacity target.

Do **not** return to absolute source-limit research unless later evidence forces it.

## MILESTONE-003 timing/sync result so far

### A/B real comparison — completed

One combined diagnostic compared:

- A: normal Minecraft/high-level start;
- B: pause/rewind captured Minecraft-owned sources and start the group with core `alSourcePlayv`.

The user accidentally ran the full sequence twice, giving **16 total trials** over 2/4/8/16 sources for both modes.

Both complete sweeps reported:

```text
highLevelMaxSpreadSamples=[2:0,4:0,8:0,16:0]
vectorMaxSpreadSamples=[2:0,4:0,8:0,16:0]
```

The vector path also had:

```text
vectorError=0
maxPrePauseOffsetSamples=0
maxPostRewindOffsetSamples=0
```

and clean stream cleanup.

Canonical evidence commit for that result:

```text
34f6a202b001cadfc18ef3e1b33ef4f26705551d
```

Interpretation: normal Minecraft start is excellent when all streams are already ready, and B is a proven synchronized local-group primitive. However, the diagnostic streams returned already-completed futures, so it does **not** prove separately resolving real media streams will all become ready at the same time.

## Current timing architecture — ADR-0010 Proposed

Do not treat A/B/C as one global setting. Timing is **intent-driven**:

### `immediate` — default / fastest

Meaning: play as soon as this sound is ready.

Leading implementation: normal Minecraft-owned playback. No group barrier and no artificial sync delay.

Use for button sounds, one-shot SFX, UI feedback, independent ambience, voice lines, and ordinary single-speaker playback where sync is not requested.

A normal one-speaker sound should not pay a 100 ms delay.

### `together` — explicit local group start ASAP

Meaning: wait until all required local participants are ready, then start them together immediately.

Leading implementation: shared readiness/arming machinery + core `alSourcePlayv` on Minecraft-owned sources.

B is **worth keeping** as a first-class intent, not merely a fallback. It is core OpenAL and has already been proven at 16 sources on the real client.

A one-member `together` group should normally collapse to `immediate` because there is nothing local to synchronize against.

### `scheduled` — explicit timeline/device-clock start

Meaning: start at a specific HighAudio/session timeline point.

Leading implementation: same readiness/arming base as `together`, then capability-gated `alSourcePlayAtTimevSOFT` against the current OpenAL device clock.

C is optional and must **not** become the default for normal SFX.

A one-member scheduled request remains meaningful if it is synchronizing to a session/external timeline.

No fixed 100 ms tax exists. Lead time is a scheduling safety budget and should be adaptive. If a silent preparation preroll exists, the public/session target time means **audible media sample zero at T**, not the beginning of the silent preroll; renderer logic must compensate internally.

If precise scheduled playback is requested but the required capability is unavailable, do not silently claim B met the timestamp guarantee. Future public API may allow an explicit fallback policy.

Public Lua should expose semantic intent (`play`, `startTogether`, `startAt`/session equivalents), not `A/B/C`, OpenAL IDs, raw device-clock values, or extension names. Exact Lua names remain deferred until the media/session milestone.

## C research/capability result — automatic PASS

Current branch:

```text
milestone-003-exp-003-timing-modes
```

Exact platform facts:

- Minecraft 1.21.1 uses LWJGL 3.3.3;
- LWJGL 3.3.3 includes `SOFTSourceStartDelay.alSourcePlayAtTimevSOFT(...)`;
- LWJGL 3.3.3 includes `SOFTDeviceClock` queries;
- Minecraft `SoundEngine` owns `Library`, and `Library` owns the current OpenAL device handle;
- existing B work already provides Minecraft `Channel` -> source-id access.

Narrow C accessors/helpers added:

- `SoundEngineLibraryAccessor`;
- `LibraryDeviceAccessor`;
- `Exp3TimingPrimitives`;
- `Exp3TimedStartDiagnostics`.

The first capability implementation incorrectly read LWJGL's process/router `ALCCapabilities`, producing a false `deviceClock=false`. This was corrected by querying Minecraft's **actual device** with:

```text
alcIsExtensionPresent(currentDevice, "ALC_SOFT_device_clock")
```

Canonical corrected capability commit:

```text
dc1a62265f1ba4165929938a41e7f460acd70301
CI 34114885331
```

After OpenAL device initialization, both exact NeoForge 21.1.247 and 21.1.248 clients reported on the `Sound engine` thread:

```text
sourceStartDelay=true
sourceLatency=true
deviceClock=true
available=true
detail=ok
```

and `ALC_DEVICE_CLOCK_SOFT` queried successfully. The early pre-library `SoundEngineLoadEvent` reports unavailable before any device exists and is expected; do not misread it as the final result.

Conclusion: C's capability boundary is automatically proven on the exact target stack. No manual launch was needed.

## Scheduled diagnostic harness — built/packaged, not real-device measured yet

Diagnostic-only scheduled code was added:

- `Exp3ScheduledSound`;
- `Exp3ScheduledController`;
- lifecycle abort on `SoundEngineLoadEvent`;
- 1..16 generated-PCM source support;
- captures pre-pause/post-rewind offsets;
- schedules Minecraft-owned source groups with the device-clock primitive;
- records target device-clock time, call duration/error, source state/offset spread before/near/after target, and cleanup.

Code candidate:

```text
dc2fbaad0f383bbbbe9d17350c003b4e0a53a62f
CI 34115260138
```

Both exact `.247` and `.248` jobs passed:

- Java/build/package;
- real dev-client sound-engine initialization;
- timing capability observation;
- accepted M1 regression;
- packaged-JAR dedicated-server startup;
- artifact upload.

Both matrix JARs were byte-identical:

```text
SHA-256 31829658ff85ad9153cd8a807d14a9520f331ff2297a59fa51ef339bcab3abba
```

Bytecode audit found the expected timing operations:

```text
AL10.alSourcePlayv
SOFTSourceStartDelay.alSourcePlayAtTimevSOFT
ALC_SOFT_device_clock
```

and none of these ownership operations in the new timing layer:

```text
alGenSources
alDeleteSources
alcOpenDevice
alcCloseDevice
alcCreateContext
alcDestroyContext
```

Therefore C remains timing control over **Minecraft-owned** sources, not a second audio engine.

Canonical automatic evidence:

`docs/test-batches/evidence/TEST-BATCH-003-TIMING-C-AUTOMATIC.md`

### Important current limitation

The scheduled harness is built and packaged, but audible scheduled behavior has **not** been measured on the user's real audio device yet. Do not call C proven end-to-end or accept ADR-0010 solely from CI capability evidence.

Also verify whether `Exp3ScheduledController` is currently wired into the client command/event surface before handing any JAR to the user; the implementation was created late in the previous chat and should be re-audited instead of assumed callable.

## Current branch state

Before creating this migration file, branch HEAD was:

```text
9bee45c8b3b4c78b442fce72925f924cb9797322
message: Freeze automatic scheduled timing harness evidence
```

Relevant recent sequence:

```text
dc1a62265f1ba4165929938a41e7f460acd70301  Query timed-start capability from actual OpenAL device
dc2fbaad0f383bbbbe9d17350c003b4e0a53a62f  Abort scheduled diagnostic on sound-engine reload
b46ed30c693a5b03138d0c0abc13c69b07730a5e  Record automatic scheduled-start capability pass
8fbc9fb60312716a4c95ba931122211c0c6bec28  Record automatic device-clock timing evidence
9bee45c8b3b4c78b442fce72925f924cb9797322  Freeze automatic scheduled timing harness evidence
```

## What the next chat should do

Do **not** ask the user to relaunch Minecraft immediately.

Start by re-auditing the current timing branch, especially:

1. fetch `Exp3TimingPrimitives`, `Exp3ScheduledController`, `Exp3ScheduledSound`, the two new accessors, `Exp3ClientEvents`, `Exp3ClientModEvents`, and `cctweakedhighaudio.mixins.json` from the current branch;
2. verify the scheduled harness is actually reachable from a consolidated diagnostic command/event path; if not, wire it cleanly;
3. verify all OpenAL operations remain on Minecraft's sound thread;
4. verify scheduled target semantics account correctly for any silent preroll so audible media zero corresponds to the intended target time;
5. verify unsupported capability/error behavior is explicit and does not silently degrade a requested precise timestamp;
6. strengthen CI/package checks to assert the new scheduled classes/accessors and reject raw source/device/context ownership;
7. keep `.247` and `.248` automatic matrix green;
8. only then decide whether one final consolidated M3 real-device timing run is actually architecture-blocking.

If one manual run is necessary, make it **one command in one launch** and have it automatically cover the useful remaining M3 timing evidence. Do not split C into repeated 1/2/4/8/16 commands unless instrumentation genuinely requires it.

## What not to do next

Do not:

- restart capacity-limit research; 16/16 target is already proven on the real device;
- remove B just because A also measured zero spread;
- make C/scheduled the default for normal SFX;
- add a fixed universal 100 ms delay;
- expose raw OpenAL/device-clock concepts to Lua;
- implement real media upload/codecs/ContentId/network sessions yet just to exercise timing;
- claim C solves long-running underrun/drift; initial start and ongoing drift are separate problems;
- require broad SPR acoustic testing during every M3 iteration.

## Current product direction in one sentence

**Normal playback is immediate and fastest by default; scripts can explicitly request `together` for ASAP local group synchronization or `scheduled` for precise session/timeline timing, all while Minecraft continues to own the actual audio sources.**
