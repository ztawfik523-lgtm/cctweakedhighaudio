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

### Part A2 reservation-rebalance automatic candidate — PASS

Proposed decision: `ADR-0009` remains **Proposed**, not accepted.

Frozen automatic candidate:

```text
branch:             milestone-003-exp-003-capacity-sync-rebalance
code/CI commit:     fc4c63efc5377700d71a78683cd123dc60b7d635
CI run:             34102697796
NeoForge 21.1.247:  PASS
NeoForge 21.1.248:  PASS
JAR SHA-256 on both matrix legs:
6a9e1eeb548b7f2b3b985f3357355510d5e8dfcbbb4319d1fbce6c11c0dabe96
```

Both matrix artifacts are byte-identical.

The candidate introduces one client-only experimental Mixin:

`client/audio/exp3/mixin/LibraryStreamingReservationMixin`

It:

- captures Minecraft's device-derived channel-count input during `Library.init(...)`;
- derives the exact vanilla 1.21.1 original static/streaming reservation;
- raises the streaming reservation to at most 16 while subtracting the same delta from static;
- preserves the original combined reservation;
- fails loudly if the actual constructor arguments do not match the derived vanilla reservation shape;
- does not own raw OpenAL sources.

Exact dev-client audio-init smoke on both `.247` and `.248` logged:

```text
reportedChannelCount=255
originalStatic=247
originalStreaming=8
newStatic=239
newStreaming=16
combinedPreserved=true
targetStreaming=16
```

OpenAL initialized and Minecraft's sound engine started after the diagnostic. The CI uses OpenAL Soft `No Output`, so this proves the exact Mixin/application/calculation boundary but not audible 16-stream allocation on the user's actual USB audio device.

Automatic regression evidence also passed:

- exact build/package matrix;
- expected Mixin packaged and old `SpeakerPeripheralMixin` absent;
- no `alGenSources` / `alDeleteSources` / AL10 raw-source ownership in the reservation patch;
- M1 GenericSource development-server self-check;
- M2/M3 playback/capacity fixtures packaged;
- packaged-JAR dedicated-server startup on both target NeoForge versions.

Independent artifact inspection confirmed the matrix JARs are byte-identical and match the recorded SHA. The only CI client `ERROR` was the headless Linux narrator failing to load `flite`, unrelated to HighAudio/Mixin/OpenAL allocation.

### Next user-only evidence — Part A2 manual baseline

Use the exact automatic candidate above with SPR absent first. Only two capacity runs are necessary because Part A already located vanilla's boundary:

```text
/highaudio_exp3 capacity 8
wait for phase=final
/highaudio_exp3 capacity 16
wait for phase=final
```

Preserve `latest.log` and `debug.log`.

Success requires the real Windows/audio-device runtime to show:

```text
streaming reservation rebalance ... originalStreaming=8 ... newStreaming=16 ... combinedPreserved=true
capacity requested=16
captures=16
activeSounds=16
soundDebug=... + 16/16
```

Do **not** mark ADR-0009 Accepted merely from the automatic client-init result. If the SPR-off real client reaches 16/16 cleanly, perform a second short launch with exact SPR 1.5.1 for coexistence/reload checks before accepting the reservation policy.

See `docs/test-batches/TEST-BATCH-003.md` for exact acceptance criteria.

### Part B — synchronization remains undecided

Do not silently choose among the synchronization options before Part A2 is understood:

- **A — pure Minecraft/high-level scheduling:** cleanest lifecycle/compatibility, possibly looser skew and weaker renderer-position visibility;
- **B — narrow accessor/control of Minecraft-owned OpenAL source:** preserves Minecraft ownership while enabling precise offsets/vector start, but adds localized exact-version coupling and must prove no preparation leak;
- **C — independent raw OpenAL ownership:** maximum control but duplicates source/lifecycle/category/reload/world cleanup and bypasses the normal SPR-facing Minecraft channel path; fallback only if Minecraft-owned approaches fail.

## Still deferred

Do not start real media upload, codecs, ContentId/cache/transfer, server MediaSession, production sync groups, moving emitters, production SPR integration, URL/live streaming, or VS2 work until the relevant later milestone.

## Manual-test cadence

Keep implementation/source/CI checks frequent but batch user-only Minecraft observations. The Part A2 candidate has now passed the exact automatic gate and artifact inspection; the next justified client launch is the two-command SPR-off 8/16 capacity check above.
