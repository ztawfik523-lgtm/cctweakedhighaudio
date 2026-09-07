# ADR-0004 — Let Minecraft own the client audio source/channel

**Status:** Proposed — `EXP-002` PASS; requires `EXP-003` before acceptance  
**Date:** 2026-09-07  
**Search tags:** `ADR-0004`, `SoundInstance`, `AudioStream`, `Channel`, `OpenAL`, `SPR`

## Context

HighAudio needs high-quality arbitrary PCM, positional attenuation, pause/resume/seek, tight local multi-speaker start, lifecycle recovery, and future Sound Physics Remastered compatibility.

Two broad implementation paths exist:

1. own raw OpenAL sources directly;
2. create sounds through Minecraft's normal `SoundManager`/`SoundEngine` and use low-level OpenAL access only where required.

The exact CC:T 1.120.0 client already proves that custom speaker PCM can be rendered by a custom `SoundInstance` plus `AudioStream` through Minecraft-owned `Channel`s. NeoForge also exposes sound-source and sound-engine lifecycle events which may provide the interception points HighAudio needs without broad SoundEngine Mixins.

## Proposed decision

HighAudio should create custom positional `SoundInstance`/`AudioStream` objects and let Minecraft own the resulting `Channel` and normal source lifecycle.

Use official NeoForge sound events first:

```text
PlayStreamingSourceEvent -> associate HighAudio SoundInstance with Minecraft Channel
SoundEngineLoadEvent     -> observe/rebuild after sound-engine/device reload
```

Add only the smallest accessor/interception needed for precision synchronization or renderer-position measurement if `EXP-003` proves public/event APIs insufficient. A likely example is read-only/narrow access to the underlying OpenAL source id.

Do not build an independent parallel OpenAL source manager by default.

## EXP-002 result — PASS

MILESTONE-002 / EXP-002 validated the basic Minecraft-owned playback/lifecycle half of this ADR on the frozen candidate:

```text
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI run: 34088822441
JAR SHA-256:
515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Automatic compilation/package/dedicated-server evidence passed on NeoForge 21.1.247 and 21.1.248. Real NeoForge 21.1.247 client evidence established:

- arbitrary generated 48 kHz / 16-bit / mono PCM is audible through Minecraft's sound path;
- positional attenuation and Minecraft Records/Jukebox + master volume controls behave normally;
- `PlayStreamingSourceEvent` exposes the real Minecraft-owned `Channel` for active HighAudio playback;
- explicit stop and natural completion cleanly stop the channel and close the stream;
- F3+T reinitializes OpenAL, fires `SoundEngineLoadEvent`, and later playback can create a fresh channel;
- disconnect/world leave cleans up active playback and it does not resurrect on rejoin;
- no independent raw OpenAL source manager was required.

EXP-002 also proved two cautions for EXP-003:

- `AudioStream` bytes consumed/queued is not audible renderer position;
- Java `SoundEngine` object identity is not a renderer-generation identifier across reloads.

A muted sound may also be rejected before channel allocation, so future arming logic cannot assume ordinary category/master mute will still create a source.

## Why still Proposed

EXP-002 proved lifecycle/Channel capture but not all HighAudio requirements. `EXP-003` still needs to prove or replace:

- actual static/streaming pool capacity;
- whether a Minecraft-owned Channel can be safely prepared/paused/reset and tightly group-started;
- renderer/source offset reliability and timing extensions;
- whether precise synchronization can remain pure high-level Minecraft or needs one narrow source-id/private accessor;
- whether atomic vector start is reliable enough if used;
- SPR behavior under multi-source load remains later compatibility evidence.

## Alternatives considered

### Fully independent raw OpenAL source ownership

**Advantages:** maximum direct control over source ids, sample offsets, queues, vector play/pause, and custom lifetime.

**Costs:** HighAudio must own source allocation/deletion, sound-category/master-volume integration, F3+T/device reload, disconnect/world cleanup, Minecraft source-pool coexistence, and acoustic-mod visibility/EFX interaction.

Rejected as the default because those lifecycle costs are substantial. Keep as a hybrid fallback only if `EXP-003` demonstrates Minecraft ownership blocks required synchronization semantics.

### Pure high-level SoundManager with zero lower-level access

**Advantages:** minimal private/platform coupling.

**Costs:** may not provide enough control/measurement for tight group start, sample offset, or queued-stream correction.

Not rejected. `EXP-003` should measure it first where practical. If it is sufficient, prefer it over adding accessors.

## Consequences if accepted

Positive:

- native Minecraft master/category attenuation and source lifecycle;
- better chance of natural compatibility with SPR and other sound-engine mods;
- F3+T/device reload can be treated as renderer reconstruction rather than foreign OpenAL cleanup;
- leverages the same architectural pattern CC:T itself uses for custom speaker PCM.

Negative:

- Minecraft streaming/static pool limits become real HighAudio constraints;
- some exact synchronization functions may need narrow private/OpenAL access;
- SoundEngine/Channel internals remain version-sensitive where accessed.

## Acceptance rule

Accept only after `EXP-002` proves lifecycle/Channel capture **and** `EXP-003` proves or replaces the synchronization/capacity assumptions. If the experiments show a hybrid is required, supersede this ADR with the exact boundary rather than silently expanding raw OpenAL ownership.
