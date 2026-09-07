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

Automatic evidence passed on NeoForge 21.1.247 and 21.1.248. Broad real NeoForge 21.1.247 client evidence passed direct block, wired remote, native `playNote`/`playSound`/`playAudio`/`stop`, turtle, real pocket speaker, recreated peripherals, and forced lifecycle reconstruction. A second 21.1.248 gameplay repetition was explicitly waived after exact changelog + packaged-server compatibility re-audit.

Accepted consequence: use public `ComputerCraftAPI.registerGenericSource` with exact-version internal `SpeakerPeripheral` targeting localized under `integration/cct`. Administrator `disabled_generic_methods` can intentionally disable the generic source/method.

---

## EXP-002 — Minecraft-owned high-quality AudioStream

**Status:** `PASS`  
**Related decisions:** `ADR-0004` remains Proposed until EXP-003  
**Risks:** `RISK-003`, `RISK-004`, `RISK-008`

### Question

Can HighAudio render custom PCM through Minecraft-owned `SoundInstance` + `AudioStream`, observe/capture the resulting Channel through NeoForge sound events, and recover cleanly across sound-engine lifecycle events?

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

Automatic evidence passed on both target NeoForge versions, including packaged-client classes and clean installed dedicated-server startup.

Real NeoForge 21.1.247 client evidence established:

- generated 48 kHz / 16-bit / mono PCM is audible through Minecraft's sound path;
- `PlayStreamingSourceEvent` exposes the real Minecraft-owned `Channel`;
- positional attenuation works;
- Records/Jukebox category and master volume work;
- natural completion closes/stops cleanly;
- repeated explicit `play -> stop -> status` closes/stops cleanly;
- F3+T rebuilds the sound/OpenAL renderer and playback works again afterward;
- integrated singleplayer pause/resume behaves naturally without audible play-ahead/skip;
- disconnect cleans active playback and it does not resurrect on rejoin;
- no independent raw OpenAL source manager is required.

Evidence: `docs/test-batches/evidence/TEST-BATCH-002-NEOFORGE-21.1.247.md`.

Unexpected but important observations:

- `AudioStream` bytes consumed is not audible renderer position; queued bytes may reach the end while the channel continues playing.
- Java `SoundEngine` object identity is not a reload-generation identity; OpenAL can rebuild while the Java object identity remains stable.
- a sound at effective Minecraft volume zero may be rejected before a streaming channel is allocated, so mute cannot be used as a guaranteed source-arming mechanism.

Decision impact: the Minecraft-owned playback/lifecycle half of `ADR-0004` is validated. Capacity and synchronization remain EXP-003 before ADR-0004 can be accepted.

---

## EXP-003 — streaming channel capacity and group start

**Status:** `IN PROGRESS`  
**Branch:** `milestone-003-exp-003-capacity-sync`  
**Related decisions:** `ADR-0004` plus future sync decision  
**Risks:** `RISK-004`, `RISK-005`, `RISK-008`

### Questions

1. How many HighAudio streaming sounds can Minecraft actually allocate on the test runtime before pool contention/failure?
2. Can a set of Minecraft-created channels be prepared and started with low enough measured skew for perceptually tight speaker sync?

### Part A — source capacity

Implemented first without raw OpenAL access.

Current diagnostic commands:

```text
/highaudio_exp3 capacity 1
/highaudio_exp3 capacity 4
/highaudio_exp3 capacity 8
/highaudio_exp3 capacity 16
/highaudio_exp3 status
/highaudio_exp3 stop
```

Instrumentation records:

- requested and captured channel counts;
- active/stopped channel counts;
- closed stream counts;
- `SoundManager.getDebugString()`;
- approximate JVM heap delta;
- sound-engine generation.

Compilation of the first Part A code has passed on both NeoForge 21.1.247 and 21.1.248. Final M3 packaging/server CI and real client capacity measurements are still pending.

Do not predeclare 16/32 as supported counts and do not derive a production limit from OpenAL hardware maximum alone.

### Part B — synchronization candidates

Do not choose an architecture boundary before measurement.

**A — pure Minecraft/high-level scheduling**

- cleanest lifecycle/compatibility;
- may have looser first-sample skew and weaker renderer-position visibility.

