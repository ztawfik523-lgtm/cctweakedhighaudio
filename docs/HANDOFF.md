# HighAudio current handoff

**Status:** canonical chat/session handoff  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Prepared:** 2026-09-07  
**Stable historical `main` baseline:** `a83dd483a501dd2e1d665e735d11fcdb0dd522e0`  
**Current completed milestone:** `MILESTONE-003` / `GATE-003: PASSED`  
**Next milestone:** `MILESTONE-004` — finite local media vertical slice

Exact evidence documents and accepted ADRs remain authoritative for the facts they record. Development branches are intentionally ahead of `main`; do not restart from old `main` milestone wording.

## Fixed project target

```text
Minecraft:     1.21.1
Java:          21
CC:Tweaked:    1.120.0
CC:T source:   tag v1.21.1-1.120.0
NeoForge:      21.1.247 baseline
Compatibility: 21.1.248
SPR future:    1.21.1-1.5.1
```

CC:T 1.120.2/current may be comparison material only, never exact-version proof.

## Testing rule

`docs/TESTING.md` is authoritative: **minimum manual testing means minimum**.

Prefer exact source research, build/package checks, bytecode inspection, automated development-client initialization, dedicated-server smoke tests, and self-measuring combined probes. Ask for another Minecraft launch only when the result is architecture-blocking and cannot be established automatically. Later-milestone compatibility concerns should not be pulled forward just to obtain reassurance.

## Completed milestones

### MILESTONE-000 / GATE-000 — PASSED

Repository/bootstrap/version pinning and the exact `.247/.248` CI matrix are established. Do not redo M0 unless new evidence specifically invalidates it.

### MILESTONE-001 / EXP-001 / GATE-001 — PASSED

Accepted CC:T integration is the targeted `GenericSource` in `ADR-0008`, not the older additive `SpeakerPeripheral` Mixin proposal.

Frozen accepted candidate:

```text
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI:     34082746562
SHA:    0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Broad real `.247` evidence covered direct/wired block speakers, native CC:T methods, turtle, pocket speaker, and recreated/lifecycle peripherals. `ADR-0003` is superseded fallback history; `ADR-0008` is Accepted.

### MILESTONE-002 / EXP-002 / GATE-002 — PASSED

HighAudio-owned 48 kHz PCM was proven through Minecraft-owned `SoundInstance + AudioStream + SoundManager + Channel` lifecycle without an independent raw OpenAL source manager.

Frozen candidate:

```text
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI:     34088822441
SHA:    515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Real `.247` evidence covered audibility, attenuation/category volume, natural/explicit stop, pause/resume, disconnect cleanup, F3+T renderer rebuild/replay, and clean lifecycle behavior.

Important carried facts:

1. stream bytes consumed are not audible playhead;
2. Java `SoundEngine` object identity is not a renderer-generation identity;
3. effective Minecraft volume zero may prevent channel allocation and is not a guaranteed arming mechanism.

### MILESTONE-003 / EXP-003 / GATE-003 — PASSED

M3 answered the two architecture-blocking questions: **can Minecraft-owned streaming meet the 16-source target, and is there a viable tightly synchronized local group-start primitive without HighAudio owning a second OpenAL engine?** Both are yes.

#### Capacity baseline

Vanilla Minecraft-owned streaming on the real `.247` runtime plateaued at 8 simultaneous streaming channels:

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

Minecraft reported `... + 8/8`. This is a Minecraft streaming-reservation policy boundary, not evidence that the OpenAL device can only own eight sources.

Evidence: `docs/test-batches/evidence/TEST-BATCH-003-NEOFORGE-21.1.247.md`.

#### Conservative reservation rebalance — Accepted

`ADR-0009` is Accepted. A narrow `LibraryStreamingReservationMixin` preserves Minecraft ownership and the runtime's combined source reservation. On the measured 255-channel runtime it changes:

```text
247 static + 8 streaming
        ->
239 static + 16 streaming
```

It does not increase the total source budget and does not create/delete HighAudio-owned OpenAL sources. Lower-capacity layouts where vanilla derives fewer than its normal eight streaming slots are left unchanged.

Frozen conservative capacity candidate:

