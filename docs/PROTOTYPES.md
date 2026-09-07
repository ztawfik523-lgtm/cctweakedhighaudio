# Prototype and experiment ledger

**Status:** canonical unresolved-runtime ledger  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `EXP`, `PROTOTYPE`, `PASS-FAIL`, `RUNTIME-EVIDENCE`

An experiment is marked PASS only when the exact implementation/runtime evidence required by its gate is preserved. Source research and automatic checks may support a result but must not be mislabeled as manual runtime evidence.

---

## EXP-001 — targeted SpeakerPeripheral GenericSource

**Status:** `PASS`  
**Related decisions:** `ADR-0008` Accepted; `ADR-0003` superseded fallback  
**Risks:** `RISK-001`, `RISK-002`, `RISK-021`

### Question

Can a registered CC:T `GenericSource` targeted at exact CC:T 1.120.0 `SpeakerPeripheral` expose a new HighAudio Lua method on the normal speaker while preserving native peripheral identity, lifecycle, native methods, and direct/wired behavior?

### Candidate history

EXP-001 originally evaluated a minimal additive Mixin. Before the manual gate, exact CC:T 1.120.0's generic-method path was re-evaluated and a less invasive candidate was found: `ComputerCraftAPI.registerGenericSource` plus a generic method whose first target parameter is the exact internal `SpeakerPeripheral` class. This preserves the original CC:T peripheral object and does not transform CC:T bytecode.

`ADR-0008` is Accepted. `ADR-0003` remains the first technical fallback if a future exact CC:T version makes the targeted GenericSource approach unusable.

### Smallest accepted implementation

- exact target stack only;
- one `SpeakerGenericSource` under `integration/cct`;
- one diagnostic `highAudioProbe()` method for the experiment;
- startup method-supplier and live-`ServerContext` self-checks;
- no Mixin;
- no audio engine, content store, packets, codec, upload, session, sync, SPR, URL, or VS2 work.

### Result

PASS on 2026-09-07.

Frozen candidate:

```text
branch: milestone-001-exp-001-genericsource
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI run: 34082746562
JAR SHA-256:
0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Automatic evidence passed on NeoForge 21.1.247 and 21.1.248, including exact CC:T method-supplier generation/invocation, speaker-only targeting, preservation of native methods, live registration, development-server startup, and installed packaged-JAR dedicated-server startup.

Broad real NeoForge 21.1.247 client evidence passed:

- direct normal block speaker exposure/callability;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain usable;
- wired remote speaker exposure/callability;
- turtle speaker exposure/callability, including recreated peripheral instances;
- real `PocketSpeakerPeripheral` exposure/callability;
- newly-created/reconstructed normal speaker peripherals continue to receive the GenericSource method;
- forced lifecycle reconstruction after disabling spawn-chunk retention;
- no observed HighAudio-specific runtime exception.

The extra NeoForge 21.1.248 gameplay repetition was explicitly waived after re-audit rather than being falsely recorded as run. The exact candidate already passed both development and installed packaged-server runtime compatibility checks on 21.1.248.

Accepted consequence: use public `ComputerCraftAPI.registerGenericSource` with exact-version internal `SpeakerPeripheral` targeting localized under `integration/cct`. A deliberate administrator `disabled_generic_methods` rule can disable the source/method and is configuration behavior, not an implementation failure.

---

## EXP-002 — Minecraft-owned high-quality AudioStream

**Status:** `PASS`  
**Related decisions:** `ADR-0004` remains Proposed until EXP-003  
**Risks:** `RISK-003`, `RISK-004`, `RISK-008`

### Question

Can HighAudio render custom PCM through Minecraft-owned `SoundInstance` + `AudioStream`, observe/capture the resulting `Channel` through NeoForge sound events, and recover cleanly across sound-engine lifecycle events?

### Smallest implementation

- client-only deterministic generated PCM;
- positional custom `SoundInstance`;
- custom `AudioStream`;
- normal `SoundManager.play(...)` ownership;
- listener for `PlayStreamingSourceEvent`;
- listener for `SoundEngineLoadEvent`;
- structured lifecycle diagnostics;
- no direct raw OpenAL source manager.

### Result

PASS on 2026-09-07.

Frozen candidate:

```text
branch: milestone-002-exp-002-minecraft-audio
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI run: 34088822441
JAR SHA-256:
515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Automatic evidence passed on both target NeoForge versions, including packaged client classes and clean installed dedicated-server startup.

