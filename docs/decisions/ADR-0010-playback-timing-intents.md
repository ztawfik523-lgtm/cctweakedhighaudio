# ADR-0010 — Playback timing is intent-driven

**Status:** Proposed  
**Date:** 2026-09-07  
**Milestone:** MILESTONE-003 / EXP-003

## Context

EXP-003 Part B compared two local multi-source start mechanisms on the real NeoForge 21.1.247 Windows/OpenAL Soft runtime:

- normal Minecraft-owned/high-level start sequencing;
- a narrow OpenAL `alSourcePlayv` vector start over Minecraft-owned sources.

Both completed 2/4/8/16-source trials with zero measured `AL_SAMPLE_OFFSET` spread at the sampled start/t+2/t+5/t+10 points, including two complete 16-source sweeps. The vector path also proved that HighAudio can pause/rewind already-created Minecraft-owned sources and start the group with one synchronized OpenAL operation without creating/deleting its own sources.

A later re-evaluation identified an important gap in the high-level experiment: the diagnostic streams returned already-completed futures. Production media may involve asynchronous decode/cache/network readiness, so separate Minecraft `SoundInstance.getStream(...)` futures can become ready at different times. NeoForge starts each streaming channel after that individual future resolves. Therefore "normal start measured zero skew in the probe" is not enough to make normal start the only synchronization policy for future media.

Minecraft 1.21.1 uses LWJGL 3.3.3. That exact LWJGL release includes `AL_SOFT_source_start_delay` and `ALC_SOFT_device_clock` bindings, including `alSourcePlayAtTimevSOFT`, which allows a set of sources to be scheduled at one absolute audio-device-clock timestamp when the capabilities are present.

The product also has very different latency/synchronization needs for a button sound versus synchronized music. A single global timing policy would either add needless latency to ordinary sound effects or give synchronized media weaker guarantees than the renderer can provide.

## Decision

HighAudio playback timing will be modeled as **user/script intent**, not as one universal engine mode.

### Intent 1 — immediate

Meaning: play as soon as the individual sound is ready. This is the default and lowest-latency behavior.

Expected implementation boundary:

- normal Minecraft-owned `SoundManager`/`Channel` playback;
- no group readiness barrier;
- no artificial synchronization lead time;
- native CC:T speaker methods remain unchanged.

Typical use: one-shot SFX, button sounds, UI feedback, independent ambience, voice lines, and any playback where lowest latency matters more than coordination.

### Intent 2 — together

Meaning: all required local participants must be ready, then begin together as soon as possible.

Leading implementation:

- Minecraft still allocates/owns every `Channel` and OpenAL source;
- HighAudio holds/rewinds the already-created group after channel capture;
- once every required participant is ready, start the group with one core OpenAL `alSourcePlayv` operation;
- no fixed post-readiness delay is required.

This is the measured/proven EXP-003 vector mechanism. It is useful independently of scheduled playback, not merely as an error fallback.

Typical use: local multi-speaker playback that should start together but has no external target timestamp.

### Intent 3 — scheduled

Meaning: audible media sample zero should line up with a specific HighAudio/session timeline point rather than merely "as soon as possible".

Leading implementation candidate:

- share the same readiness/arming machinery as `together`;
- feature-detect `AL_SOFT_source_start_delay`, `AL_SOFT_source_latency`, and `ALC_SOFT_device_clock`;
- translate an internal HighAudio/session target into the current client's OpenAL device-clock domain;
- account for renderer preparation preroll internally so the public target refers to media sample zero, not hidden silence;
- account for measured output latency when mapping a future session/heard-at target to renderer/device-clock time;
- schedule all required Minecraft-owned sources with `alSourcePlayAtTimevSOFT`;
- use adaptive lead time only to ensure preparation and scheduling complete before the required source-start time.

There is **no fixed 100 ms tax** in the public semantics. The current diagnostic uses a 100 ms silent preroll because NeoForge exposes the Minecraft channel after Minecraft has already initiated source playback; the diagnostic therefore schedules the underlying source early enough that the end of that hidden preroll, not its beginning, corresponds to the intended media-zero renderer timestamp.

`ALC_SOFT_device_clock` also exposes device output latency. That distinction matters for later multi-client/session timing: a source can begin rendering at device-clock time `T` while the corresponding samples reach the physical output later. Local group synchronization only needs a shared renderer clock, but a public/session "heard at T" guarantee must compensate for the local device's output latency as part of the session-to-device mapping.

