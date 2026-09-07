# HighAudio current handoff

**Status:** canonical chat/session handoff  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Prepared:** 2026-09-07  
**Current branch:** `milestone-003-exp-003-capacity-sync-rebalance`  
**Next gate:** `MILESTONE-003` / `EXP-003` / `GATE-003`

Exact runtime/prototype evidence remains more authoritative than this summary.

## Fixed project target

Do not silently substitute newer versions:

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

## Completed gates

### MILESTONE-000 / GATE-000 — PASSED

Repository/bootstrap/version pinning/CI matrix remains valid.

### MILESTONE-001 / EXP-001 / GATE-001 — PASSED

Accepted integration: targeted CC:T `GenericSource` (`ADR-0008`).

Frozen EXP-001 candidate:

```text
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI run: 34082746562
JAR SHA-256:
0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Automatic evidence passed on NeoForge 21.1.247 and 21.1.248. Broad real-client evidence on 21.1.247 passed direct/wired block speakers, native CC:T methods, turtle, real pocket speaker, and reconstructed peripheral/lifecycle cases. The extra 21.1.248 gameplay repetition was explicitly waived after exact changelog/CI re-audit.

`ADR-0003` remains superseded fallback history; `ADR-0008` is Accepted.

### MILESTONE-002 / EXP-002 / GATE-002 — PASSED

Goal proven: arbitrary HighAudio-owned high-quality PCM can be rendered through Minecraft's own sound lifecycle without an independent raw OpenAL source manager.

Frozen EXP-002 candidate:

```text
branch: milestone-002-exp-002-minecraft-audio
code commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI run: 34088822441
JAR SHA-256:
515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Automatic evidence passed on both NeoForge 21.1.247 and 21.1.248. Real NeoForge 21.1.247 client evidence passed generated PCM audibility, Minecraft-owned `Channel` capture, attenuation, Records/master volume, natural completion, repeated explicit stop, F3+T rebuild/replay, integrated pause/resume, disconnect cleanup, and no observed HighAudio-specific OpenAL/runtime failure.

Manual evidence:

`docs/test-batches/evidence/TEST-BATCH-002-NEOFORGE-21.1.247.md`

Important EXP-002 constraints carried into EXP-003:

1. `AudioStream`/generated-stream `bytesRead` is **not audible playback position**.
2. Java `SoundEngine` object identity is **not** a renderer-generation identity across OpenAL rebuild.
3. Effective Minecraft volume zero may prevent channel allocation, so mute is not a guaranteed source-arming mechanism.

`ADR-0004` remains Proposed until EXP-003 resolves capacity and synchronization assumptions.

## MILESTONE-003 / EXP-003 — current work

Goal: establish a Minecraft-owned capacity policy that can meet the intended stress target, then select a measured local multi-source synchronization mechanism before production media/session architecture hardens around guesses.

### Part A vanilla baseline — PASS

Frozen strengthened baseline candidate:

```text
code commit:       a500f3bee773e4e5558fe3473367927ece637f9b
CI run:            34095196833
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256:
553919083f8d998fd7d3b0e143f8e76ad3da7b78862ee84c93d886110be41055
```

Real NeoForge 21.1.247 client evidence with SPR absent tested requested counts:

```text
1, 1, 2, 4, 6, 8, 10, 12, 16
```

Measured allocation:

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

Minecraft's own debug string reached `... + 8/8` and no ninth `PlayStreamingSourceEvent` capture appeared. All actually allocated streams remained active through the early snapshots and closed cleanly after the 10-inactive-tick finalization grace.

Conclusion: **vanilla Minecraft's streaming reservation on the tested runtime is 8 channels**. This is a policy/counter limit, not a claim that the OpenAL device has only eight sources.

Canonical evidence:

`docs/test-batches/evidence/TEST-BATCH-003-NEOFORGE-21.1.247.md`

### Re-evaluation after the 8-channel result

Do not jump straight to a static-audio backend or independent raw OpenAL ownership.

The strongest next candidate is a narrow **Minecraft-owned source-reservation rebalance**:

- keep the existing `SoundInstance + AudioStream + SoundManager` playback path;
- keep Minecraft `Library`/`Channel` ownership;
- preserve the runtime's existing combined static+streaming reservation;
- reserve up to 16 of those existing slots for streaming;
- reduce the static reservation by the same delta;
- do not increase the total source budget;
- do not add HighAudio-owned `alGenSources`/`alDeleteSources` lifecycle.

On the measured 255-channel runtime this means `247 static + 8 streaming -> 239 static + 16 streaming`, but the implementation derives the original reservation rather than treating those numbers as universal constants.

Why this candidate ranks ahead of the alternatives:

1. EXP-002 already proved the Minecraft-owned streaming backend and lifecycle.
2. The failure is specifically the streaming reservation, not PCM rendering.
3. Minecraft 1.21.1 exposes distinct static/streaming channel pools/counters.
4. Existing 1.21-family source-limit implementations demonstrate that these pool sizes are technically patchable.
5. SPR 1.21.1 hooks Minecraft `Library`/`SoundEngine`/`Channel` and `Channel.play()`. Keeping Minecraft-owned channels preserves the path SPR already expects; independent sources would require a separate compatibility design.

### Part A2 reservation rebalance — SPR-off real-client PASS

Proposed decision: `ADR-0009` remains **Proposed**, not accepted, until the exact SPR coexistence/reload comparison passes.

The first automatically green rebalance candidate (`fc4c63efc5377700d71a78683cd123dc60b7d635`, CI `34102697796`) was superseded before user manual testing because a post-pass re-evaluation found it could be too aggressive on lower-capacity devices.

Frozen conservative candidate:

