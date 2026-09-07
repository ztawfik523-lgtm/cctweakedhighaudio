# HighAudio roadmap

**Status:** canonical implementation order  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `MILESTONE`, `GATE`, `ROADMAP`, `PROOF-FIRST`

This roadmap is deliberately proof-first. A milestone is complete only when its gate passes; later architecture must not silently rely on an unresolved prototype assumption.

---

## MILESTONE-000 — repository/documentation/bootstrap

**Status:** COMPLETE  
**Gate:** `GATE-000: PASSED`

Fixed the exact stack, documentation/ADR/fact/risk ledgers, reproducible Gradle wrapper, NeoForge 21.1.247/21.1.248 CI matrix, packaged metadata checks, and batched-manual-testing policy. No manual Minecraft launch was required for this gate.

---

## MILESTONE-001 — EXP-001 CC:T speaker augmentation proof

**Status:** COMPLETE  
**Gate:** `GATE-001: PASSED`  
**Accepted:** targeted `GenericSource` (`ADR-0008`)  
**Fallback history:** additive `SpeakerPeripheral` Mixin (`ADR-0003`, superseded)  
**Manual gate:** `TEST-BATCH-001: PASS`

Goal proven: HighAudio can add `speaker.highAudioProbe()` to exact CC:T 1.120.0 speaker peripherals without replacing the peripheral or regressing native speaker behavior.

Frozen candidate:

```text
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI run: 34082746562
JAR SHA-256:
0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Automatic evidence passed on NeoForge 21.1.247 and 21.1.248. Broad real-client evidence passed direct/wired block speakers, native methods, turtle, real pocket speaker, recreated peripherals, and forced lifecycle reconstruction. The extra 21.1.248 gameplay repetition was explicitly waived after exact compatibility/changelog re-audit rather than falsely recorded as run.

---

## MILESTONE-002 — EXP-002 Minecraft-owned PCM streaming proof

**Status:** COMPLETE  
**Gate:** `GATE-002: PASSED`  
**Manual gate:** `TEST-BATCH-002: PASS`  
**Decision impact:** `ADR-0004` playback/lifecycle half validated; ADR remains Proposed until EXP-003

Goal proven: arbitrary HighAudio-owned high-quality PCM can be rendered through Minecraft's own sound lifecycle without an independent raw OpenAL source manager.

Frozen candidate:

```text
branch: milestone-002-exp-002-minecraft-audio
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI run: 34088822441
JAR SHA-256:
515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Automatic evidence passed both target NeoForge versions. Real NeoForge 21.1.247 client evidence proved:

- generated 48 kHz / 16-bit / mono PCM audibility;
- real Minecraft-owned `Channel` association through `PlayStreamingSourceEvent`;
- positional attenuation;
- Records/Jukebox and master-volume semantics;
- natural completion;
- repeated explicit stop + inactive status;
- F3+T sound-engine/OpenAL rebuild and successful replay;
- integrated singleplayer pause/resume without audible skip/play-ahead;
- disconnect cleanup with no stale playback after rejoin;
- no observed HighAudio-specific OpenAL/runtime failure.

Important constraints discovered for M3:

- `AudioStream` bytes consumed/queued is not audible playback position;
- Java `SoundEngine` identity is not a renderer-generation identity across reload;
- effective Minecraft volume zero may prevent channel allocation, so mute cannot be relied on as a source-arming mechanism.

Manual evidence: `docs/test-batches/evidence/TEST-BATCH-002-NEOFORGE-21.1.247.md`.

---

## MILESTONE-003 — EXP-003 capacity and synchronization proof

**Status:** IN PROGRESS  
**Branch:** `milestone-003-exp-003-capacity-sync`  
**Test plan:** `TEST-BATCH-003`

**Goal:** measure real Minecraft-owned source/channel capacity and select a measured local multi-source synchronization mechanism before production sessions are built.

### Part A — capacity

Create controlled sets of 1, 4, 8, and 16 simultaneous HighAudio streaming sounds.

Current instrumentation requests multiple normal Minecraft-owned positional streams and records:

- requested vs real `PlayStreamingSourceEvent` channel captures;
- active/running/stopped channel counts;
- closed stream counts;
- `SoundManager.getDebugString()`;
- approximate JVM heap delta;
- sound-engine generation.

Commands:

```text
/highaudio_exp3 capacity 1
/highaudio_exp3 capacity 4
/highaudio_exp3 capacity 8
/highaudio_exp3 capacity 16
/highaudio_exp3 status
/highaudio_exp3 stop
```

The first Part A implementation compiles on NeoForge 21.1.247 and 21.1.248. Final M3 packaging/server CI and real client capacity evidence are still required.

Do not derive a production source limit from OpenAL hardware maximum alone.

### Part B — synchronization

There are three meaningful architectural candidates; do not silently choose among them before measurement.

**Option A — pure Minecraft/high-level scheduling**

- cleanest compatibility/lifecycle boundary;
- may have looser first-sample skew and insufficient renderer-position visibility.

**Option B — narrow accessor/control of Minecraft-owned OpenAL source**

