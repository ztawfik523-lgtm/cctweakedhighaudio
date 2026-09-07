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
- exact CC:T tag `v1.21.1-1.120.0` confirms the pinned Maven coordinates and explicitly warns that internal use is not stable API;
- repository-wide recheck found no stale CC:T 1.120.2 or `core-api` dependency reference;
- official NeoForge 1.21.1 NeoGradle MDK conventions match the scaffold's Java 21, UserDev, Gradle 9.2.1, Parchment, and loader-version setup;
- Java 21 and Minecraft 1.21.1 are explicit in build metadata;
- committed Gradle wrapper is used by CI rather than a separately installed Gradle executable;
- Gradle distribution download is checksum-pinned and the `wrapper` task preserves the same checksum on regeneration;
- CI run `34075809793` passed on both NeoForge `21.1.247` and `21.1.248`;
- validated build-relevant commit: `fb754659c4f2a6abe918c5fa7fa6dcad35be9bd0`;
- both CI legs passed official wrapper-JAR checksum validation, wrapper execution, compilation, packaged metadata validation, packaged `HighAudio.class` validation, and JAR artifact upload;
- CI rejects unexpanded metadata placeholders and verifies exact CC:T 1.120.0 / Minecraft 1.21.1 requirements plus the declared NeoForge compatibility range;
- **manual Minecraft launches used for MILESTONE-000: 0**.

The later integration pivots do **not** invalidate GATE-000. Do not redo MILESTONE-000 unless new evidence specifically contradicts it.

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

The extra NeoForge 21.1.248 gameplay repetition was explicitly waived after re-audit rather than being falsely recorded as run. The exact candidate already passed both development and installed packaged-server runtime compatibility checks on 21.1.248.

### Decision

`ADR-0008` is Accepted. HighAudio uses the public `ComputerCraftAPI.registerGenericSource` registration mechanism while deliberately targeting exact CC:T 1.120.0's non-public `SpeakerPeripheral`; that implementation coupling remains localized under `integration/cct`.

`ADR-0003` remains the first technical fallback if a future exact CC:T version makes the targeted GenericSource approach unusable.

---

## MILESTONE-002 — EXP-002 Minecraft-owned PCM streaming proof

**Status:** COMPLETE  
**Gate:** `GATE-002: PASSED`  
**Branch:** `milestone-002-exp-002-minecraft-audio`  
**Manual gate:** `TEST-BATCH-002: PASS`  
**Decision impact:** `ADR-0004` playback/lifecycle half validated; ADR remains Proposed until EXP-003

**Goal:** prove HighAudio can render arbitrary high-quality PCM through Minecraft's own sound lifecycle.

Frozen candidate:

```text
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI run: 34088822441
JAR SHA-256:
515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

The accepted prototype contains only:

- deterministic 8-second 48 kHz / 16-bit / mono generated PCM chirp;
- custom positional HighAudio `SoundInstance`;
- custom HighAudio `AudioStream`;
- normal `SoundManager.play` path;
- NeoForge `PlayStreamingSourceEvent` capture/diagnostics;
- `SoundEngineLoadEvent`/reload diagnostics;
- client command `/highaudio_exp2 play|stop|status`;
- no direct raw OpenAL source manager.

Automatic evidence passed both target NeoForge versions, including packaged-client classes, accepted EXP-001 regressions, development-server startup, and clean installed packaged-JAR dedicated-server startup.

The NeoForge 21.1.247 broad real-client gate proved:

- generated PCM is audible through Minecraft's normal sound path;
- `PlayStreamingSourceEvent` associates the HighAudio sound with a real Minecraft-owned `Channel`;
- position and linear attenuation behave normally;
- Records/Jukebox category volume and master volume behave normally;
- natural completion cleans the probe sound/stream;
- repeated explicit `play -> stop -> status` sequences cleanly stop and close the stream;
- F3+T rebuilds the sound/OpenAL renderer, old playback does not remain stuck, and new playback works afterward;
- integrated singleplayer pause/resume behaves naturally without audible skip/play-ahead;
- disconnect while active cleans playback and no stale sound resurrects after rejoin;
- no observed HighAudio-specific OpenAL/runtime failure;
- basic playback requires no independent raw OpenAL manager.

Important constraints discovered for MILESTONE-003:

- `AudioStream` bytes consumed/queued is **not** audible playback position;
- Java `SoundEngine` object identity is **not** a renderer-generation identity across reload;
- effective Minecraft volume zero may prevent channel allocation, so mute cannot be relied on as a guaranteed source-arming mechanism.

Manual evidence: `docs/test-batches/evidence/TEST-BATCH-002-NEOFORGE-21.1.247.md`.

Output-device reselection and the optional SPR observation were not required to close GATE-002. Full capacity, precise renderer position, and synchronization remain MILESTONE-003.

**Decision after gate:** keep `ADR-0004` Proposed until EXP-003 resolves capacity/synchronization assumptions; EXP-002 alone does not accept ADR-0004.

---

## MILESTONE-003 — EXP-003 capacity and synchronization proof

**Status:** IN PROGRESS  
**Branch:** `milestone-003-exp-003-capacity-sync`  
**Manual Part A:** NOT RUN — strengthened candidate under final automatic recheck  
**Test plan:** `TEST-BATCH-003`

**Goal:** measure the real limits and determine the final local multi-source sync mechanism before architecture hardens around guesses.

### Part A — channel/source capacity

Baseline stress points remain 1, 4, 8, and 16 simultaneous HighAudio streaming sounds. The strengthened diagnostic accepts any count from 1 through 16 so a failure at 16 can be refined without another build/launch.

Current measurement records:

- requested vs unique real `PlayStreamingSourceEvent` captures;
- active HighAudio sounds through `SoundManager.isActive`;
- closed stream counts;
- `SoundManager.getDebugString()`;
- approximate whole-probe JVM heap delta;
- sound-engine generation.

Exact re-audit established why a capture is meaningful: the probe sound resource is explicitly streaming, and NeoForge posts `PlayStreamingSourceEvent` only after Minecraft has attached the `AudioStream` to its `Channel` and called `channel.play()`. A unique capture therefore proves a real allocated/started Minecraft streaming channel rather than merely an accepted high-level play request.

The first automatically green Part A artifact (`abe6063f46077fd74c0a83d05660cf07e0f3a33c`, CI `34092379023`, SHA-256 `3e4b487221a39b13cfe5fc2382c6317c2f4ec60e38b8342fe0fffa3f38991b15`) was deliberately **superseded before manual testing** after re-audit found avoidable measurement confounds.

The strengthened probe now:

- refuses a new run until the previous run reaches its final inactive snapshot, avoiding asynchronous release contamination;
- shares one immutable generated PCM backing array across capacity streams while retaining independent stream cursor/close state;
- tags each run/sound with a run token to reject stale asynchronous events;
- hands sound-thread capture events into a `ConcurrentLinkedQueue` and keeps ordinary measurement state render-thread-owned;
- does not query `Channel.stopped()` or otherwise poll OpenAL channel state from the render thread;
- treats channel identity as diagnostic only;
- keeps direct raw OpenAL/source-id access entirely out of Part A.

Interpretation is intentionally conservative:

- `16/16` means **at least 16 concurrent HighAudio streaming channels under the tested runtime conditions**, not that 16 is Minecraft/OpenAL's absolute maximum;
- if 16 is short, intermediate counts locate the highest reliable count at or below the project's 16-source stress target;
- `heapDeltaBytes` is an end-to-end observation and not a per-channel memory cost;
- do not derive a production source limit from OpenAL's hardware maximum alone.

Before the manual gate, the strengthened code must pass exact NeoForge 21.1.247 and 21.1.248 build/package/server checks. Real client capacity evidence has not yet been collected.

### Part B — group start

There are three meaningful architectural candidates. Do not silently choose among them before measurement.

**Option A — pure Minecraft/high-level scheduling**

- cleanest lifecycle and compatibility boundary;
- no private OpenAL/source-id coupling;
- separate `SoundManager.play(...)` operations may have measurable first-sample skew;
- public/high-level state may not expose precise enough renderer position for drift measurement.

**Option B — narrow accessor/control of Minecraft-owned OpenAL sources**

- Minecraft still owns allocation/lifecycle/category/spatial setup;
- can expose precise source/sample offsets and potentially atomic `alSourcePlayv` on already-owned sources;
- adds localized exact-version private/OpenAL coupling;
- must prove prepare/arm does not leak audible samples or fight Minecraft/SPR state.

**Option C — independent raw OpenAL ownership**

- maximum source/queue/timing control;
- duplicates source allocation/deletion, category integration, F3+T/device/world cleanup, and source-pool coexistence responsibilities;
- highest risk for SPR and lifecycle correctness;
- fallback only if A and B fail experimentally.

Instrumentation should use a deterministic PCM click/chirp pattern and log source/sample offsets. If practical, capture loopback/system audio for objective waveform comparison.

Measure:

- samples escaping before arm;
- start skew for 2/4/8/16 sources;
- pause/resume group skew;
- seek/re-arm behavior;
- offset query reliability on queued streams;
- extension availability (`AL_SOFT_source_latency`, etc.) rather than assuming it.

**Gate GATE-003:**

- actual source capacity is known well enough to set a conservative provisional limit for the intended product target;
- one synchronization mechanism is selected with measured skew;
- required OpenAL/private access is precisely scoped;
- preparation leak, pause/resume, and renderer-offset behavior are documented;
- if atomic vector start is not reliable through Minecraft ownership, an alternative is documented before production sessions are built.

---

## MILESTONE-004 — first vertical slice: finite local media, one block speaker

**Status:** NOT STARTED

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

**Status:** NOT STARTED

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

**Status:** NOT STARTED

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

**Status:** NOT STARTED

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

**Status:** NOT STARTED

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

**Status:** NOT STARTED

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

**Status:** NOT STARTED  
**Target:** SPR 1.21.1-1.5.1

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