Real NeoForge 21.1.247 client evidence established:

- generated 48 kHz / 16-bit / mono PCM is audible through Minecraft's normal sound path;
- `PlayStreamingSourceEvent` exposes the real Minecraft-owned `Channel`;
- positional attenuation works;
- Records/Jukebox category and master volume work;
- natural completion closes/stops cleanly;
- repeated explicit `play -> stop -> status` closes/stops cleanly;
- F3+T rebuilds the sound/OpenAL renderer and playback works again afterward;
- integrated singleplayer pause/resume behaves naturally without audible play-ahead/skip;
- disconnect cleans active playback and it does not resurrect on rejoin;
- no HighAudio-specific runtime/OpenAL error was observed;
- no independent raw OpenAL source manager is required for basic playback.

Evidence: `docs/test-batches/evidence/TEST-BATCH-002-NEOFORGE-21.1.247.md`.

Unexpected but important observations:

1. `AudioStream` bytes consumed is not audible renderer position. The stream can be fully read while queued audio is still rendering.
2. Java `SoundEngine` object identity is not a sound-renderer generation identity. OpenAL can rebuild while the Java object identity remains stable.
3. A sound at effective Minecraft volume zero may be rejected before a streaming channel is allocated, so mute cannot be relied on as a guaranteed source-arming mechanism.

Decision impact: the Minecraft-owned playback/lifecycle half of `ADR-0004` is validated. Capacity and synchronization remain EXP-003 before ADR-0004 can be accepted.

---

## EXP-003 — streaming channel capacity and playback timing boundary

**Status:** `PASS`  
**Branch:** `milestone-003-exp-003-timing-modes`  
**Related decisions:** `ADR-0004` Accepted; `ADR-0009` Accepted; `ADR-0010` Proposed  
**Risks:** `RISK-004`, `RISK-005`, `RISK-008`

### Questions

1. How many HighAudio streaming sounds can Minecraft actually allocate on the real target runtime, at least through the project's 16-source stress target?
2. Can Minecraft-owned sources provide a viable tightly synchronized local group-start primitive without HighAudio taking over OpenAL source/device/context ownership?
3. Is optional device-clock scheduled start feasible enough to preserve it as a future timing intent without making it the default or blocking GATE-003?

### Part A — vanilla source capacity

The strengthened capacity probe deliberately stayed on Minecraft-owned streaming channels and did not use raw OpenAL/source-id control.

The first automatically green candidate (`abe6063f46077fd74c0a83d05660cf07e0f3a33c`, CI `34092379023`) was superseded before manual testing after measurement-quality re-audit. The frozen strengthened baseline candidate was:

```text
commit: a500f3bee773e4e5558fe3473367927ece637f9b
CI:     34095196833
SHA:    553919083f8d998fd7d3b0e143f8e76ad3da7b78862ee84c93d886110be41055
```

Real NeoForge 21.1.247 testing measured:

```text
1  -> 1
2  -> 2
4  -> 4
6  -> 6
8  -> 8
10 -> 8
12 -> 8
16 -> 8
```

Minecraft reported `... + 8/8`. Each unique `PlayStreamingSourceEvent` capture represented a real allocated/started Minecraft streaming channel, all captures came from the sound thread, snapshots stayed stable, and allocated streams closed cleanly.

Evidence: `docs/test-batches/evidence/TEST-BATCH-003-NEOFORGE-21.1.247.md`.

Conclusion: vanilla Minecraft's normal streaming reservation is 8 on the measured runtime. This is a Minecraft pool-policy boundary, not an OpenAL hardware maximum.

### Part A2 — conservative Minecraft reservation rebalance

A narrow client-side `LibraryStreamingReservationMixin` rebalances Minecraft's existing static/streaming reservation while preserving the combined source budget and Minecraft ownership. Lower-capacity layouts where vanilla derives fewer than its normal eight streaming slots are left unchanged.

Frozen conservative candidate:

```text
commit: 82c195637de3987463c864c8f8493e9194410094
CI:     34103604455
SHA:    f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

On the measured runtime:

```text
247 static + 8 streaming = 255
        ->
