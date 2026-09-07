# HighAudio roadmap

**Status:** canonical implementation order  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `MILESTONE`, `GATE`, `ROADMAP`, `PROOF-FIRST`

This roadmap is deliberately **proof-first**. We do not build the full media/session/cache system before proving the two riskiest integration points: CC:T speaker augmentation and Minecraft-owned high-quality streaming/synchronization.

A milestone is complete only when its **gate** passes. A partial prototype is not permission to silently continue as if its assumptions were verified.

---

## MILESTONE-000 — repository/documentation/bootstrap

**Status:** COMPLETE  
**Gate:** `GATE-000: PASSED`  
**Re-audit:** PASSED on 2026-09-07

**Goal:** make the target stack and architecture assumptions explicit before implementation.

Deliverables completed:

- exact CC:T 1.120.0 source pin;
- NeoForge 21.1.247 initial compile target;
- explicit compatibility/build matrix including 21.1.248;
- architecture docs, ADRs, fact ledger, prototype ledger, risk ledger;
- minimal NeoForge/CC:T build scaffold;
- minimal mod entrypoint and required-CC:T metadata;
- committed Gradle 9.2.1 wrapper for reproducible local/CI builds;
- GitHub Actions build matrix for NeoForge 21.1.247 and 21.1.248;
- packaged-JAR metadata/entrypoint validation in CI;
- canonical batched-manual-testing policy in `docs/TESTING.md`.

**GATE-000 re-audit evidence:**

- docs identify accepted vs proposed vs experiment-required decisions;
- exact CC:T tag `v1.21.1-1.120.0` confirms the pinned Maven coordinates (`common-api`, `forge-api`, runtime `forge`) and explicitly warns that internal/mixin use is not stable API;
- official NeoForge 1.21.1 NeoGradle MDK conventions match the scaffold's Java 21, UserDev, Gradle 9.2.1, Parchment, and loader-version setup;
- Java 21 and Minecraft 1.21.1 are explicit in build metadata;
- committed Gradle wrapper is used by CI rather than a separately installed Gradle executable;
- CI run `34075390502` passed on both NeoForge `21.1.247` and `21.1.248`;
- validated build-relevant commit: `2844a80db4df4795ede812d33859f2f1604e8b87`;
- both CI legs passed wrapper execution, compilation, packaged `neoforge.mods.toml` validation, packaged `HighAudio.class` validation, and JAR artifact upload;
- CI rejects unexpanded metadata placeholders and verifies exact CC:T 1.120.0 / Minecraft 1.21.1 requirements plus the declared NeoForge compatibility range;
- **manual Minecraft launches used for MILESTONE-000: 0**.

Manual runtime testing from this point follows `docs/TESTING.md`: automatic compile/CI checks remain frequent, while user-run Minecraft tests are accumulated into broader milestone-gate sessions unless an architecture-blocking runtime question requires an earlier probe.

---

## MILESTONE-001 — EXP-001 CC:T speaker augmentation proof

**Goal:** prove the proposed additive `SpeakerPeripheral` Mixin works in the real assembled mod without breaking native peripherals.

Implement only enough to expose something like:

```lua
speaker.highAudioProbe()
```

The probe should identify emitter kind and return stable diagnostic information. It should not start real media playback.

Test:

- placed speaker next to computer;
- placed speaker via wired modem/network;
- native `playNote`;
- native `playSound`;
- native `playAudio`;
- native `stop`;
- computer attach/detach;
- speaker chunk unload/reload;
- block break/re-place;
- turtle speaker method visibility;
- pocket speaker method visibility if practical;
- NeoForge 21.1.247;
- NeoForge 21.1.248.

**Gate GATE-001:**

1. HighAudio method is discoverable and callable on the intended speaker type(s).
2. Native methods remain present and behavior is not observably regressed.
3. Direct and wired attachment work.
4. No duplicate/peripheral-identity churn is caused by the integration.
5. The target builds load without Mixin failure on both NeoForge versions.

**Decision after gate:** accept/reject/supersede `ADR-0003`.

If it fails, compare targeted alternatives in this order:

1. more specific additive Mixin target;
2. `IDynamicPeripheral`/forwarding strategy if feasible without replacing identity incorrectly;
3. capability-provider wrapping only with explicit recursion/invalidation/equality proof.

Do not jump directly to a full CC:T fork.

---

## MILESTONE-002 — EXP-002 Minecraft-owned PCM streaming proof

**Goal:** prove HighAudio can render arbitrary high-quality PCM through Minecraft's own sound lifecycle.

Build:

