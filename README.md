# CC:Tweaked HighAudio

High-quality, controllable, synchronized media playback through normal CC:Tweaked speakers.

> **Status:** architecture/prototype phase. There is intentionally no production implementation yet.

## Fixed target

- Minecraft **1.21.1**
- Java **21**
- CC:Tweaked **1.120.0**, exact source tag [`v1.21.1-1.120.0`](https://github.com/cc-tweaked/CC-Tweaked/tree/v1.21.1-1.120.0)
- NeoForge **21.1.247** as the initial compile target; **21.1.247 and 21.1.248** must both be runtime-tested before claiming support
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
6. [`docs/RISKS.md`](docs/RISKS.md) and [`docs/SOURCES.md`](docs/SOURCES.md) — known risks and source/provenance ledger.

**Do not code from old Deep Research prose alone.** If research text conflicts with a verified fact, an accepted ADR, or a completed prototype result, the fact/ADR/result wins.

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

The first code is **not** the full media system. The first three milestones are probes which can invalidate key assumptions:

- `EXP-001`: prove the CC:T speaker augmentation path without breaking native speaker behavior.
- `EXP-002`: prove Minecraft-owned custom streaming audio and lifecycle recovery.
- `EXP-003`: measure actual streaming-source capacity and prove/replace the proposed tight-sync mechanism.

Only after those gates pass do we build content upload, sessions, codecs, caching, and production networking.

## Name, mod id, package, and license

`HighAudio` is currently a working product name. The final mod id, Java package root, and project license are deliberately **not locked yet**. Third-party licensing/provenance constraints are tracked in [`docs/SOURCES.md`](docs/SOURCES.md).
