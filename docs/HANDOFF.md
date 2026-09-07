# HighAudio current handoff

**Status:** canonical chat/session handoff  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Prepared:** 2026-09-07  
**Current branch:** `milestone-002-exp-002-minecraft-audio`  
**Next gate:** `MILESTONE-002` / `EXP-002` / `TEST-BATCH-002`

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

Frozen EXP-001 candidate evidence:

```text
code/evidence commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI run:              34082746562
JAR SHA-256:
0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Automatic evidence passed on both NeoForge 21.1.247 and 21.1.248, including exact CC:T method generation, speaker-only targeting, live `ServerContext` registration, development-server startup, and installed packaged-JAR dedicated-server startup.

Real NeoForge 21.1.247 client evidence passed for:

- direct block speaker;
- wired remote speaker;
- native `playNote`, `playSound`, `playAudio`, `stop`;
- turtle speaker;
- real pocket speaker;
- recreated/reconstructed block/turtle speaker peripheral instances;
- deterministic lifecycle reconstruction after disabling spawn-chunk retention;
- no observed HighAudio-specific runtime exception.

A second 21.1.248 gameplay repetition was explicitly waived after re-audit. The exact candidate already passed both server/runtime compatibility paths on .248, and NeoForge's official 21.1.248 changelog contains only a `SolidBucketItem#getPlaceSound` backport after .247, unrelated to GenericSource/peripheral dispatch.

`ADR-0003` remains superseded fallback history; `ADR-0008` is Accepted.

## MILESTONE-002 / EXP-002 — current work

Goal: prove arbitrary HighAudio-owned PCM can be rendered through Minecraft's own sound lifecycle before building codecs, uploads, sessions, or synchronization.

Exact source recheck established:

- NeoForge 21.1.248 has `PlayStreamingSourceEvent`, exposing the actual `SoundInstance` and Minecraft-owned `Channel` on the main client event bus;
- NeoForge 21.1.248 has `SoundEngineLoadEvent`, an `IModBusEvent` fired when the sound engine is constructed/reloaded;
- current FML automatically routes `@EventBusSubscriber` methods for `IModBusEvent` to the mod bus and other events to the game bus;
- exact CC:T 1.120.0 already uses a custom `SoundInstance` + `AudioStream` plus `PlayStreamingSourceEvent` to associate its PCM stream with Minecraft's `Channel`;
- NeoForge's own 1.21.1 client test contains the same custom-AudioStream pattern.

Current EXP-002 implementation intentionally contains only:

- deterministic 8-second 48 kHz / 16-bit / mono generated PCM chirp;
- custom positional `GeneratedPcmSound`;
- custom `GeneratedPcmStream`;
- normal `SoundManager.play(...)` ownership;
- `PlayStreamingSourceEvent` channel-capture diagnostics;
- `SoundEngineLoadEvent` reload diagnostics;
- client command `/highaudio_exp2 play|stop|status`;
- `assets/cctweakedhighaudio/sounds.json` placeholder using CC:T's guaranteed empty streaming resource.

There is no direct raw OpenAL source creation in EXP-002.

## TEST-BATCH-002

Use `docs/test-batches/TEST-BATCH-002.md` only after the final branch head is green on both NeoForge versions.

The single baseline client session should cover:

- generated PCM audibility;
- positional attenuation;
- Records/Jukebox category volume;
- master volume;
- explicit stop;
- natural completion;
- `PlayStreamingSourceEvent` channel capture;
- F3+T/sound-engine reload and replay;
- world leave/rejoin;
- output-device reload if practical.

SPR 1.21.1-1.5.1 gets only a narrow follow-up observation after the SPR-absent baseline; full SPR correctness remains MILESTONE-010.

## GATE-002

Pass when evidence establishes:

- HighAudio PCM is rendered through Minecraft's normal sound path;
- position/volume controls behave normally;
- the HighAudio `SoundInstance` can be associated with Minecraft's real `Channel` through official NeoForge events;
- stop/natural completion leave no persistent probe sound/channel;
- sound-engine reload is observable and playback can be reconstructed afterward;
- world lifecycle leaves no stale playback;
- basic playback needs no independent raw OpenAL manager.

A narrow OpenAL/source-id accessor for precise synchronization, if later required, belongs to EXP-003 rather than EXP-002.

## Non-goals until GATE-002 passes

Do not start:

- real media-file upload;
- WAV/Ogg/MP3 codec integration;
- ContentId/cache/transfer protocol;
- server MediaSession implementation;
- multi-speaker synchronization;
- direct OpenAL source ownership;
- production SPR integration;
- URL/live streaming;
- Valkyrien Skies support.

## Manual-test cadence

Do not request Minecraft launches after small patches. Keep source/CI checks frequent and accumulate user-only observations into the consolidated milestone test batch.
