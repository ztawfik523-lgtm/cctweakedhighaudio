# ADR-0004 — Let Minecraft own the client audio source/channel

**Status:** Proposed — requires `EXP-002` and `EXP-003`  
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

## Why only Proposed

Source-level evidence proves the basic playback pattern, not all HighAudio requirements. Runtime prototypes still need to prove:

- exact 21.1.247/248 sound-event availability/behavior;
- reliable Channel association;
- cleanup and reconstruction through F3+T/device/world lifecycle;
- actual static/streaming pool capacity;
- whether a Minecraft-owned Channel can be safely prepared/paused/reset and then tightly group-started;
- whether SPR naturally sees/processes these sources.

## Alternatives considered

### Fully independent raw OpenAL source ownership

**Advantages:** maximum direct control over source ids, sample offsets, queues, vector play/pause, and custom lifetime.

**Costs:** HighAudio must own source allocation/deletion, sound-category/master-volume integration, F3+T/device reload, disconnect/world cleanup, Minecraft source-pool coexistence, and acoustic-mod visibility/EFX interaction.

Rejected as the default because those lifecycle costs are substantial. Keep as a hybrid fallback only if `EXP-003` demonstrates Minecraft ownership blocks required synchronization semantics.

### Pure high-level SoundManager with zero lower-level access

**Advantages:** minimal private/platform coupling.

**Costs:** may not provide enough control/measurement for tight group start, sample offset, or queued-stream correction.

Not rejected, but must be tested. If it proves sufficient, prefer it over adding accessors.

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

Accept only after `EXP-002` proves lifecycle/Channel capture and `EXP-003` proves or replaces the synchronization/capacity assumptions. If the experiments show a hybrid is required, supersede this ADR with the exact boundary rather than silently expanding raw OpenAL ownership.
