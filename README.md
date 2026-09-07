# CC:Tweaked HighAudio

High-quality, controllable, synchronized media playback through normal CC:Tweaked speakers.

> **Status:** bootstrap/prototype phase. The NeoForge/CC:T build scaffold exists, but no HighAudio speaker/media feature is implemented yet.

## Fixed target

- Minecraft **1.21.1**
- Java **21**
- CC:Tweaked **1.120.0**, exact source tag [`v1.21.1-1.120.0`](https://github.com/cc-tweaked/CC-Tweaked/tree/v1.21.1-1.120.0)
- NeoForge **21.1.247** as the initial compile target; CI also builds **21.1.248**
- Future Sound Physics Remastered compatibility target: **1.21.1-1.5.1**

Do **not** silently substitute CC:Tweaked 1.120.2/current `main` or a different Minecraft/NeoForge line when making architecture claims.

## Product direction

For v1, HighAudio is a **single NeoForge mod with CC:Tweaked required**. The user-facing device is the existing `computercraft:speaker`; we are not creating a replacement computer ecosystem or a general standalone audio framework.

Internally, CC:T-specific implementation coupling must be kept in a narrow integration layer. Content storage, sessions, networking, decoding, caching, synchronization, and client playback belong to HighAudio-owned systems.

This is an architectural boundary, not permission to assume a particular hook. The current leading speaker hook (an additive Mixin into `SpeakerPeripheral`) remains **proposed until EXP-001 passes**.

## Read this before coding

Start with [`docs/README.md`](docs/README.md).

The canonical order is:

1. [`docs/VERIFIED-FACTS.md`](docs/VERIFIED-FACTS.md) — facts proven from exact source/docs.
2. [`docs/decisions/`](docs/decisions/) — architecturally significant decisions and their status.
3. [`docs/PROTOTYPES.md`](docs/PROTOTYPES.md) — experiments required before assumptions become decisions.
4. [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — current system model, only as strong as the facts/ADRs it links to.
5. [`docs/ROADMAP.md`](docs/ROADMAP.md) — proof-first implementation order and gates.
6. [`docs/TESTING.md`](docs/TESTING.md) — automatic-vs-manual test cadence and batched runtime-test policy.
7. [`docs/RISKS.md`](docs/RISKS.md) and [`docs/SOURCES.md`](docs/SOURCES.md) — known risks and source/provenance ledger.

**Do not code from old Deep Research prose alone.** If research text conflicts with a verified fact, an accepted ADR, or a completed prototype result, the fact/ADR/result wins.

## Build scaffold

The bootstrap uses the official NeoForge 1.21.1 NeoGradle/userdev style and CC:T's published Maven artifacts.

Current baseline:

```text
Minecraft 1.21.1
Java 21
NeoForge 21.1.247
CC:Tweaked 1.120.0
```

Local builds use the committed Gradle **9.2.1** wrapper, so a separate Gradle installation is not required:

```text
./gradlew build
```

On Windows:

```text
gradlew.bat build
```

GitHub Actions also builds through the committed wrapper against both:

```text
NeoForge 21.1.247
NeoForge 21.1.248
```

CI additionally inspects the finished JAR and verifies that:

- `META-INF/neoforge.mods.toml` exists and contains no unexpanded `${...}` placeholders;
- the packaged mod id is `cctweakedhighaudio`;
- CC:Tweaked is required at exactly `1.120.0`;
- Minecraft is required at exactly `1.21.1`;
- the declared NeoForge compatibility range is `[21.1.247,21.2)`;
- `dev/ztawfik/cctweakedhighaudio/HighAudio.class` is actually packaged.

### MILESTONE-000 re-audit

The bootstrap was rechecked against the exact pinned CC:T 1.120.0 release and the official NeoForge 1.21.1 NeoGradle MDK. The stack pins, CC:T Maven coordinates, Java/Parchment settings, and dependency metadata structure were confirmed. The re-audit then strengthened reproducibility by adding the standard Gradle wrapper and strengthened CI by validating the packaged artifact instead of treating compilation alone as sufficient evidence.

MILESTONE-000 does not require a user-run Minecraft session; build/package compatibility is checked automatically. Manual Minecraft tests begin only when a runtime question actually needs them and are batched according to [`docs/TESTING.md`](docs/TESTING.md).

## Documentation status vocabulary

Search these exact tags throughout the repository:

- `[VERIFIED]` — directly supported by the pinned source/version or completed runtime evidence.
- `[INFERENCE]` — strongly suggested but not directly guaranteed.
- `[ACCEPTED]` — architecture decision currently in force.
- `[PROPOSED]` — leading design that must still pass a gate/prototype.
- `[EXPERIMENT]` — unresolved runtime behavior; do not treat as fact.
- `[DEFERRED]` — intentionally outside the current milestone.

Stable IDs are used so future research/code reviews can search directly for a claim:

- `FACT-*` verified facts
- `ADR-*` architecture decisions
- `EXP-*` experiments
- `RISK-*` risks
- `MILESTONE-*` roadmap stages
- `TEST-BATCH-*` consolidated user-run runtime sessions

## Current high-level architecture

```text
CC:Tweaked 1.120.0 normal speaker
        |
        | proposed additive integration (EXP-001)
        v
HighAudio CC:T adapter
        |
        v
HighAudio server
  ContentStore / UploadManager
  EmitterRegistry / SessionManager / MediaClock
  AudienceManager / TransferScheduler / SyncGroupManager
        |
        | NeoForge protocol
        v
HighAudio client
  SessionMirror / CompressedCache / Decoder workers / PCM cache or ring
  HighAudio SoundInstance + AudioStream
        |
        v
Minecraft SoundEngine-owned Channel
  NeoForge sound-source events first
  minimal OpenAL accessor/control only if experiments prove necessary
        |
        v
OpenAL / future Sound Physics integration
```

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the authoritative version.

## First implementation rule

The first feature code is **not** the full media system. The first three milestones after bootstrap are probes which can invalidate key assumptions:

- `EXP-001`: prove the CC:T speaker augmentation path without breaking native speaker behavior.
- `EXP-002`: prove Minecraft-owned custom streaming audio and lifecycle recovery.
- `EXP-003`: measure actual streaming-source capacity and prove/replace the proposed tight-sync mechanism.

Only after those gates pass do we build content upload, sessions, codecs, caching, and production networking.

## Provisional bootstrap identifiers and license metadata

The scaffold currently needs concrete identifiers in order to compile:

```text
mod id: cctweakedhighaudio
Java package: dev.ztawfik.cctweakedhighaudio
metadata license: All Rights Reserved
```

These are **bootstrap values, not locked architecture/product decisions**. Before public release, the final project name/mod id/package/license must be reviewed together with the provenance and dependency constraints in [`docs/SOURCES.md`](docs/SOURCES.md).
