# TEST-BATCH-002 — EXP-002 Minecraft-owned PCM playback gate

**Status:** PASS — GATE-002 satisfied  
**Milestone:** MILESTONE-002  
**Experiment:** EXP-002  
**Branch:** `milestone-002-exp-002-minecraft-audio`  
**Manual evidence:** `docs/test-batches/evidence/TEST-BATCH-002-NEOFORGE-21.1.247.md`

## Frozen candidate

```text
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI run: 34088822441
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256 on both matrix legs:
515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Automatic evidence confirms on both exact NeoForge targets:

- Java 21 / Minecraft 1.21.1 / exact CC:T 1.120.0 compilation;
- accepted EXP-001 GenericSource regression checks remain green;
- all EXP-002 client classes and sound resource are packaged;
- no old HighAudio Mixin declaration/class/config is present;
- development-server startup remains healthy;
- the finished packaged JAR starts in a clean installed dedicated server, proving EXP-002 client classes do not contaminate dedicated-server loading.

## Prototype scope

EXP-002 intentionally contains only:

- a deterministic 8-second 48 kHz / 16-bit / mono generated PCM chirp;
- a custom positional `SoundInstance`;
- a custom `AudioStream`;
- normal `Minecraft.getInstance().getSoundManager().play(...)` ownership;
- `PlayStreamingSourceEvent` diagnostics for the real Minecraft `Channel`;
- `SoundEngineLoadEvent` diagnostics for sound-engine construction/reload;
- a client-only `/highaudio_exp2 play|stop|status` command.

It contains no codecs, file IO, uploads, content hashing, server sessions, networking, synchronization, direct raw OpenAL source manager, SPR integration code, or VS2 support.

## Manual result

The NeoForge 21.1.247 SPR-absent baseline passed.

Observed coverage includes:

- generated PCM audibility through Minecraft;
- real `PlayStreamingSourceEvent` channel capture;
- positional attenuation;
- Records/Jukebox category-volume behavior;
- master-volume behavior;
- repeated clean natural completion;
- repeated explicit `play -> stop -> status` closure;
- F3+T sound-engine reload and successful replay afterward;
- integrated singleplayer pause/resume behavior without audible skip/play-ahead;
- disconnect cleanup with no stale playback after rejoin;
- no observed HighAudio-specific runtime exception/OpenAL error.

The explicit-stop follow-up produced three clean stop cycles. Representative result:

```text
play requested
PlayStreamingSourceEvent PASS
stop requested channelCaptured=true
playback inactive channelStopped=true streamClosed=true
status: active=false
```

Two important diagnostic corrections were learned during the batch:

1. A sound whose effective Minecraft volume is zero may be rejected before a streaming channel is allocated, so `PlayStreamingSourceEvent` is not guaranteed for a muted sound. The original test wording requiring an event while muted was too strict.
2. `AudioStream` bytes-read is not audible playback position. Minecraft/OpenAL can have the full stream queued while the channel continues rendering. EXP-003 must use renderer/source timing rather than stream-consumption bytes as its synchronization clock.

The Java `SoundEngine` object identity also remained stable across reload while OpenAL was reinitialized, so reload generation must come from lifecycle events/counters rather than Java object identity.

## GATE-002 result

**PASS.** Evidence establishes:

- arbitrary HighAudio-owned PCM is rendered through Minecraft's normal sound path;
- the sound is positional and obeys expected Minecraft master/category volume controls;
- `PlayStreamingSourceEvent` associates active HighAudio playback with a Minecraft-owned `Channel`;
- explicit stop, natural completion, F3+T reload, and world leave do not leave a persistent probe sound/channel;
- `SoundEngineLoadEvent` is observed and playback can be reconstructed after reload;
- basic playback requires no independent raw OpenAL source manager.

Output-device reselection and the optional SPR observation were not required to close GATE-002. Full SPR certification remains MILESTONE-010. Capacity, precise renderer position, and multi-source synchronization are now MILESTONE-003 / EXP-003.
