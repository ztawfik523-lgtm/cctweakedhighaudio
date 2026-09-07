# HighAudio current handoff

**Status:** canonical chat/session handoff  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Prepared:** 2026-09-07  
**Current branch:** `milestone-003-exp-003-capacity-sync`  
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

Goal: measure the real Minecraft-owned streaming capacity through the project's 16-source stress target, then select a measured local multi-source synchronization mechanism before production media/session architecture hardens around guesses.

### Part A — re-audited capacity probe

The first automatically green candidate was **superseded before manual testing** after re-audit found measurement-quality issues:

```text
old code commit: abe6063f46077fd74c0a83d05660cf07e0f3a33c
old CI run:     34092379023
old JAR SHA:
3e4b487221a39b13cfe5fc2382c6317c2f4ec60e38b8342fe0fffa3f38991b15
```

Do not use that artifact for manual evidence.

Frozen strengthened Part A candidate:

```text
code commit:       a500f3bee773e4e5558fe3473367927ece637f9b
CI run:            34095196833
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256:
553919083f8d998fd7d3b0e143f8e76ad3da7b78862ee84c93d886110be41055
```

Both exact matrix legs passed compilation, strengthened M3 package checks, accepted EXP-001 development-server regression smoke, and clean finished packaged-JAR dedicated-server startup. Both produced byte-identical HighAudio JARs.

Artifact inspection confirmed:

- `Exp3CapacityStream`, `Exp3CapacitySound`, `Exp3CapacityController`, client event hooks are present;
- no HighAudio Mixin residue;
- no packaged `dan200/` CC:T classes;
- no raw LWJGL OpenAL/source-control symbols in EXP-003 classes;
- no `Channel.stopped()` call in EXP-003 classes.

Re-audit changes that make the measurement acceptable:

- the streaming resource/event path was checked: a unique `PlayStreamingSourceEvent` occurs only after Minecraft has attached the stream to a real channel and called `channel.play()`;
- baseline counts are 1/4/8/16, but any 1..16 count is accepted for same-session threshold refinement;
- `16/16` means **at least 16 under tested conditions**, not an absolute Minecraft/OpenAL maximum;
- a new run is refused until the previous run has been inactive for 10 consecutive client ticks and logs `phase=final`;
- all streams share one immutable prewarmed PCM backing array while keeping independent cursor/close state;
- each run/sound carries a run token to reject stale events;
- sound-thread `PlayStreamingSourceEvent` only enqueues an immutable diagnostic record into a `ConcurrentLinkedQueue`;
- render-thread code owns ordinary counters/maps and uses `SoundManager.isActive`, stream closure, and `SoundManager.getDebugString()`;
- no cross-thread `Channel.stopped()` polling remains;
- captured channel identity is diagnostic only;
- whole-probe heap delta is rough end-to-end evidence, not per-channel memory cost.

Manual Part A runtime evidence is still NOT RUN. Use `docs/test-batches/TEST-BATCH-003.md` and the frozen SHA above.

### Part B — synchronization remains undecided

Do not silently choose among the meaningful architectural options before Part A evidence is understood:

- **A — pure Minecraft/high-level scheduling:** cleanest lifecycle/compatibility, possibly looser skew and weaker renderer-position visibility;
- **B — narrow accessor/control of Minecraft-owned OpenAL source:** preserves Minecraft ownership while enabling precise offsets/vector start, but adds localized exact-version coupling and must prove no preparation leak;
- **C — independent raw OpenAL ownership:** maximum control but duplicates source/lifecycle/category/reload/world cleanup and has the highest SPR/source-pool cost; fallback only if A and B fail.

EXP-003 Part B must measure escaped preparation audio, 2/4/8/16 start skew, pause/resume skew, renderer/sample offset reliability, and available timing/latency extensions rather than assuming them.

## Still deferred

Do not start real media upload, codecs, ContentId/cache/transfer, server MediaSession, production sync groups, moving emitters, production SPR integration, URL/live streaming, or VS2 work until the relevant later milestone.

## Manual-test cadence

Keep implementation/source/CI checks frequent but batch user-only Minecraft observations. MILESTONE-003 Part A should use the single consolidated client launch described in `TEST-BATCH-003`; do not request repeated launches for small patches.
