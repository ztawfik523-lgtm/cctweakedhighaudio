# HighAudio roadmap

**Status:** canonical implementation order  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `MILESTONE`, `GATE`, `ROADMAP`, `PROOF-FIRST`

This roadmap is deliberately proof-first. A milestone is complete only when its gate is supported by the evidence that milestone actually needs. Manual Minecraft testing follows `docs/TESTING.md`: broad, self-measuring, and minimal.

---

## MILESTONE-000 — repository/documentation/bootstrap

**Status:** COMPLETE  
**Gate:** `GATE-000: PASSED`

Established the exact Minecraft 1.21.1 / Java 21 / CC:T 1.120.0 / NeoForge 21.1.247–21.1.248 stack, reproducible Gradle wrapper/build, package validation, and CI matrix.

Validated build-relevant commit: `fb754659c4f2a6abe918c5fa7fa6dcad35be9bd0`  
Green re-audit CI: `34075809793`

Do not redo M0 unless new evidence specifically invalidates it.

---

## MILESTONE-001 — CC:T speaker augmentation proof

**Status:** COMPLETE  
**Gate:** `GATE-001: PASSED`  
**Accepted:** targeted `GenericSource` (`ADR-0008`)  
**Superseded fallback:** additive `SpeakerPeripheral` Mixin (`ADR-0003`)

Frozen accepted candidate:

```text
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI:     34082746562
SHA:    0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Real runtime evidence covered normal direct/wired speakers, native methods, turtle, pocket speaker, and reconstructed peripherals.

---

## MILESTONE-002 — Minecraft-owned arbitrary PCM proof

**Status:** COMPLETE  
**Gate:** `GATE-002: PASSED`

Frozen candidate:

```text
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI:     34088822441
SHA:    515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Proved generated 48 kHz PCM through Minecraft-owned `SoundInstance + AudioStream + SoundManager + Channel`, including positional/category behavior, stop, pause, disconnect cleanup, and F3+T reconstruction.

Important facts carried forward: stream bytes consumed are not audible playhead; Java `SoundEngine` identity is not a renderer-generation clock; effective volume zero may prevent source allocation.

---

## MILESTONE-003 — capacity and local synchronization proof

**Status:** COMPLETE  
**Gate:** `GATE-003: PASSED`  
**Accepted decisions:** `ADR-0004`, `ADR-0009`  
**Optional timing model:** `ADR-0010` remains Proposed

**Goal:** prove the Minecraft-owned renderer can meet the project's 16-source target and provide a viable tightly synchronized local group-start primitive without an independent HighAudio OpenAL engine.

### Capacity result

Real NeoForge 21.1.247 baseline measured the vanilla Minecraft streaming reservation at 8 concurrent streams:

```text
1->1, 2->2, 4->4, 6->6, 8->8,
10->8, 12->8, 16->8
```

A conservative `LibraryStreamingReservationMixin` then preserved the existing combined source reservation while raising the streaming reservation to 16 on eligible normal layouts. On the tested 255-source runtime:

```text
247 static + 8 streaming
->
239 static + 16 streaming
```

Real client testing obtained 16/16 five times, including explicit-stop runs; ordinary static-side sound activity remained available during 16/16 streaming use.

Frozen rebalance candidate:

