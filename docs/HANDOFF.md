# HighAudio current handoff

**Status:** canonical chat/session handoff  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Prepared:** 2026-09-07  
**Current completed branch:** `milestone-002-exp-002-minecraft-audio`  
**Next work:** `MILESTONE-003` / `EXP-003` — capacity and synchronization proof

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

Automatic evidence passed on both NeoForge 21.1.247 and 21.1.248, including compilation, packaged EXP-002 classes/resources, EXP-001 regression checks, development-server startup, and clean installed packaged-JAR dedicated-server startup.

Real NeoForge 21.1.247 client evidence passed for:

- generated 48 kHz / 16-bit / mono PCM audibility;
- Minecraft-owned `SoundManager` playback;
- real `PlayStreamingSourceEvent` association with `com.mojang.blaze3d.audio.Channel`;
- positional attenuation;
- Records/Jukebox category volume;
- master volume;
- repeated natural completion;
- repeated explicit stop and inactive status;
- F3+T sound-engine reload plus successful replay;
- integrated singleplayer pause/resume without audible skip/play-ahead;
- disconnect cleanup with no stale sound after rejoin;
- no observed HighAudio-specific runtime exception/OpenAL error.

Manual evidence:

`docs/test-batches/evidence/TEST-BATCH-002-NEOFORGE-21.1.247.md`

### EXP-002 lessons that constrain EXP-003

1. `AudioStream`/generated-stream `bytesRead` is **not audible playback position**. Minecraft/OpenAL may have the full stream queued while the channel continues rendering it. Do not use stream consumption as synchronization time.
2. The Java `SoundEngine` object identity remained stable while F3+T reinitialized OpenAL. Use `SoundEngineLoadEvent` generation/lifecycle signals, not Java object identity, to distinguish renderer generations.
3. A sound with effective Minecraft volume zero may be rejected before a streaming channel is allocated, so `PlayStreamingSourceEvent` is not guaranteed for muted sounds. EXP-003 arming must not depend on ordinary category/master mute producing channels.

`ADR-0004` remains Proposed until EXP-003 resolves capacity and synchronization assumptions, exactly as its acceptance rule requires.

## MILESTONE-003 / EXP-003 — next work

Goal: measure the real Minecraft-owned source/channel capacity and select a measured local multi-source synchronization mechanism before production media/session architecture hardens around guesses.

### Part A — capacity

Create controlled 1 / 4 / 8 / 16 simultaneous HighAudio streaming sounds and record:

- successful channel captures;
- acquisition failure/voice stealing behavior;
- Minecraft sound/library debug information;
- streaming pool usage/max if accessible;
- CPU/memory observations where practical;
- coexistence with ordinary Minecraft sounds;
- SPR-off baseline first, SPR-on comparison later.

Do not infer HighAudio capacity from raw OpenAL hardware maximum.

### Part B — synchronization

Candidate family to test, not yet accepted:

```text
Minecraft creates/configures Channel normally
-> HighAudio captures Channel through NeoForge event
-> prepare/arm channels without audible leak
-> use high-level Minecraft control if measured sufficient
   OR the smallest source-id/private accessor if required
-> if justified, atomic OpenAL vector start for already-Minecraft-owned sources
-> measure actual first-sample/source-offset skew
```

Before choosing a low-level route, compare the meaningful options and their tradeoffs:

- pure Minecraft/high-level scheduled start — cleanest compatibility, potentially looser skew;
- narrow read/control accessor to Minecraft-owned OpenAL source — tighter measurement/control with localized version-sensitive coupling;
- independent raw OpenAL ownership — maximum control but high lifecycle/SPR/source-pool cost, fallback only if the first two fail.

Do not silently choose among those architectural boundaries without evidence.

### EXP-003 must measure

- 1/4/8/16 source capacity;
- escaped samples during prepare/arm;
- 2/4/8/16 source start skew;
- pause/resume skew;
- seek/re-arm behavior if the chosen candidate needs it;
- real source/sample offset reliability;
- available OpenAL timing/latency extensions rather than assuming them.

## Still deferred

Do not start real media upload, codecs, ContentId/cache/transfer, server MediaSession, production sync groups, moving emitters, production SPR integration, URL/live streaming, or VS2 work until the relevant later milestone.

## Manual-test cadence

Keep implementation/source/CI checks frequent but batch user-only Minecraft observations. MILESTONE-003 should have one consolidated capacity/sync test build rather than many tiny launch requests.
