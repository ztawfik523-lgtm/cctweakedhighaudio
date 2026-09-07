# Prototype and experiment ledger

**Status:** canonical unresolved-runtime ledger  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `EXP`, `PROTOTYPE`, `PASS-FAIL`, `RUNTIME-EVIDENCE`

An entry stays `NOT RUN` until the exact experiment is executed and its evidence is preserved. A research conclusion is not a completed experiment.

---

## EXP-001 — additive SpeakerPeripheral Mixin

**Status:** `NOT RUN`  
**Related decisions:** `ADR-0003`  
**Risks:** `RISK-001`, `RISK-002`

### Question

Can an additive Mixin into exact CC:T 1.120.0 `SpeakerPeripheral` expose new public `@LuaFunction` methods while preserving all native peripheral identity/lifecycle/method behavior?

### Smallest implementation

- minimal NeoForge mod scaffold;
- exact CC:T 1.120.0 dependency;
- one additive Mixin;
- one method such as `highAudioProbe()`;
- no audio engine, no content store, no custom packets beyond diagnostics if avoidable.

### Procedure

1. Launch with NeoForge 21.1.247.
2. Place normal speaker next to a computer.
3. Inspect `peripheral.getMethods(side)` and call `highAudioProbe()`.
4. Call native `playNote`, `playSound`, `playAudio`, `stop` before and after HighAudio method calls.
5. Attach speaker through wired modem; repeat discovery/calls.
6. Reboot computer; repeat.
7. Unload/reload speaker chunk; repeat.
8. Break/re-place speaker at same position; record method/peripheral identity behavior.
9. Equip turtle speaker; inspect method visibility.
10. Inspect pocket speaker method visibility if test setup supports it.
11. Repeat entire critical subset on NeoForge 21.1.248.

### Pass criteria

- HighAudio method is visible/callable on intended speaker class(es).
- No native method disappears or changes dispatch.
- No duplicate peripheral is exposed.
- Wired/direct attachment remain stable.
- No Mixin transform error on either NeoForge version.
- Method annotations survive transformation in the assembled runtime.

### Fail criteria

Any of:

- method not discovered;
- native method conflict;
- duplicate/unstable peripheral identity;
- wired network behavior regresses;
- transform fails on target stack;
- additive method unintentionally breaks upgrade speakers in a way we cannot gate cleanly.

### Evidence to preserve

- exact mod/CC:T/NeoForge versions;
- startup log with Mixin diagnostics;
- Lua output for method lists/calls;
- direct/wired/reload results table;
- source commit/JAR SHA.

### Result

`NOT RUN`

---

## EXP-002 — Minecraft-owned high-quality AudioStream

**Status:** `NOT RUN`  
**Related decisions:** `ADR-0004`  
**Risks:** `RISK-003`, `RISK-004`, `RISK-008`

### Question

Can HighAudio render custom PCM through Minecraft-owned `SoundInstance` + `AudioStream`, observe/capture the resulting Channel through NeoForge sound events, and recover cleanly across sound-engine lifecycle events?

### Smallest implementation

- client-only generated PCM source (e.g. deterministic chirp/click);
- positional custom SoundInstance;
- custom AudioStream;
- normal SoundManager playback;
- listener for `PlayStreamingSourceEvent`;
- listener for `SoundEngineLoadEvent`;
- structured diagnostics for source creation/destruction/reload.

### Procedure

Test:

1. play/stop one generated stream;
2. move emitter position;
3. master volume 0/100%;
4. target sound-category volume 0/100%;
5. F3+T/resource reload;
6. output device reload/change if possible;
7. leave world/disconnect/rejoin;
8. run with SPR absent;
9. run with SPR 1.21.1-1.5.1 present and observe whether acoustic processing appears naturally.

### Pass criteria

- Minecraft owns normal SoundInstance/Channel lifecycle;
- stream is positional and obeys expected volume controls;
- Channel can be associated with our SoundInstance through public/event mechanisms;
- no persistent source after authoritative stop/world unload;
- sound can be recreated after SoundEngine reload/device reset;
- exact missing low-level access (if any) can be limited to a small accessor rather than a parallel OpenAL manager.

### Result

`NOT RUN`

---

## EXP-003 — streaming channel capacity and group start

**Status:** `NOT RUN`  
**Related decisions:** `ADR-0004` plus future sync ADR  
**Risks:** `RISK-004`, `RISK-005`, `RISK-008`

### Questions

1. How many HighAudio streaming/static sounds can Minecraft actually allocate on the test runtime before pool contention/failure?
2. Can a set of Minecraft-created channels be prepared and started with low enough skew for perceptually tight speaker sync?

### Part A — source capacity

Run 1, 4, 8, 16 simultaneous streams and log:

- Minecraft sound/Library debug string;
- streaming/static pool usage if available;
- channel acquisition failures or voice stealing;
- OpenAL error state;
- CPU/memory;
- behavior with ordinary Minecraft sounds competing;
- repeat with SPR enabled.

**Do not predeclare 16/32 as supported counts.**

### Part B — exact group start candidate

Candidate only:

```text
create via SoundManager
-> PlayStreamingSourceEvent captures Channel
-> prepare queued PCM while inaudible
-> pause/rewind/reset as legal
-> use smallest raw-source accessor if needed
-> alSourcePlayv(ready sources)
```

Use deterministic impulse/chirp PCM to measure start timing.

Measure:

- escaped audio before arm;
- sample offset immediately after group start;
- skew at 2/4/8/16 sources;
- pause/resume skew;
- group seek/re-arm behavior;
- whether queued streaming offsets behave as expected;
- available OpenAL extensions at runtime;
- if loopback/audio capture is practical, waveform-level skew.

### Pass criteria

There is a repeatable mechanism whose skew is measured and small enough to justify production sync work, with no significant lifecycle breakage or audible preparation leak.

If it fails, record the exact reason and compare:

- Minecraft scheduled starts without vector AL start;
- static buffers for synchronized finite content;
- different Channel interception point;
- direct OpenAL only for tightly synchronized media as a hybrid (last resort because lifecycle cost is high).

### Result

`NOT RUN`

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

Do not assume queued OpenAL buffer reuse/lifetime semantics until tested with the final Minecraft Channel backend.

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

When an experiment is run, append:

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

Then update `VERIFIED-FACTS.md`, affected ADRs, risks, and roadmap in the same development phase.