```text
commit: 82c195637de3987463c864c8f8493e9194410094
CI:     34103604455
SHA:    f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

`ADR-0009` is Accepted. The policy does not increase the total source budget and leaves weaker-than-normal layouts untouched.

### Local start result

One combined real-client probe compared ordinary Minecraft start against a narrow synchronized `alSourcePlayv` start over already Minecraft-owned sources at 2/4/8/16 sources. The whole comparison was run twice.

Both modes measured zero relative `AL_SAMPLE_OFFSET` spread at the sampled checkpoints through 16. The vector mode had `vectorError=0`, no observed pre-pause/post-rewind sample advance in the probe, and clean stream closure.

This proves a viable local `together` primitive. Ordinary Minecraft start remains the default fastest behavior; the vector barrier remains useful because real decode/cache/network streams may become ready at different times.

### Timing intents

`ADR-0010` defines the proposed public semantics:

- `immediate`: default/fastest, no group barrier;
- `together`: explicitly wait for all local participants, then start together ASAP;
- `scheduled`: optional timeline start, capability-gated against OpenAL Soft device-clock timed start.

The optional scheduled/device-clock capability and integration boundary are automatically green on both exact NeoForge targets. The strengthened candidate at `ecb6c9d8a787184033f08082f888a0283b1d6ec5` passed CI `34121266402`, including client sound-engine initialization/capability detection and package/server checks.

End-to-end audible scheduled timing is deliberately **not** a GATE-003 prerequisite. It should be validated later with the real session timeline/media pipeline so a manual test proves product behavior rather than a synthetic clock primitive.

### GATE-003 closure

Passed because:

- actual vanilla streaming capacity is measured;
- the 16-source project target is repeatedly proven with Minecraft still owning channels/sources;
- one explicit synchronized local group-start mechanism is real-client proven through 16;
- required low-level access is narrow and localized;
- no independent OpenAL source/device/context manager is needed.

Synthetic pause/resume/seek group transport is deferred to M5/M7 where real authoritative sessions exist. Broad SPR coexistence belongs to M10.

---

## MILESTONE-004 — first vertical slice: finite local media, one block speaker

**Status:** IMPLEMENTED; AUTOMATED VALIDATION COMPLETE; AUDIBLE GATE-004 PENDING

**Goal:** play one real finite file end-to-end through a normal placed CC:T speaker using the accepted M1–M3 architecture.

First vertical path:

```text
CC Lua file
  -> bounded begin/write/finish upload
  -> SHA-256 ContentId over original file bytes
  -> bounded server ContentStore
  -> bounded/chunked server->client transfer
  -> compressed client cache
  -> WAV PCM decoder
  -> Minecraft-owned HighAudio stream
  -> normal positional computercraft:speaker