**B — narrow source-id/control accessor on Minecraft-owned channels**

- preserves Minecraft allocation/lifecycle while enabling precise offset measurement and possibly atomic `alSourcePlayv`;
- adds localized exact-version private/OpenAL coupling and must prove no preparation leak.

**C — independent raw OpenAL ownership**

- maximum control;
- highest lifecycle/source-pool/SPR cost;
- fallback only if A and B fail.

Measure escaped preparation audio, 2/4/8/16 start skew, pause/resume skew, source/sample offset reliability, and available latency/timing extensions rather than assuming them.

### Pass criteria

- actual source capacity is measured well enough for a conservative provisional limit;
- one synchronization mechanism is selected from measured evidence;
- required OpenAL/private access is precisely scoped;
- if atomic vector start is not reliable through Minecraft ownership, the alternative is documented before production sessions.

### Result

`IN PROGRESS`

---

## EXP-004 — emitter identity through block lifecycle

**Status:** `NOT RUN`  
**Related area:** `EmitterId`, lifecycle persistence  
**Risks:** `RISK-006`

Determine the exact object/events/persistence behavior for normal CC:T speakers across chunk unload/reload, dimension unload/reload, server save/restart, block break, and replacement at the same position. The goal is to distinguish a temporarily unloaded physical emitter from a destroyed/replaced emitter well enough to choose persistent `EmitterId` semantics.

### Result

`NOT RUN`

---

## EXP-005 — MediaClock pause/freeze semantics

**Status:** `NOT RUN / PRODUCT DECISION REQUIRED`  
**Related area:** `MediaClock`  
**Risks:** `RISK-007`

Observe dedicated-server TPS drop/freeze, integrated-server pause, client-only pause-like screens, and available tick-freeze tooling. Then choose deliberately between real monotonic media time and pause-aware media time. This is a product-semantics decision, not something source research can decide automatically.

### Result

`NOT RUN`

---

## EXP-006 — Lua upload chunk performance

**Status:** `NOT RUN`  
**Related area:** upload API/resource limits  
**Risks:** `RISK-009`

Compare at least 8, 16, 32, and 64 KiB Lua->Java upload chunks using realistic 1–20 MiB finite files. Measure throughput, memory, and computer responsiveness. Copy bytes into HighAudio-owned storage before they escape the CC:T call lifetime.

### Result

`NOT RUN`

---

## EXP-007 — static vs streaming threshold

**Status:** `NOT RUN`  
**Related area:** memory/cache/source pools  
**Risks:** `RISK-004`, `RISK-010`

Use measured EXP-003 static/streaming pool availability, decoded size, cache budget, emitter count, start/seek/sync cost, and client memory to choose a budget-driven static-vs-streaming policy rather than a permanent magic duration constant.

### Result

`NOT RUN`

---

## EXP-008 — MP3 decoder suitability

**Status:** `NOT RUN`  
**Related area:** codec selection  
**Risks:** `RISK-011`, licensing

Evaluate a current pure-Java MP3 candidate against CBR, VBR/Xing, VBRI where applicable, encoder delay/padding, short/long clips, and malformed/truncated media. Measure decoded-frame duration, seek error, gapless behavior, speed, memory, and packaging/license obligations.

### Result

`NOT RUN`

---

## EXP-009 — shared decode/buffer reuse across emitters

**Status:** `NOT RUN`  
**Related area:** dedupe/caching  
**Risks:** `RISK-010`

For N emitters playing identical content, compare sharing compressed content, decoded segment caches, static OpenAL buffers, and independent streaming queues backed by shared decoded segments. Do not assume queued-buffer reuse/lifetime semantics until measured on the final Minecraft-owned backend.

### Result

`NOT RUN`

---

## EXP-010 — SPR exact-version integration

**Status:** `DEFERRED / NOT RUN`  
**Target:** SPR 1.21.1-1.5.1  
**Risks:** `RISK-008`

After playback/sync architecture is stable, test source discovery, reverb/occlusion, EFX ownership, movement, pause/resume/seek, stop/destroy, sound-engine reload, and 1/4/16-source performance. Do not recreate the old CC:HQ compatibility bridge by default.

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

Then update verified facts, affected ADRs, risks, roadmap, and handoff in the same development phase.