```text
commit: 82c195637de3987463c864c8f8493e9194410094
CI:     34103604455
SHA:    f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

Real `.247` Windows/OpenAL Soft evidence passed 4/4, 8/8, 12/12 and repeated 16/16 allocation. The 16-channel target succeeded five times, including explicit-stop cycles. Ordinary static-side Minecraft sounds still allocated while streaming was saturated.

Evidence: `docs/test-batches/evidence/TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md`.

A standalone SPR/rebalance reload launch was intentionally **not** required to accept this policy. Exact SPR 1.5.1 coexistence/acoustics/performance belongs to MILESTONE-010 and should be tested there in one bundled gate. If later evidence reveals a conflict, revisit ADR-0009.

#### Local start synchronization

One real-client command compared:

- ordinary Minecraft/high-level start;
- a narrow synchronized vector start over already Minecraft-owned sources using core OpenAL `alSourcePlayv`.

The user ran the complete 2/4/8/16 comparison twice. Both modes measured zero relative `AL_SAMPLE_OFFSET` spread at the sampled checkpoints through 16 sources. The vector path also had full captures, `vectorError=0`, no observed pre-pause/post-rewind sample advance in the diagnostic, and clean stream closure.

Evidence: `docs/test-batches/evidence/TEST-BATCH-003-PARTB-NEOFORGE-21.1.247.md`.

The high-level result is strong for already-ready streams, but future decode/cache/network streams may become ready at different times. Therefore the synchronized vector primitive remains useful for an explicit group-readiness behavior.

#### Accepted renderer boundary

`ADR-0004` is now **Accepted**. HighAudio keeps Minecraft-owned `SoundInstance`/`AudioStream`/`Channel`/OpenAL source lifecycle. Narrow low-level access is permitted only for explicitly justified timing/measurement operations over Minecraft-owned sources. HighAudio does not create a parallel OpenAL source/device/context manager.

## Playback timing model — ADR-0010 remains Proposed

Timing is modeled by **intent**, not by exposing A/B/C implementation names to Lua.

### `immediate` — default / fastest

Play as soon as this sound is ready through normal Minecraft-owned playback. No group barrier and no artificial sync delay. This is the default for button sounds, one-shot SFX, voice lines, independent ambience, and ordinary one-speaker playback.

### `together` — explicit local group start ASAP

Wait until all required local participants are prepared, then start the group together immediately. Leading implementation uses the proven `alSourcePlayv` primitive on Minecraft-owned sources. A one-member `together` request should normally collapse to `immediate`.

### `scheduled` — optional timeline start

Align audible media sample zero with a specific HighAudio/session timeline point. The current candidate uses OpenAL Soft device-clock scheduled start (`alSourcePlayAtTimevSOFT`) when the exact capability set is present.

Important semantics:

- scheduled is optional and is **not** the default;
- there is no fixed public 100 ms latency tax;
- any silent preparation preroll is an internal renderer detail;
- source-start device time, media-zero renderer time, and estimated physical-output time are distinct;
- output-device latency matters for future multi-client/session "heard at T" mapping;
- raw OpenAL device clocks must not be exposed directly to Lua;
- if precise scheduled timing is requested but unavailable, do not silently claim a weaker start met the requested timestamp.

A one-speaker ordinary SFX stays `immediate`; a one-speaker scheduled request is still meaningful when it is synchronizing to an external/session timeline.

### Scheduled/device-clock automatic feasibility

The exact `.247/.248` target stack automatically reports after OpenAL initialization:

```text
sourceStartDelay=true
sourceLatency=true
deviceClock=true
available=true
```

The strengthened diagnostic distinguishes source start, media-zero renderer time, physical-output latency, and uses the atomic `AL_SAMPLE_OFFSET_CLOCK_SOFT` query for source-offset/device-clock measurement. It also avoids falsely clock-compensating a future-scheduled source while its offset is still frozen before the target time.

The strengthened code candidate at `ecb6c9d8a787184033f08082f888a0283b1d6ec5` passed CI run `34121266402` on both NeoForge 21.1.247 and 21.1.248, including Java/build, packaged timing audit, development-client sound-engine/capability smoke, accepted M1 server regression, packaged-JAR dedicated-server startup, and artifact upload.

This proves the **capability and integration boundary**, not end-to-end audible scheduled timing on the user's physical device. That optional behavior is deliberately not a GATE-003 blocker. Validate it later in a real session/synchronization milestone when the session clock and real media pipeline exist, so one test answers something product-meaningful.

## Why GATE-003 is closed without another Minecraft launch

The current project requirement was to avoid repeated synthetic manual tests. M3 already has real-device evidence for:

- vanilla 8-stream limit;
- repeated 16/16 Minecraft-owned capacity after conservative rebalance;
- clean natural/explicit cleanup;
- ordinary static sounds coexisting with 16/16 streams;
- 2/4/8/16 local synchronized vector start;
- ordinary high-level start measurement for comparison.

A separate C-only audible probe would prove an optional timing primitive before the real session clock exists and would not replace later production session testing. Likewise, synthetic pause/resume/seek group transport belongs with real authoritative controls/sessions in MILESTONE-005/MILESTONE-007 rather than forcing another M3 launch.

Therefore:

```text
EXP-003:       PASS for required renderer architecture
GATE-003:      PASSED
MILESTONE-003: COMPLETE
ADR-0004:      ACCEPTED
ADR-0009:      ACCEPTED
ADR-0010:      PROPOSED optional timing-intent model
```

## Next — MILESTONE-004

M4 is the first actual product vertical slice: **one normal placed CC:T speaker plays one finite uploaded file end-to-end**.

Planned first path:

```text
CC Lua file
  -> bounded begin/write/finish upload
  -> SHA-256 ContentId over original file bytes
  -> server ContentStore
  -> bounded server->client content transfer
  -> client compressed cache
  -> WAV PCM decoder baseline
  -> Minecraft-owned HighAudio stream
  -> normal positional computercraft:speaker
```

Key M4 constraints already established:

- upload chunks that outlive a Lua call must be copied into HighAudio-owned memory/storage before asynchronous use;
- transport must be chunked and bounded; do not send arbitrary songs in one NeoForge custom payload;
- content transport remains separate from small session/control state;
- WAV PCM is the first correctness codec; Ogg Vorbis follows only after the vertical slice is sound;
- no MP3, sync groups, moving speakers, URL/live streaming, or broad SPR work is required for the first M4 slice.

Before implementation, re-read exact CC:T 1.120.0 argument/lifecycle behavior and exact NeoForge 21.1.247/21.1.248 payload-registration/codec APIs. Keep CC:T implementation details inside `integration/cct` and media/network/client systems CC:T-agnostic.

## Still deferred

Production pause/seek/loop/lifecycle truth (M5), cache/dedupe/long-media scaling (M6), production sync groups/drift correction (M7), codec expansion (M8), moving emitters (M9), exact SPR compatibility (M10), URL/live streaming (M11), and VS2-specific work remain later milestones.