- generated mono PCM source (sine/chirp/test sequence, no codec dependency);
- custom HighAudio `SoundInstance`;
- custom HighAudio `AudioStream`;
- normal `SoundManager.play` path;
- NeoForge `PlayStreamingSourceEvent` capture/diagnostics;
- `SoundEngineLoadEvent`/reload diagnostics.

Test:

- position and attenuation;
- `RECORDS`/selected sound category and master volume;
- stop/cleanup;
- source movement update mechanism;
- F3+T/resource reload;
- output-device reload/change where test environment allows;
- disconnect/world change;
- SPR absent;
- SPR 1.21.1-1.5.1 present (basic audibility/acoustic observation only, not final compatibility).

**Gate GATE-002:**

- sound is Minecraft-owned and positional;
- no persistent leaked sound/channel after stop/reload;
- source can be reconstructed after sound-engine reload;
- official NeoForge events give enough Channel/lifecycle access to proceed, or the exact missing private access is identified narrowly;
- no independent raw OpenAL manager is required for basic playback.

**Decision after gate:** accept/reject/supersede `ADR-0004`.

---

## MILESTONE-003 — EXP-003 capacity and synchronization proof

**Goal:** measure the real limits and determine the final local multi-source sync mechanism before architecture hardens around guesses.

### Part A — channel/source capacity

Create controlled sets of 1, 4, 8, and 16 simultaneous HighAudio streaming sounds.

Record:

- Minecraft `Library`/sound debug string;
- streaming pool used/max if accessible;
- acquisition failures/voice stealing;
- CPU/memory;
- behavior with other ordinary Minecraft sounds;
- SPR off and on.

Do not derive a production source limit from OpenAL's hardware maximum alone.

### Part B — group start

Test the candidate sequence:

```text
Minecraft creates/configures each Channel normally
-> capture Channel through NeoForge event
-> keep output inaudible while preparing
-> pause/reset/rewind/queue as required
-> obtain raw AL source id only through the smallest accessor if needed
-> alSourcePlayv(all ready sources)
-> measure first-sample skew
```

Instrumentation should use a deterministic PCM click/chirp pattern and log source/sample offsets. If practical, capture loopback/system audio for objective waveform comparison.

Measure:

- samples escaping before arm;
- start skew for 2/4/8/16 sources;
- pause/resume group skew;
- seek/re-arm behavior;
- offset query reliability on queued streams;
- extension availability (`AL_SOFT_source_latency`, etc.) rather than assuming it.

**Gate GATE-003:**

- actual source capacity is known well enough to set a conservative provisional limit;
- one synchronization mechanism is selected with measured skew;
- required OpenAL/private access is precisely scoped;
- if atomic vector start is not reliable through Minecraft ownership, an alternative is documented before production sessions are built.

---

## MILESTONE-004 — first vertical slice: finite local media, one block speaker

**Goal:** play a real uploaded media file end-to-end using the validated integration/backend.

Scope:

- normal placed speaker only for supported rendering;
- low-level upload session from Lua;
- bundled Lua helper for `playFile(path)` ergonomics;
- bounded upload chunks copied out of `IArguments` scope;
- server `ContentStore`;
- SHA-256 `ContentId`;
- initial WAV PCM decoder;
- Ogg Vorbis decoder after WAV baseline;
- one server `MediaSession`;
- client content transfer;
- compressed client cache;
- one decoded PCM stream/cache;
- play/stop/state query.

Intentionally not required yet:

- MP3;
- pause/seek;
- sync groups;
- moving speakers;
- URL/live streaming;
- persistent server restart sessions.

**Gate GATE-004:** one real file can be uploaded by a CC program, transferred once, decoded client-side, played positionally, stopped authoritatively, and cleaned up through reconnect/reload without falling back to CC:HQ architecture.

---

## MILESTONE-005 — authoritative controls and lifecycle truth

**Goal:** make server semantic state truthful and recoverable.

Add:

- `MediaClock` abstraction;
- sample-frame anchors;
- session revisions;
- pause/resume;
- seek;
- live volume without restart;
- loop semantics;
- `getMediaState`/position;
- client `SessionMirror` reconstruction;
- authoritative stop tombstone/revision handling;
- audience enter/leave;
- late join;
- range out/in;
- dimension out/back;
- disconnect/reconnect;
- F3+T/device rebuild while session remains active.

Before closing this milestone, explicitly choose:

- pause-aware vs real monotonic server media clock;
- block-speaker session behavior on chunk unload;
- block break/replacement semantics.

These are product decisions, not accidental implementation details.

**Gate GATE-005:** all lifecycle matrix cases reconstruct from server truth; stale client audio never resurrects after authoritative stop.

---

## MILESTONE-006 — dedupe, cache policy, and long media