- preserves Minecraft allocation/lifecycle/category/spatial setup;
- can expose precise source offsets and potentially atomic `alSourcePlayv`;
- adds localized exact-version private/OpenAL coupling and must prove no preparation leak.

**Option C — independent raw OpenAL ownership**

- maximum control;
- duplicates source allocation/deletion, category/lifecycle/reload/world cleanup and raises SPR/source-pool coexistence risk;
- fallback only if A and B fail.

Measure:

- escaped samples during prepare/arm;
- start skew for 2/4/8/16 sources;
- pause/resume group skew;
- seek/re-arm behavior if required by the candidate;
- real source/sample offset reliability;
- available latency/timing extensions rather than assuming them.

**Gate GATE-003:**

- actual source capacity is known well enough to set a conservative provisional limit;
- one synchronization mechanism is selected from measured evidence;
- required OpenAL/private access is precisely scoped;
- if atomic vector start is not reliable through Minecraft ownership, an alternative is documented before production sessions are built.

---

## MILESTONE-004 — first vertical slice: finite local media, one block speaker

**Status:** NOT STARTED

**Goal:** play one real uploaded finite media file end-to-end using the validated speaker integration/backend.

Initial scope:

- normal placed speaker only;
- low-level Lua upload session plus helper `playFile(path)`;
- bounded byte chunks copied out of CC:T argument lifetime;
- server `ContentStore` and SHA-256 `ContentId`;
- initial WAV PCM decoder, then Ogg Vorbis;
- one server `MediaSession`;
- client content transfer/cache and one decoded PCM stream;
- play/stop/state query.

Not yet required: MP3, pause/seek, sync groups, moving emitters, URLs/live media, persistent restart sessions.

**Gate GATE-004:** one real file uploads, transfers, decodes, plays positionally, stops authoritatively, and cleans up through reconnect/reload without falling back to CC:HQ architecture.

---

## MILESTONE-005 — authoritative controls and lifecycle truth

**Status:** NOT STARTED

Add server-authoritative media clock/session revisions, pause/resume, seek, live volume, loop, state/position query, client mirror reconstruction, authoritative stop tombstones, audience enter/leave, late join, range/dimension/disconnect reconstruction, and F3+T/device rebuild while semantic session remains active.

Before closing, explicitly choose pause-aware vs real-monotonic media time and block-speaker chunk-unload/break/replacement semantics.

**Gate GATE-005:** lifecycle matrix reconstructs from server truth and stale audio never resurrects after authoritative stop.

---

## MILESTONE-006 — dedupe, cache policy, and long media

**Status:** NOT STARTED

Add content-have/need negotiation, bounded transfer scheduler, compressed cache eviction, decoded cache/ring, shared decode where useful, static-vs-streaming policy, long-file buffering, underrun handling, hash verification, and cancelled/stale transfer cleanup.

Stress same/different content across 1/4/16 speakers and long files.

**Gate GATE-006:** identical content is not redundantly transferred/decoded per speaker and memory remains bounded for long media.

---

## MILESTONE-007 — production synchronization

**Status:** NOT STARTED

Turn the validated EXP-003 mechanism into authoritative sync groups: shared schedule/timeline, readiness barrier, group prepare/start, pause/resume/seek, drift measurement/correction, late-member policy, and failure policy.

**Gate GATE-007:** 2/4/multi-speaker starts and transport controls meet the measured target and remain stable through lifecycle reloads.

---

## MILESTONE-008 — codec expansion

**Status:** NOT STARTED

Evaluate MP3 first (CBR/VBR/Xing/VBRI, duration, seek, delay/padding/gapless, malformed data), then FLAC, Opus where useful, and AAC/M4A only with a justified dependency/container plan.

**Gate GATE-008:** every accepted codec implements the same `DecoderSession` contract and passes duration/seek/malformed-media tests.

---

## MILESTONE-009 — moving emitters

**Status:** NOT STARTED

Support emitter transforms independently of media content/session state for turtle, pocket, and later VS-mounted speakers. Do not resend media just because position changes; define bounded update/interpolation and lifecycle/identity rules.

**Gate GATE-009:** moving speaker stays spatially coherent without content/session restart and cleans up correctly.

---

## MILESTONE-010 — Sound Physics Remastered compatibility

**Status:** NOT STARTED  
**Target:** SPR 1.21.1-1.5.1

Verify source discovery/interception, reverb/occlusion/EFX, pause/resume/seek lifecycle, movement if supported, destruction, F3+T/device reload, 1/4/16-source performance, and no duplicate EFX ownership.

Prefer natural integration through Minecraft source lifecycle; add an explicit adapter only if exact evidence requires one.

**Gate GATE-010:** acoustics work without source leaks/double-EFX and performance limits are documented.

---

## MILESTONE-011 — HTTP/live media [DEFERRED]

Only begin after finite-content playback is mature and product scope still requires it. Treat finite HTTP download, server/client fetch policy, SSRF/private-network controls, credentials/redirects, ICY, reconnect, HLS, live sync, and non-seekable streams as a separate design track.

Do not force live streams through finite-file `ContentId = SHA-256(complete file)` semantics.

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

If an experiment invalidates an architecture assumption, stop and update the ADR/architecture/roadmap first. Do not preserve milestone ordering merely because implementation work already started.