```

Required scope:

- normal placed speaker first;
- low-level upload session from Lua;
- upload chunks copied out of `IArguments` lifetime before async use;
- explicit upload size/concurrency/timeout/abort bounds;
- server `ContentStore`;
- SHA-256 `ContentId`;
- initial WAV PCM decoder;
- bounded content transfer below NeoForge payload ceilings;
- client compressed-content cache sufficient for the vertical slice;
- one authoritative play/stop path;
- bundled Lua helper only after the low-level upload shape is sound.

Useful follow-up inside M4 after WAV works: Ogg Vorbis. Do not make Vorbis a prerequisite for proving the first end-to-end path if it slows the gate.

Intentionally not required yet:

- MP3;
- production pause/seek/loop;
- sync groups;
- long-media streaming/ring-buffer policy;
- moving speakers;
- URL/live streaming;
- persistent restart sessions;
- broad SPR compatibility.

**GATE-004:** one real file can be uploaded by a CC program, content-addressed/stored, transferred in bounded chunks, decoded client-side, played positionally through a normal CC:T speaker, stopped authoritatively, and cleaned without bypassing the accepted Minecraft-owned renderer architecture.

Manual validation should be one consolidated vertical-slice session only after source/CI/package/client/server checks are green.

Implemented M4 slice:

- six low-level methods on placed block speakers: bounded upload begin/write/finish/abort plus play/stop;
- SHA-256 `ContentId`, a 32 MiB server LRU content store, and useful server/client deduplication;
- request-on-cache-miss transfer in 32 KiB clientbound chunks, with one delivery per client/session;
- a 16 MiB client compressed-content LRU, at most four/8 MiB incomplete client transfers, and at most four/8 MiB decoded active playbacks;
- strict RIFF/WAVE integer PCM decode for positional mono, 8-bit unsigned or 16-bit signed little-endian, at 8–48 kHz;
- positional `SoundInstance` + `AudioStream` playback through Minecraft's normal `SoundManager` path;
- authoritative replacement/stop, server-stop cleanup, client reload/logout cleanup, computer-detach cleanup, and 30-second incomplete-upload expiry;
- deterministic unit coverage plus development dedicated-server registration/Mixin smoke.

Upload limits are 2 MiB per file, 16 KiB per Lua write, two incomplete uploads per computer/speaker, and 16 incomplete uploads server-wide. Server session state is capped at 256 sessions. Initial play announcements target players in the speaker's dimension within 64 blocks. Late-listener recovery and broader session lifecycle remain M5 work.

The remaining audible gate is specified in [`test-batches/TEST-BATCH-004.md`](test-batches/TEST-BATCH-004.md). Until it passes, the implementation is not evidence that real output is audible.

---

## MILESTONE-005 — authoritative controls and lifecycle truth

**Status:** NOT STARTED

Add server `MediaClock`, sample-frame anchors/revisions, pause/resume, seek, live volume, loop semantics, state/position queries, client reconstruction, audience enter/leave, late join, range/dimension/reconnect behavior, and F3+T/device rebuild while a session remains active.

Explicitly choose pause-aware vs real monotonic media time, chunk-unload behavior, and block break/replacement semantics.

**GATE-005:** lifecycle reconstruction comes from server truth and stale client audio never resurrects after authoritative stop.

---

## MILESTONE-006 — dedupe, cache policy, and long media

**Status:** NOT STARTED

Add bounded content-have/need negotiation, transfer scheduler, compressed/decoded cache eviction, shared decode where practical, long-file ring buffers, underrun detection/recovery, hash verification, and cancelled-transfer cleanup.

**GATE-006:** identical content is not redundantly transferred/decoded per speaker and memory remains bounded for long media.

---

## MILESTONE-007 — production synchronization

**Status:** NOT STARTED

Turn the M3 timing primitives into authoritative sync groups: `SyncGroupId`, common schedule/timeline, readiness barrier, `together`/scheduled behavior, group pause/resume/seek, drift measurement/correction, late-member policy, and failure policy.

This is where optional C/scheduled end-to-end timing should be validated against the real session clock if the feature remains desired.

**GATE-007:** multi-speaker starts and transport controls meet the measured quality target and remain stable through lifecycle reconstruction.

---

## MILESTONE-008 — codec expansion

**Status:** NOT STARTED

Add MP3 only after VBR/duration/seek/gapless/malformed behavior is proven. FLAC/Opus next where useful; AAC/M4A only with a justified dependency/container path.

**GATE-008:** each codec implements the common decoder contract and passes correctness/malformed-media tests.

---

## MILESTONE-009 — moving emitters

**Status:** NOT STARTED

Support turtle/pocket/VS2 emitter transforms separately from media/session state. Content must not be resent merely because position changes.

**GATE-009:** moving emitters stay spatially coherent without media/data restart and clean up correctly.

---

## MILESTONE-010 — Sound Physics Remastered compatibility

**Status:** NOT STARTED  
**Exact target:** SPR 1.21.1-1.5.1

Verify source interception, reverb/occlusion/EFX, lifecycle operations, F3+T/device reload, and 1/4/16-source performance. Prefer natural compatibility through Minecraft-owned channels; add an adapter only if exact evidence requires one.

**GATE-010:** acoustics work without source leaks/double-EFX and performance limits are documented.

---

## MILESTONE-011 — HTTP/live media

**Status:** DEFERRED

Finite HTTP downloads, server/client fetch policy, SSRF controls, credentials/redirects, ICY/HLS/live reconnect, and non-seekable synchronization are a separate architecture track. Do not force live streams into finite `ContentId = SHA-256(complete file)` semantics.

---

## Runtime acceptance matrix

Across the finished project, automated/scripted/manual evidence must eventually cover native CC:T methods; HighAudio play/stop/replay; authoritative pause/resume/seek/volume/loop; cached replay; 2/4/16 synchronized speakers; late audience members; range/dimension/reconnect; F3+T/output-device changes; singleplayer pause; server timing disruption; speaker chunk/block/computer/wired lifecycle; malformed/oversized media; interrupted transfer; same content across many speakers; dedicated server; both NeoForge targets; and SPR off/on where relevant.

## Roadmap change rule

If an experiment invalidates an architecture assumption, stop and update the ADR/architecture/roadmap before expanding production code. Do not preserve a milestone ordering merely because work has already been written.
