# Prototype and experiment ledger

**Status:** canonical unresolved-runtime ledger  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `EXP`, `PROTOTYPE`, `PASS-FAIL`, `RUNTIME-EVIDENCE`

An entry stays `NOT RUN` until the exact experiment is executed and its evidence is preserved. A research conclusion or partial automatic proof is not a completed experiment.

---

## EXP-001 — targeted SpeakerPeripheral GenericSource

**Status:** `NOT RUN` — automatic pre-gate evidence PASS; consolidated manual runtime gate pending  
**Related decisions:** `ADR-0008` (current), `ADR-0003` (superseded fallback)  
**Risks:** `RISK-001`, `RISK-002`, `RISK-021`

### Question

Can a registered CC:T `GenericSource` targeted at exact CC:T 1.120.0 `SpeakerPeripheral` expose a new HighAudio Lua method on the normal speaker while preserving native peripheral identity, lifecycle, native methods, and direct/wired behavior?

### Candidate history

EXP-001 originally evaluated a minimal additive Mixin. That candidate passed automatic build/startup checks on both target NeoForge versions, but before the manual gate the exact CC:T 1.120.0 generic-method path was re-evaluated.

A less invasive candidate was found: `ComputerCraftAPI.registerGenericSource` plus a generic method whose first target parameter is the exact internal `SpeakerPeripheral` class. This preserves the original CC:T peripheral object and does not transform CC:T bytecode.

`ADR-0003` is therefore superseded by proposed `ADR-0008`. The Mixin remains the first fallback if this candidate fails.

### Smallest implementation

- exact target stack only;
- one `SpeakerGenericSource` under `integration/cct`;
- one diagnostic `highAudioProbe()` method;
- one startup method-supplier self-check;
- no Mixin;
- no audio engine, content store, packets, codec, upload, session, sync, SPR, URL, or VS2 work.

### Automatic pre-gate evidence already obtained

A separate comparison branch established that the GenericSource candidate can:

- compile against exact CC:T 1.120.0;
- be processed by CC:T's exact generic/peripheral method supplier;
- contribute `highAudioProbe` to a `SpeakerPeripheral` subtype;
- retain native `playNote`, `playSound`, `playAudio`, and `stop` in the generated method map;
- register before server context startup;
- reach dedicated-server ready state on NeoForge 21.1.247 and 21.1.248.

Comparison evidence:

```text
branch: exp-001-genericsource-comparison
commit: ecacab361416c0bbdbd1bd789806f567317773db
CI run: 34078670979
JAR SHA-256:
e9a82ee4f4403881c01c4901b2dff88d86fc16cafa882581430064f9c2f1471a
```

This evidence does **not** complete EXP-001 because real Lua visibility, direct/wired behavior, and lifecycle transitions still require the consolidated manual gate.

### Procedure

1. Use the clean MILESTONE-001 GenericSource branch and its green CI artifact.
2. Launch NeoForge 21.1.247.
3. Place normal speaker next to a computer.
4. Inspect `peripheral.getMethods(side)` and call `highAudioProbe()`.
5. Exercise native `playNote`, `playSound`, `playAudio`, and `stop`, allowing normal temporary CC:T busy returns to be retried.
6. Attach the same speaker through wired modems; repeat discovery/calls and compare diagnostics.
7. Reboot/detach/reattach; repeat.
8. Unload/reload speaker chunk; repeat.
9. Break/re-place speaker at the same position; record identity behavior without assuming coordinate identity.
10. Equip turtle speaker; inspect method visibility/native behavior.
11. Inspect pocket speaker visibility if practical in the same session.
12. Repeat the critical subset on NeoForge 21.1.248.

### Pass criteria

- `highAudioProbe` is visible/callable on the intended normal speaker.
- Native `playNote`, `playSound`, `playAudio`, and `stop` remain present and usable.
- No duplicate HighAudio peripheral is exposed.
- Direct and wired attachment remain stable.
- Lifecycle transitions do not corrupt method exposure.
- Turtle/pocket exposure is observed rather than guessed.
- GenericSource registration/method generation/startup succeeds on both target NeoForge versions.

### Fail criteria

Any of:

- method not discovered/callable under normal default CC:T configuration;
- native method conflict or broken dispatch;
- duplicate/unstable peripheral caused by HighAudio;
- wired behavior regresses;
- lifecycle transition loses/corrupts method exposure;
- exact target stack fails to register/start;
- upgrade-speaker exposure cannot be gated/supported cleanly enough for the product direction.

A deliberate `disabled_generic_methods` rule which disables `cctweakedhighaudio:speaker` is an administrator configuration condition, not by itself an implementation failure; it must still be documented if ADR-0008 is accepted.

### Evidence to preserve

- exact mod/CC:T/NeoForge versions;
- branch/commit and built JAR SHA-256;
- CI startup/self-check logs;
- Lua method lists/call output;
- direct/wired/reload results table;
- server `[EXP-001] highAudioProbe ...` lines.

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