239 static + 16 streaming = 255
```

Real `.247` Windows/OpenAL Soft evidence passed 4/4, 8/8, 12/12 and five separate 16/16 runs, including explicit-stop cycles. All completed runs cleaned their streams, and ordinary static-side Minecraft sounds were observed while `16/16` streaming channels were occupied.

Evidence: `docs/test-batches/evidence/TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md`.

Decision impact: `ADR-0009` is Accepted. Exact SPR 1.21.1-1.5.1 coexistence/acoustics/performance is deliberately deferred to MILESTONE-010 rather than requiring another M3 reassurance launch.

### Part B — local group start

One real-client comparison command measured ordinary Minecraft/high-level starting against synchronized vector starting with core OpenAL `alSourcePlayv` over already Minecraft-owned sources for 2/4/8/16 participants. The complete comparison was run twice.

Both methods measured zero relative `AL_SAMPLE_OFFSET` spread at the sampled start, t+2, t+5 and t+10 checkpoints through 16 sources. The vector path additionally showed:

- full 16/16 source capture;
- `vectorError=0`;
- zero measured pre-pause preparation offset in the diagnostic;
- zero post-rewind offset;
- clean stream closure.

Evidence: `docs/test-batches/evidence/TEST-BATCH-003-PARTB-NEOFORGE-21.1.247.md`.

The high-level result is strong for already-ready synthetic streams, but future decode/cache/network participants may become ready at different times. Therefore normal Minecraft playback remains the lowest-latency immediate path while vector start remains available for an explicit all-ready local group barrier.

### Timing intents and optional scheduled feasibility

The resulting semantic direction is recorded in `ADR-0010`:

- `immediate`: normal/default Minecraft-owned playback as soon as one sound is ready;
- `together`: wait for all required local participants, then start them together ASAP using the proven vector primitive where needed;
- `scheduled`: optional alignment of media sample zero to a HighAudio/session timeline point.

Scheduled timing is not the default and is not required for GATE-003. Automatic `.247/.248` client initialization proved the exact target exposes `AL_SOFT_source_start_delay`, `AL_SOFT_source_latency`, and `ALC_SOFT_device_clock` on Minecraft's initialized device. The strengthened diagnostic uses atomic `AL_SAMPLE_OFFSET_CLOCK_SOFT` sampling, distinguishes source-start device time from media-zero renderer time and estimated physical-output time, compensates hidden preparation preroll, and avoids treating a future-scheduled source as if its offset were already advancing.

Latest strengthened scheduled code candidate:

```text
commit: ecb6c9d8a787184033f08082f888a0283b1d6ec5
CI:     34121266402
result: PASS on NeoForge 21.1.247 and 21.1.248
```

This is automatic capability/integration evidence, not end-to-end audible scheduled timing on the user's physical device. That optional product behavior is deferred until a real session timeline/media pipeline exists so the eventual manual test measures something meaningful. `ADR-0010` therefore remains Proposed.

### Result

`PASS` on 2026-09-07 for the required renderer architecture.

`GATE-003` is passed because:

- vanilla capacity is measured;
- the project 16-stream target is repeatedly proven under a total-preserving Minecraft-owned reservation policy;
- a synchronized local group-start primitive is real-client proven through 16 sources;
- required low-level timing access is narrow and localized;
- no independent OpenAL source/device/context manager is needed.

Initial synchronized start does not imply long-running drift correction. Pause/seek/group transport, renderer tracking, underrun handling, scheduled session semantics, and drift correction remain later session/synchronization work.

---

## EXP-004 — emitter identity through block lifecycle

**Status:** `NOT RUN`  
**Related area:** `EmitterId`, lifecycle persistence  
**Risks:** `RISK-006`

### Question

What exact events/object reconstruction happen for a normal CC:T speaker on:

- chunk unload/reload;
- dimension unload/reload;
- server save/restart;
- block break;
- replacement at same position?

### Procedure

Instrument block/peripheral construction/removal, chunk/world events, block state, position, and any persisted custom data mechanism considered.

### Desired answer

Distinguish:

```text
same physical speaker temporarily unloaded
```

from:

```text
old speaker destroyed; different speaker placed at same coordinates
```

well enough to choose persistent `EmitterId` semantics without overengineering.

### Result

`NOT RUN`

---

## EXP-005 — MediaClock pause/freeze semantics

**Status:** `NOT RUN / PRODUCT DECISION REQUIRED`  
**Related area:** `MediaClock`  
**Risks:** `RISK-007`

### Questions

Observe available timing sources and determine what happens during:

- dedicated-server TPS drop;
- deliberate server-thread freeze;
- integrated-server pause menu;
- client-only pause-like screens;
- `/tick freeze` or equivalent test tooling if applicable.

Then choose desired product semantics:

**Option A — real monotonic media time**

- media advances while game simulation is stalled/paused;
- good wall-clock music behavior;
- can jump ahead after long server freeze.

**Option B — pause-aware media time**

- media semantic timeline pauses with chosen game pause semantics;
- feels more game-integrated;
- needs a well-defined pause source shared with server/client state.

This is not resolved by source research alone.

### Result

`NOT RUN`

---

## EXP-006 — Lua upload chunk performance

**Status:** `NOT RUN`  
**Related area:** upload API/resource limits  
**Risks:** `RISK-009`

### Question

What chunk size gives reasonable CC Lua memory/CPU/call overhead while allowing bounded copying to Java-owned storage?

### Compare

At least:

- 8 KiB;
- 16 KiB;
- 32 KiB;
- 64 KiB;
- possibly larger if CC memory/API behavior supports it cleanly.

Measure upload throughput and computer responsiveness for realistic 1–20 MiB files.

Do not confuse this with NeoForge's C2S packet limit; the Lua peripheral call is server-side.

### Result

`NOT RUN`

---

## EXP-007 — static vs streaming threshold

**Status:** `NOT RUN`  
**Related area:** memory/cache/source pools  
**Risks:** `RISK-004`, `RISK-010`

### Question

When should decoded finite content use a full static buffer versus a streamed/ring-buffer path?

### Inputs

- measured static vs streaming channel pool availability from EXP-003;
- decoded size;
- current decoded-cache budget;
- number of simultaneous emitters sharing content;
- seek/start/sync cost;
- client memory.

### Output

A budget-driven policy, not a permanent magic duration constant.

### Result

`NOT RUN`

---

## EXP-008 — MP3 decoder suitability

**Status:** `NOT RUN`  
**Related area:** codec selection  
**Risks:** `RISK-011`, licensing

### Question

Does the leading pure-Java MP3 candidate (initially evaluate JLayer or a better current alternative) satisfy HighAudio's sample-frame timeline needs?

Test corpus:

- CBR;
- VBR/Xing;
- VBRI if supported;
- files with LAME delay/padding metadata;
- short clips;
- long tracks;
- malformed/truncated frames.

Measure:

- reported duration vs decoded frames;
- seek error at multiple positions;
- resume after seek;
- gapless loop/replay behavior;
- decode speed;
- memory;
- license/packaging obligations.

### Result

`NOT RUN`

---

## EXP-009 — shared decode/buffer reuse across emitters

**Status:** `NOT RUN`  
**Related area:** dedupe/caching  
**Risks:** `RISK-010`

### Question

For N emitters playing identical content, what can safely be shared?

Compare:

- one compressed cache object;
- one decoder feeding a reusable decoded segment cache;
- shared static OpenAL buffer with multiple sources;
- streaming queues backed by shared decoded segments but independent source queue state.

Do not assume queued OpenAL buffer reuse/lifetime semantics until tested with the final Minecraft `Channel` backend.

### Result

`NOT RUN`

---

## EXP-010 — SPR exact-version integration

**Status:** `DEFERRED / NOT RUN`  
**Target:** SPR 1.21.1-1.5.1  
**Risks:** `RISK-008`

After playback/sync architecture is stable, inspect exact SPR source/JAR behavior and test:

- source discovery;
- reverb/occlusion;
- EFX ownership;
- source move;
- pause/resume/seek;
- stop/destroy;
- SoundEngine reload;
- 1/4/16-source performance.

Do not recreate the old CC:HQ compatibility bridge by default.

### Result

`NOT RUN`

---

# Experiment result template

When an experiment finishes, record:

```text
Result: PASS / FAIL / PARTIAL
Date:
Minecraft:
Java:
CC:T:
NeoForge:
SPR (if relevant):
Commit:
JAR SHA-256:
Logs/artifacts:
Observed facts:
Unexpected behavior:
Decision impact:
Follow-up experiment:
```

Then update `VERIFIED-FACTS.md`, affected ADRs, risks, roadmap, and handoff in the same development phase.
