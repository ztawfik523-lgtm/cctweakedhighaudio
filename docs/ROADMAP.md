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
- Gradle 9.2.1 binary-distribution SHA-256 pinned in wrapper configuration and wrapper regeneration;
- official Gradle 9.2.1 wrapper-JAR SHA-256 checked by CI before execution;
- GitHub Actions build matrix for NeoForge 21.1.247 and 21.1.248;
- packaged-JAR metadata/entrypoint validation in CI;
- canonical batched-manual-testing policy in `docs/TESTING.md`.

**GATE-000 re-audit evidence:**

- docs identify accepted vs proposed vs experiment-required decisions;
- exact CC:T tag `v1.21.1-1.120.0` confirms the pinned Maven coordinates (`common-api`, `forge-api`, runtime `forge`) and explicitly warns that internal use is not stable API;
- repository-wide recheck found no stale CC:T 1.120.2 or `core-api` dependency reference;
- official NeoForge 1.21.1 NeoGradle MDK conventions match the scaffold's Java 21, UserDev, Gradle 9.2.1, Parchment, and loader-version setup;
- Java 21 and Minecraft 1.21.1 are explicit in build metadata;
- committed Gradle wrapper is used by CI rather than a separately installed Gradle executable;
- Gradle distribution download is checksum-pinned and the `wrapper` task preserves the same checksum on regeneration;
- CI run `34075809793` passed on both NeoForge `21.1.247` and `21.1.248`;
- validated build-relevant commit: `fb754659c4f2a6abe918c5fa7fa6dcad35be9bd0`;
- both CI legs passed official wrapper-JAR checksum validation, wrapper execution, compilation, packaged `neoforge.mods.toml` validation, packaged `HighAudio.class` validation, and JAR artifact upload;
- CI rejects unexpanded metadata placeholders and verifies exact CC:T 1.120.0 / Minecraft 1.21.1 requirements plus the declared NeoForge compatibility range;
- branch diff from the previous `main` contains only intended bootstrap/reproducibility/documentation changes and no feature implementation;
- **manual Minecraft launches used for MILESTONE-000: 0**.

The MILESTONE-001 integration pivot does **not** invalidate GATE-000. Do not redo MILESTONE-000 unless new evidence specifically contradicts it.

---

## MILESTONE-001 — EXP-001 CC:T speaker augmentation proof

**Status:** COMPLETE  
**Gate:** `GATE-001: PASSED`  
**Accepted candidate:** targeted `GenericSource` (`ADR-0008`)  
**Superseded candidate:** additive `SpeakerPeripheral` Mixin (`ADR-0003`)  
**Manual gate:** `TEST-BATCH-001: PASS`

**Goal:** prove HighAudio can add a diagnostic Lua method to the real CC:T speaker without replacing the speaker peripheral or regressing native behavior.

The accepted experiment exposed only:

```lua
speaker.highAudioProbe()
```

### Accepted evidence

Frozen code/evidence candidate:

```text
branch: milestone-001-exp-001-genericsource
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI run: 34082746562
JAR SHA-256:
0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Automatic evidence passed on NeoForge 21.1.247 and 21.1.248, including exact CC:T method-supplier generation/invocation, speaker-only targeting, preservation of native speaker methods, live `ServerContext` registration, development-server startup, and clean installed packaged-JAR dedicated-server startup.

The NeoForge 21.1.247 broad real-client gate then proved:

- direct normal speaker exposure/callability;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain usable;
- wired remote speaker exposure/callability;
- turtle speaker exposure/callability, including recreated peripheral instances;
- real `PocketSpeakerPeripheral` exposure/callability;
- newly-created/reconstructed normal speaker peripherals continue to receive the GenericSource method;
- deterministic lifecycle reconstruction after disabling spawn-chunk retention;
- no observed HighAudio-specific runtime exception.

The extra NeoForge 21.1.248 gameplay repetition was explicitly waived after re-audit rather than being falsely recorded as run. The exact candidate already passed both development and installed packaged-server runtime compatibility checks on 21.1.248, and NeoForge's official 21.1.248 release delta from 21.1.247 is a `SolidBucketItem#getPlaceSound` backport unrelated to CC:T GenericSource/peripheral dispatch.

### Decision

`ADR-0008` is Accepted. HighAudio uses the public `ComputerCraftAPI.registerGenericSource` registration mechanism while deliberately targeting exact CC:T 1.120.0's non-public `SpeakerPeripheral`; that implementation coupling remains localized under `integration/cct`.

`ADR-0003` remains the first technical fallback if a future exact CC:T version makes the targeted GenericSource approach unusable.

---

## MILESTONE-002 — EXP-002 Minecraft-owned PCM streaming proof

**Status:** IN PROGRESS  
**Branch:** `milestone-002-exp-002-minecraft-audio`  
**Manual gate:** `TEST-BATCH-002: NOT RUN`

**Goal:** prove HighAudio can render arbitrary high-quality PCM through Minecraft's own sound lifecycle.

Current prototype contains only:

- deterministic 8-second 48 kHz / 16-bit / mono generated PCM chirp;
- custom positional HighAudio `SoundInstance`;
- custom HighAudio `AudioStream`;
- normal `SoundManager.play` path;
- NeoForge `PlayStreamingSourceEvent` capture/diagnostics;
- `SoundEngineLoadEvent`/reload diagnostics;
- client command `/highaudio_exp2 play|stop|status`;
- no direct raw OpenAL source manager.

Exact source/API recheck before implementation established that NeoForge 21.1.248 exposes the required `PlayStreamingSourceEvent` and `SoundEngineLoadEvent`, current FML automatically routes `IModBusEvent` subscribers to the mod bus, and both exact CC:T 1.120.0 plus NeoForge's own 1.21.1 client test use the same custom `SoundInstance#getStream(...)` + `AudioStream` pattern.

### Test

Use `TEST-BATCH-002` to cover in one consolidated client session:

- basic generated PCM playback;
- position and attenuation;
- `RECORDS` sound category and master volume;
- explicit stop and natural completion;
- real Minecraft `Channel` capture through `PlayStreamingSourceEvent`;
- F3+T/resource/sound-engine reload and replay;
- disconnect/world change and replay;
- output-device reload/change where practical;
- SPR absent baseline;
- exact SPR 1.21.1-1.5.1 basic follow-up observation only after the baseline is clean.

Full SPR correctness remains MILESTONE-010.

**Gate GATE-002:**

- sound is Minecraft-owned and positional;
- master/category volume semantics behave normally;
- a HighAudio `SoundInstance` can be associated with its real Minecraft `Channel` through official NeoForge events;
- no persistent leaked sound/channel after stop/natural completion/reload/world leave;
- source can be reconstructed after sound-engine reload;
- no independent raw OpenAL manager is required for basic playback.

**Decision after gate:** keep `ADR-0004` Proposed until EXP-003 also resolves capacity/synchronization assumptions; EXP-002 alone does not accept ADR-0004.

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