```text
branch:             milestone-003-exp-003-capacity-sync-rebalance
code/CI commit:     82c195637de3987463c864c8f8493e9194410094
CI run:             34103604455
NeoForge 21.1.247:  PASS
NeoForge 21.1.248:  PASS
JAR SHA-256 on both matrix legs:
f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

Both matrix artifacts are byte-identical.

The candidate introduces one client-only experimental Mixin:

`client/audio/exp3/mixin/LibraryStreamingReservationMixin`

It:

- captures Minecraft's device-derived channel-count input during `Library.init(...)`;
- derives the exact vanilla 1.21.1 original static/streaming reservation;
- only applies the 16-stream rebalance when vanilla itself derives its normal maximum streaming reservation of 8;
- raises the eligible streaming reservation to at most 16 while subtracting the same delta from static;
- preserves the original combined reservation;
- leaves lower-capacity vanilla layouts unchanged rather than taking disproportionate static/SFX capacity;
- fails loudly if the actual constructor arguments do not match the derived vanilla reservation shape;
- does not own raw OpenAL sources.

Exact dev-client audio-init smoke on both `.247` and `.248` logged the expected `255 -> 247/8 -> 239/16` transform with `combinedPreserved=true` and `rebalanceApplied=true`. Automatic regression evidence also passed the M1 GenericSource self-check, packaged M2/M3 fixtures, bytecode inspection for absence of HighAudio-owned raw source allocation, and packaged-JAR dedicated-server startup on both target NeoForge versions.

#### Real NeoForge 21.1.247 / Windows / OpenAL evidence — PASS

The user then ran the conservative candidate with SPR absent on the real device:

```text
OpenAL Soft on Speakers (4- USB Audio Device)
```

Runtime rebalance:

```text
reportedChannelCount=255
originalStatic=247
originalStreaming=8
newStatic=239
newStreaming=16
combinedPreserved=true
rebalanceApplied=true
targetStreaming=16
```

Measured allocations:

```text
4  -> 4
8  -> 8
12 -> 12
16 -> 16
16 -> 16
16 -> 16  (explicit stop)
16 -> 16  (explicit stop)
16 -> 16
```

The 16-channel result therefore repeated **five times**. Every capture arrived from the `Sound engine` thread. Natural 16-channel runs retained `captures=16`, `activeSounds=16`, and `soundDebug=... + 16/16` through the t+5/t+20/t+40 snapshots. The explicit-stop runs also captured all 16 channels and closed all 16 streams cleanly.

Every completed run reached `phase=final` only after `inactiveTicks=10`. No stale-run capture was observed. Attempts to start a new count before finalization were refused as intended.

Minecraft static-side sounds remained available while all 16 streaming slots were occupied; the debug counter observed states such as `Sounds: 1/239 + 16/16` and `Sounds: 2/239 + 16/16`.

Across the supplied `latest.log` and `debug.log`, there were no `ERROR` or `FATAL` entries and no HighAudio/OpenAL/Mixin allocation failure. The warnings were unrelated YACL/offline/assets/goat-horn/shader warnings already outside the HighAudio path.

Conclusion: **the tested real runtime now meets the project's 16 simultaneous HighAudio streaming-channel stress target through Minecraft-owned channels without increasing the combined source reservation.**

Canonical Part A2 evidence:

`docs/test-batches/evidence/TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md`

### Next user-only evidence — exact SPR coexistence/reload comparison

Part A2's SPR-off capacity side is complete. The next justified launch is the short exact SPR `1.21.1-1.5.1` coexistence check.

Required checks in one launch:

1. install exact SPR 1.21.1-1.5.1 with the frozen HighAudio candidate;
2. confirm the game and sound engine start without Mixin/OpenAL conflict;
3. run `/highaudio_exp3 capacity 16` and wait for `phase=final`;
4. verify at least one ordinary Minecraft sound still plays while HighAudio is present;
5. press F3+T once and wait for the sound/resource reload to finish;
6. run `/highaudio_exp3 capacity 16` again and wait for `phase=final`;
7. preserve `latest.log` and `debug.log`.

Success requires the rebalance to reappear after reload, 16/16 allocation before and after reload, and no HighAudio/SPR/OpenAL/Mixin error. This is a capacity/coexistence proof only; full SPR acoustic correctness remains MILESTONE-010.

Do **not** mark ADR-0009 Accepted merely from the SPR-off result. If the exact SPR coexistence/reload run passes, ADR-0009 can be accepted for the M3 capacity policy and EXP-003 can move to Part B synchronization measurement.

See `docs/test-batches/TEST-BATCH-003.md` for exact acceptance criteria.

### Part B — synchronization remains undecided

Do not silently choose among the synchronization options before the SPR coexistence check closes the capacity policy:

- **A — pure Minecraft/high-level scheduling:** cleanest lifecycle/compatibility, possibly looser skew and weaker renderer-position visibility;
- **B — narrow accessor/control of Minecraft-owned OpenAL source:** preserves Minecraft ownership while enabling precise offsets/vector start, but adds localized exact-version coupling and must prove no preparation leak;
- **C — independent raw OpenAL ownership:** maximum control but duplicates source/lifecycle/category/reload/world cleanup and bypasses the normal SPR-facing Minecraft channel path; fallback only if Minecraft-owned approaches fail.

## Still deferred

Do not start real media upload, codecs, ContentId/cache/transfer, server MediaSession, production sync groups, moving emitters, production SPR integration, URL/live streaming, or VS2 work until the relevant later milestone.

## Manual-test cadence

Keep implementation/source/CI checks frequent but batch user-only Minecraft observations. The SPR-off Part A2 real-client capacity baseline is complete; the next justified client launch is the single exact SPR coexistence/reload run above.
