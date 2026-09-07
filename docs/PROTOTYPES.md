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

## EXP-003 — streaming channel capacity and group start

**Status:** `IN PROGRESS — PART A RE-AUDITED; MANUAL RUNTIME NOT RUN`  
**Branch:** `milestone-003-exp-003-capacity-sync`  
**Related decisions:** `ADR-0004` plus future sync decision  
**Risks:** `RISK-004`, `RISK-005`, `RISK-008`

### Questions

1. How many HighAudio streaming sounds can Minecraft actually allocate on the test runtime before pool contention/failure, at least through the project's 16-source stress target?
2. Can a set of Minecraft-created channels be prepared and started with low enough measured skew for perceptually tight speaker sync?

### Part A — source capacity

Part A is intentionally implemented without raw OpenAL/source-id access.

Baseline commands remain:

```text
/highaudio_exp3 capacity 1
/highaudio_exp3 capacity 4
/highaudio_exp3 capacity 8
/highaudio_exp3 capacity 16
/highaudio_exp3 status
/highaudio_exp3 stop
```

The strengthened command accepts any count from 1 through 16 so a failure at 16 can be refined in the same client launch.

Re-audit established the following measurement boundary:

- the probe resource is explicitly marked streaming;
- NeoForge posts `PlayStreamingSourceEvent` only after the Minecraft-owned channel has had the stream attached and `channel.play()` invoked;
- therefore each unique capture is evidence of a real allocated/started Minecraft streaming channel, not merely a `SoundManager.play()` request;
- a result of 16/16 means **at least 16 concurrent HighAudio streaming channels under the tested conditions**, not that 16 is Minecraft/OpenAL's absolute maximum;
- if 16 does not fully allocate, intermediate values 1–16 can locate the highest reliable count at or below the project stress target.

The original Part A candidate (`abe6063f46077fd74c0a83d05660cf07e0f3a33c`, CI `34092379023`, JAR SHA `3e4b487221a39b13cfe5fc2382c6317c2f4ec60e38b8342fe0fffa3f38991b15`) was automatically green but was **superseded before any manual runtime evidence** after measurement-quality re-audit found avoidable confounds.

Strengthening changes made before the manual gate:

- a new run is refused until the previous run reaches its final inactive snapshot, avoiding a race with asynchronous sound-thread channel release;
- all capacity streams share one immutable generated PCM backing array while keeping independent stream cursor/close state, avoiding roughly one full 8-second PCM allocation/generation per source;
- each run and sound is tagged with a run token so stale asynchronous events cannot be counted into a later measurement;
- `PlayStreamingSourceEvent` performs only a lightweight sound-thread handoff into a `ConcurrentLinkedQueue`;
- ordinary collection/counter state is owned and processed on the render thread;
- the render thread does **not** call `Channel.stopped()` or otherwise poll OpenAL channel state cross-thread;
- captured channel identity is diagnostic only;
- lifecycle evidence uses unique channel captures, `SoundManager.isActive(sound)`, stream closure, and `SoundManager.getDebugString()`;
- whole-probe `heapDeltaBytes` is only a rough end-to-end observation and must not be interpreted as per-channel memory cost.

Real client Part A evidence has not yet been collected. The strengthened code must finish the exact NeoForge 21.1.247/21.1.248 automatic matrix before becoming the manual-test candidate.

Do not predeclare 16/32 as supported counts and do not derive a production source limit from OpenAL hardware maximum alone.

### Part B — synchronization candidates

Do not choose an architecture boundary before Part A evidence is understood.

**Option A — pure Minecraft/high-level scheduling**

- cleanest lifecycle/compatibility boundary;
- no private OpenAL/source-id coupling;
- separate `SoundManager.play(...)` operations may have measurable first-sample skew;
- public/high-level state may not expose renderer position precisely enough for drift measurement.

**Option B — narrow accessor/control of Minecraft-owned OpenAL sources**

- preserves Minecraft allocation/lifecycle/category/spatial setup;
- can expose source/sample offsets and potentially atomic `alSourcePlayv` on already-owned sources;
- adds localized exact-version private/OpenAL coupling;
- must prove prepare/arm does not leak audible samples or fight Minecraft/SPR state.

**Option C — independent raw OpenAL ownership**

- maximum source/queue/timing control;
- duplicates Minecraft source allocation/deletion, category integration, F3+T/device/world cleanup, and source-pool coexistence responsibilities;
- highest SPR/lifecycle risk;
- fallback only if A and B fail experimentally.

Measure:

- escaped audio before arm;
- start skew for 2/4/8/16 sources;
- pause/resume group skew;
- seek/re-arm behavior if required by the candidate;
- renderer/sample offset reliability on queued streams;
- available OpenAL latency/timing extensions rather than assuming them;
- waveform-level skew if practical.

### Pass criteria

- real source/channel capacity is measured well enough to set a conservative provisional limit for the intended product target;
- one synchronization mechanism is selected from measured evidence;
- required OpenAL/private access, if any, is precisely scoped;
- preparation leak, pause/resume, and renderer-offset behavior are documented;
- if atomic vector start is not reliable through Minecraft ownership, an alternative is documented before production sessions are built.

### Result

`IN PROGRESS`

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