If precise scheduled playback is requested and the required capabilities are unavailable, HighAudio must not silently pretend timing guarantees were met. A future API may allow an explicit fallback policy, but approximation must be opt-in.

Typical use: synchronized media/session starts, scripted timeline events, and later server-authoritative/multi-client synchronization work.

## User-facing API rule

Lua/public APIs should expose semantic intent such as `play`, `startTogether`, and `startAt`/session equivalents. They should **not** expose "A/B/C", OpenAL source IDs, raw device-clock timestamps, output-latency values, or renderer-specific extension names.

The eventual exact Lua names are deferred until the media/session API milestone. This ADR defines semantics only; it does not authorize production media/session/network implementation during EXP-003.

A one-member `together` group should normally collapse to the immediate path because there is nothing local to synchronize against. A one-member `scheduled` request remains meaningful when it is synchronized to an external/session timeline.

## Why retain all three

`immediate`, `together`, and `scheduled` optimize different goals:

- `immediate`: minimum latency and maximum compatibility simplicity;
- `together`: strongest local group start with no unnecessary future timestamp;
- `scheduled`: strongest basis for precise timeline-driven playback when supported.

Making `scheduled` the default would unnecessarily delay ordinary SFX. Making `immediate` the only path would ignore realistic asynchronous-readiness failure cases. Keeping `together` is worthwhile because it uses core OpenAL, has already passed the real 16-source experiment, and naturally serves both as a direct product behavior and a lower-capability synchronization path.

## Important limits

This decision concerns **initial start semantics**. None of the three modes alone proves long-running streaming cannot drift after an underrun. Future production synchronization still needs buffer-health and renderer-position/timeline monitoring.

OpenAL device clocks are local to each client/device. A server cannot send one raw device-clock number to multiple machines. Future multiplayer synchronization must map a server/session timeline into each client's local device-clock domain and account for local output latency. The renderer-specific clock remains an internal implementation detail.

Minecraft/SPR compatibility remains based on retaining Minecraft-owned `Channel`/source lifecycle. `together`/`scheduled` perform narrow state/start operations on those existing sources rather than introducing a second OpenAL source manager.

## EXP-003 implementation boundary after re-audit

The diagnostic branch now separates three timestamps/positions which must not be conflated:

1. **source-start device clock** — when OpenAL begins advancing the source;
2. **media-zero renderer clock** — source start plus any hidden preparation preroll;
3. **estimated physical-output media-zero time** — media-zero renderer clock plus current device output latency.

The scheduled diagnostic also uses `AL_SAMPLE_OFFSET_CLOCK_SOFT`, which reports source offset and device clock atomically, and compensates sequential source-query timestamps before calculating group spread. This is stronger evidence than comparing separately queried sample offsets to a later device-clock read.

All such OpenAL operations remain on Minecraft's sound thread and operate only on Minecraft-owned sources. HighAudio still does not create/delete OpenAL sources, devices, or contexts.

## Validation required before acceptance

Before this ADR is Accepted:

1. exact `.247` and `.248` compilation/package/client-init must prove the scheduled-start bindings/accessors are valid;
2. the initialized client device must report the required timed-start/device-clock capabilities before scheduled mode is treated as available;
3. scheduled start must be measured in a consolidated EXP-003 real-client test only if that end-to-end renderer behavior remains architecture-blocking after automatic checks;
4. preparation must not leak audible content;
5. the measured scheduled source group must preserve tight relative synchronization around media-zero;
6. source-start, media-zero, and output-latency semantics must remain distinct in diagnostics and future session mapping;
7. unavailable scheduled capability must fail explicitly unless a future caller opts into a weaker fallback;
8. long-running drift remains a later session/sync concern and must not be falsely claimed solved by the start primitive.

## Alternatives rejected for this decision

- **One universal immediate mode:** insufficient semantic guarantee for future asynchronous multi-source media readiness.
- **One universal vector mode:** adds coordination to ordinary one-shot sounds that do not need it.
- **One universal scheduled mode:** adds needless timing machinery/lead time to ordinary SFX and depends on optional OpenAL Soft capabilities.
- **Independent HighAudio OpenAL engine:** still rejected as the primary path because it duplicates Minecraft's source/device/lifecycle responsibilities and worsens SPR coexistence.