**Goal:** make the architecture scale without decoding/transferring identical content repeatedly.

Add/measure:

- per-client content-have/need negotiation;
- content transfer scheduler with bounded queued bytes;
- client compressed cache eviction;
- decoded PCM cache/ring;
- shared decode for same content where practical;
- static vs streaming selection based on decoded-memory/source-pool budget;
- long-file streaming/ring buffers;
- underrun detection and recovery;
- hash verification;
- cancelled/stale transfer cleanup.

Test:

```text
1 speaker / 1 file
4 speakers / same file
16 speakers / same file
4 speakers / different files
16 speakers / different files (stress)
long file
client joins with cached file
```

**Gate GATE-006:** same content is not redundantly transferred/decoded per speaker; memory remains bounded for long media.

---

## MILESTONE-007 — production synchronization

**Goal:** turn the validated EXP-003 mechanism into authoritative sync groups.

Add:

- `SyncGroupId`;
- common server schedule/timeline;
- client readiness barrier;
- group prepare/start;
- pause/resume/seek group operations;
- drift measurement;
- conservative correction threshold;
- late member policy;
- failure policy when one client/source cannot prepare.

Do not let one slow client globally set session state to `BUFFERING`.

Measure and document a real synchronization quality target in milliseconds/sample frames after EXP-003 data exists.

**Gate GATE-007:** 2/4/multi-speaker starts and transport controls meet the measured target and remain stable through lifecycle reloads.

---

## MILESTONE-008 — codec expansion

**Goal:** broaden formats without contaminating playback/session architecture.

Order:

1. MP3 decoder prototype: VBR, Xing/VBRI, duration, seek accuracy, encoder delay/padding/gapless behavior, malformed input.
2. If acceptable, production MP3 integration.
3. FLAC.
4. Opus where useful.
5. AAC/M4A only with a justified decoder/container dependency.

**Gate GATE-008:** each codec implements the same `DecoderSession` contract and passes duration/seek/malformed-media tests.

---

## MILESTONE-009 — moving emitters

**Goal:** support emitter transforms independently of media content/session state.

Candidates:

- CC:T turtle speakers;
- pocket speakers;
- Valkyrien Skies-mounted block speakers.

Requirements:

- media content is never resent just because position changes;
- bounded transform update frequency;
- interpolation if necessary;
- dimension/entity lifecycle rules;
- stable enough `EmitterId` semantics.

**Gate GATE-009:** moving speaker remains spatially coherent without session/data restart and cleans up correctly.

---

## MILESTONE-010 — Sound Physics Remastered compatibility

**Goal:** explicitly support/benchmark SPR 1.21.1-1.5.1 using the final Minecraft-owned source backend.

Verify:

- source discovery/interception;
- reverb/occlusion/EFX application;
- pause/resume/seek source lifecycle;
- moving emitters if already supported;
- source destruction;
- F3+T/device reload;
- 1/4/16 source performance;
- no duplicate EFX ownership conflict.

Prefer natural integration through Minecraft source lifecycle. Add an explicit HighAudio↔SPR adapter only if exact evidence shows it is required.

**Gate GATE-010:** acoustics work without source leaks/double-EFX and performance limits are documented.

---

## MILESTONE-011 — HTTP/live media [DEFERRED]

Only begin after finite-content playback is mature and only if product scope requires it.

Separate design track for:

- HTTP finite download;
- server vs client fetch;
- SSRF/private-network policy;
- credentials/redirects;
- Icecast/Shoutcast/ICY metadata;
- reconnect;
- HLS;
- live synchronization;
- non-seekable streams.

Do not force live streams through `ContentId = SHA-256(complete finite file)` semantics.

---

# Runtime acceptance matrix

These cases must eventually be represented by automated tests, scripted runtime tests, or a documented manual gate:

```text
native CC:T playAudio/playSound/playNote
HighAudio play/stop/replay
pause/resume at same position
seek forward/backward
volume change without restart
loop
same content replay/cache hit
2 synchronized speakers
4 synchronized speakers
16-speaker stress target
one source joins an already active group
walk out of range and return
dimension out/back
disconnect/reconnect
second client joins mid-track
F3+T
output-device reload/change
singleplayer pause
server TPS drop/freeze
speaker chunk unload/reload
speaker block break/replacement
computer reboot
wired modem detach
malformed media
oversized media
interrupted transfer
same content across many speakers
dedicated server
NeoForge 21.1.247
NeoForge 21.1.248
SPR off/on where relevant
```

# Roadmap change rule

If an experiment invalidates an architecture assumption, **stop and update the ADR/architecture/roadmap first**. Do not preserve milestone ordering merely because work already started.
