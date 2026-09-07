# ADR-0004 — Let Minecraft own the client audio source/channel

**Status:** Accepted — EXP-002 + EXP-003 passed the required architecture boundary  
**Date:** 2026-09-07  
**Search tags:** `ADR-0004`, `SoundInstance`, `AudioStream`, `Channel`, `OpenAL`, `SPR`

## Context

HighAudio needs arbitrary high-quality PCM, positional attenuation, normal Minecraft volume/lifecycle behavior, multi-source capacity, and precise-enough local synchronization without creating a second competing audio engine.

Two broad implementation paths were evaluated:

1. own raw OpenAL sources directly;
2. create sounds through Minecraft's normal `SoundManager`/`SoundEngine`, keep Minecraft ownership, and use narrow low-level access only where an explicitly requested timing behavior requires it.

EXP-002 proved the basic Minecraft-owned PCM/lifecycle path. EXP-003 then measured the real streaming limit, proved a Minecraft-owned reservation policy can meet the 16-source project stress target, and proved a narrow synchronized start over already-Minecraft-owned sources.

## Decision

HighAudio uses Minecraft-owned client audio sources/channels.

The normal path remains:

```text
HighAudio PCM
    -> HighAudio SoundInstance / AudioStream
    -> Minecraft SoundManager / SoundEngine
    -> Minecraft-owned Channel
    -> Minecraft-owned OpenAL source
```

Use official NeoForge sound events first:

```text
PlayStreamingSourceEvent -> associate a HighAudio sound with its Minecraft Channel
SoundEngineLoadEvent     -> observe renderer reconstruction/reload
```

Low-level access is allowed only as a **narrow timing/control layer over sources Minecraft already owns**. It does not authorize HighAudio to create a parallel OpenAL source/device/context manager.

Current justified private/OpenAL access is limited to:

- reading the OpenAL source id behind a Minecraft `Channel` when an explicit synchronization intent needs it;
- executing timing operations on Minecraft's sound thread;
- reading renderer/source timing information;
- reading Minecraft's current OpenAL device handle for optional device-clock scheduling diagnostics;
- changing Minecraft's own static/streaming reservation while preserving its combined source reservation (`ADR-0009`).

## EXP-002 evidence

Frozen EXP-002 candidate:

```text
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI run: 34088822441
JAR SHA-256:
515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Real NeoForge 21.1.247 client evidence established:

- generated 48 kHz / 16-bit / mono PCM is audible through Minecraft's sound path;
- position/attenuation and Records/Jukebox + master volume controls behave normally;
- `PlayStreamingSourceEvent` exposes the real Minecraft-owned `Channel`;
- explicit stop and natural completion cleanly stop/close playback;
- F3+T rebuilds the sound/OpenAL renderer and playback works afterward;
- integrated singleplayer pause/resume behaves naturally;
- disconnect cleans active playback and stale audio does not resurrect;
- no independent raw OpenAL manager is needed for basic playback.

EXP-002 also proved that `AudioStream` bytes consumed is not audible renderer position and that Java `SoundEngine` object identity is not a renderer-generation clock.

## EXP-003 evidence

### Capacity

The real client measured vanilla streaming capacity at exactly 8 simultaneous streaming channels on the tested runtime. Requests above 8 plateaued at `+ 8/8`.

The conservative Minecraft-owned reservation candidate then preserved the runtime's combined reservation while changing the measured layout from:

```text
247 static + 8 streaming = 255
```

to:

```text
239 static + 16 streaming = 255
```

Real NeoForge 21.1.247 testing obtained `16/16` Minecraft-owned streaming channels repeatedly, including explicit-stop runs, with clean stream closure and ordinary static sounds still allocating while the 16 streaming slots were occupied. See `ADR-0009` and `TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md`.

### Local start synchronization

One real-client comparison command exercised both ordinary Minecraft start sequencing and a narrow vector start over Minecraft-owned sources at 2, 4, 8, and 16 sources. The whole comparison was run twice.

Both modes measured zero relative `AL_SAMPLE_OFFSET` spread at all sampled checkpoints through 16 sources. The vector mode additionally proved:

- every source remained Minecraft-owned;
- pause/rewind preparation ran on Minecraft's sound thread;
- no pre-pause or post-rewind sample advance was observed in the exact probe;
- one core `alSourcePlayv` call started the prepared group without OpenAL errors;
- all streams cleaned up normally.

The high-level path remains the correct default for lowest-latency independent playback. The vector primitive remains justified for an explicit `together` intent because production streams may become ready at different times even though the synthetic comparison streams were immediately ready.

### Optional scheduled timing

EXP-003 also established automatically that the exact target stack can access the OpenAL Soft timed-start/device-clock capabilities needed for an optional future `scheduled` intent (`ADR-0010`). That optional capability is **not required for acceptance of this ADR or closure of GATE-003**. It is not the default playback path and does not justify delaying ordinary SFX.

## Why this is accepted now

The two risks that originally kept this ADR Proposed are resolved strongly enough for the architecture boundary:

- **capacity:** the project target of 16 Minecraft-owned streaming sources is proven on the real runtime with a conservative reservation rebalance;
- **local synchronized start:** a Minecraft-owned 16-source synchronized vector start is proven, while ordinary high-level playback also measured zero spread in the tested ready-stream case.

Therefore there is no evidence-based reason to introduce independent raw OpenAL ownership before the real media/session system exists.

## Sound Physics Remastered boundary

SPR compatibility is still important, but it has its own dedicated compatibility milestone (`MILESTONE-010`). Keeping SPR runtime testing as an acceptance prerequisite here would duplicate later testing and violate the project's minimum-manual-testing policy.

The accepted architecture is intentionally favorable to SPR because HighAudio continues to use Minecraft `Channel`/source ownership. Exact SPR 1.5.1 coexistence, acoustics, F3+T behavior, and 1/4/16-source performance remain explicit MILESTONE-010 work rather than being falsely marked proven here.

## Consequences

Positive:

- normal Minecraft master/category attenuation, positioning, and lifecycle;
- better compatibility surface with Minecraft sound mods than a parallel source engine;
- no duplicate OpenAL source/device/context ownership;
- narrow synchronization primitives can still use precise renderer timing when explicitly requested;
- the same basic playback architecture remains valid from one source through the proven 16-source target.

Negative:

- Minecraft source-pool policy is a real HighAudio constraint and requires the localized `ADR-0009` reservation adjustment to meet the 16-stream target on the measured runtime;
- a few exact-version private Minecraft/OpenAL accessors exist for advanced timing behavior;
- those accessors must remain localized and revalidated on future Minecraft/LWJGL versions.

## Revisit rule

Revisit or supersede this ADR if future real evidence shows that Minecraft ownership prevents required media/session semantics, causes an unsolved renderer lifecycle problem, or cannot coexist with the supported SPR target. Do not silently expand the narrow timing layer into independent OpenAL ownership.
