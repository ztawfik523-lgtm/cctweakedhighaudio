# HighAudio current state

**Status:** authoritative durable project status

**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248

**Last reviewed:** 2026-09-07

This document answers what exists now and what comes next. Detailed evidence, design, and sequencing remain in the linked canonical documents.

## Implemented today

MILESTONE-000 through MILESTONE-003 are complete and their gates passed. The MILESTONE-004 implementation is now present and awaiting its one audible manual gate:

- M0 established the reproducible exact-version Gradle build, packaging checks, and NeoForge `.247`/`.248` CI matrix.
- M1 added the targeted CC:T `GenericSource` integration and retained startup/runtime self-checks without replacing native speaker peripherals or methods.
- M2 added diagnostic arbitrary 48 kHz PCM playback through Minecraft-owned `SoundInstance`, `AudioStream`, and `Channel` lifecycle, including cleanup/reconstruction probes.
- M3 added retained capacity/timing diagnostics and the accepted conservative streaming-reservation rebalance. On eligible normal layouts it makes up to 16 streaming slots available by reducing the static reservation by the same amount; it does not increase the combined source budget.
- M4 adds production methods on normal placed CC:T speakers for bounded begin/write/finish/abort upload, SHA-256 content identity, a bounded in-memory server store, request-on-cache-miss client transfer, bounded client compressed caching, strict finite WAV PCM decode, Minecraft-owned positional playback, and authoritative stop.

The repository still contains experiment-era `exp2` and `exp3` package names. They hold validated diagnostics and regression mechanisms; the names do not make their evidence unresolved.

The M4 low-level Lua surface is `highAudioUploadBegin(size)`, `highAudioUploadWrite(uploadId, bytes)`, `highAudioUploadFinish(uploadId)`, `highAudioUploadAbort(uploadId)`, `highAudioPlay(contentId)`, and `highAudioStop()`. `tools/test-batch-004.lua` is the consolidated gate helper; a higher-level bundled user API remains deferred.

## Validated foundation

Exact-stack evidence validates these foundations for future product work:

- additive HighAudio methods can be exposed through a targeted `GenericSource` while preserving normal CC:T speaker behavior;
- decoded PCM can flow through Minecraft-owned playback and lifecycle handling;
- the accepted reservation policy repeatedly reached 16/16 Minecraft-owned streams on the measured runtime while preserving the total reservation and static-side availability;
- a narrow vector start is viable for already prepared Minecraft-owned sources through 16 participants;
- optional device-clock scheduled-start capabilities initialize on both targeted NeoForge versions.

The last item is capability/integration evidence, not end-to-end audible scheduled playback with a real session timeline.

## Decision status

Accepted decisions are indexed in [`decisions/README.md`](decisions/README.md). In particular:

- ADR-0004: Minecraft owns normal source/channel/device lifecycle; HighAudio does not run a parallel OpenAL engine.
- ADR-0009: the streaming reservation may be conservatively rebalanced while preserving the combined total.
- ADR-0001, ADR-0002, ADR-0005–ADR-0008 are also Accepted; ADR-0003 is Superseded.

ADR-0010 remains Proposed. Its `immediate`, `together`, and optional `scheduled` intent model is a design direction, not implemented product behavior.

## Current milestone gate

MILESTONE-004 is **implemented with automated validation complete; audible GATE-004 validation remains pending**. Unit tests cover content identity/store/cache bounds, upload completion/failure/expiry/disconnect cleanup, and WAV validation/PCM extraction. The development dedicated-server smoke confirms both M4 CC:T generated methods and lifecycle Mixins load while retaining the EXP-001 live registry check.

The remaining gate is one real-client run with a supported mono PCM WAV through one placed speaker, including authoritative stop. Do not claim audible playback until that run passes. The scope and exact procedure are in [`ROADMAP.md`](ROADMAP.md) and [`test-batches/TEST-BATCH-004.md`](test-batches/TEST-BATCH-004.md).

## Planned and deferred

M5–M10 cover broader authoritative controls/lifecycle, long-media policy, production synchronization, codec expansion, moving emitters, and exact SPR compatibility. M11 HTTP/live media is deferred. Pause/seek/loop/sync-group behavior, MP3, moving emitters, late-listener recovery, broad SPR work, and live media are not current capabilities.

## Historical evidence

[`PROTOTYPES.md`](PROTOTYPES.md) is the experiment ledger for both completed and unresolved experiments. Detailed retained procedures and results live under [`test-batches/`](test-batches/), while [`VERIFIED-FACTS.md`](VERIFIED-FACTS.md) records exact-version facts. These are evidence/history, not competing descriptions of current implementation status.

For the system design read [`ARCHITECTURE.md`](ARCHITECTURE.md); for milestone order read [`ROADMAP.md`](ROADMAP.md); for validation policy read [`TESTING.md`](TESTING.md); and for open risks read [`RISKS.md`](RISKS.md).
