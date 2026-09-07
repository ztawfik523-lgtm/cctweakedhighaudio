# TEST-BATCH-002 evidence — NeoForge 21.1.247

**Status:** PASS — GATE-002 client runtime evidence complete  
**Milestone:** MILESTONE-002  
**Experiment:** EXP-002  
**Date:** 2026-09-07  
**Minecraft:** 1.21.1  
**Java:** Microsoft OpenJDK 21.0.7  
**NeoForge:** 21.1.247  
**CC:Tweaked:** 1.120.0  
**HighAudio candidate:** `cctweakedhighaudio-0.1.0-dev-exp002.jar`  
**Frozen code commit:** `4e31bbd08cc8c4e314637d857098c02232f41ff4`  
**CI run:** `34088822441`  
**JAR SHA-256:** `515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913`

The manual baseline used the frozen EXP-002 candidate with Sound Physics Remastered absent. Automatic build/package/dedicated-server evidence for the same candidate already passed on both NeoForge 21.1.247 and 21.1.248.

## Session 1 — broad client playback/lifecycle coverage

Observed facts from the preserved client logs and in-game notes:

- `SoundEngineLoadEvent` was observed during initial sound-engine construction and again on resource/sound-engine reload.
- HighAudio generated deterministic 48 kHz / 16-bit / mono PCM and requested playback through Minecraft's normal `SoundManager` path.
- Eight `PlayStreamingSourceEvent PASS` captures were observed across the session; each exposed a real `com.mojang.blaze3d.audio.Channel` associated with `GeneratedPcmSound`.
- Normal completion repeatedly reached `channelStopped=true`, `streamClosed=true`, and `bytesRead=768000/768000`.
- Positional attenuation was audible; the tester recorded that moving roughly 16–19 blocks away caused the fixed-position chirp to become inaudible.
- Setting the Records/Jukebox category to zero made the HighAudio chirp inaudible.
- Setting master volume to zero made the HighAudio chirp inaudible.
- When effective volume was zero, Minecraft did not necessarily allocate/start a streaming channel, so a `PlayStreamingSourceEvent` is not guaranteed for a muted sound. This corrects the original TEST-BATCH-002 expectation that a channel event must still occur at zero effective volume.
- F3+T while a chirp was active stopped the old renderer-owned playback during reload, `SoundEngineLoadEvent` was observed, OpenAL was reinitialized, and subsequent playback captured a fresh Minecraft channel.
- Singleplayer pause behaved naturally: the tester repeatedly entered/exited the pause menu and reported that playback paused/resumed without skipping, playing ahead, or cutting out.
- Disconnect while playback was active cleaned up the sound; the log recorded `channelStopped=true`, `streamClosed=true`, and a partial stream byte count, and the sound did not resurrect after rejoining.
- No HighAudio-specific runtime exception or OpenAL error was observed.

Two diagnostic lessons are important for later synchronization work:

1. `GeneratedPcmStream.bytesRead()` measures stream/buffer consumption, not audible playback position. The stream can be fully read while the Minecraft channel is still actively rendering queued audio.
2. The Java `SoundEngine` object identity remained stable across sound-engine reloads even though OpenAL was reinitialized. The explicit `SoundEngineLoadEvent` generation counter is the useful reload-generation signal; object identity is not.

## Session 2 — explicit stop/status closure

A short follow-up session exercised the previously omitted explicit stop path three times.

Representative sequence:

```text
09:38:22.462 play requested
09:38:22.463 PlayStreamingSourceEvent PASS
09:38:24.271 stop requested channelCaptured=true
09:38:24.358 playback inactive channelStopped=true streamClosed=true
```

A second identical stop cycle occurred at 09:38:25–09:38:27, and a third at 09:38:46–09:38:48.

After stop, status explicitly reported:

```text
active=false
channelStopped=none
stream=none
last=finished:channelStopped=true,streamClosed=true
```

The tester also recorded `stopped properly` in chat during the run.

This closes the explicit authoritative-stop condition for GATE-002.

## Noise / non-HighAudio warnings

The logs still contain the previously observed YACL/Mixin startup warning:

```text
ClassNotFoundException: org.jetbrains.annotations.ApiStatus$Internal
```

It occurs during other-mod Mixin processing, does not prevent startup, and is not HighAudio-specific.

The normal missing goat-horn sound warnings also remain unrelated noise.

## GATE-002 assessment

PASS.

Evidence now establishes that:

- arbitrary HighAudio-generated PCM is rendered through Minecraft's normal sound path;
- the sound is positional and obeys Minecraft master/category volume controls;
- `PlayStreamingSourceEvent` reliably associates active HighAudio streaming playback with a Minecraft-owned `Channel`;
- explicit stop, natural completion, F3+T reload, and world disconnect do not leave a persistent HighAudio probe sound/channel;
- `SoundEngineLoadEvent` provides the renderer reconstruction signal needed after sound-engine reload;
- playback can be recreated successfully after reload;
- basic HighAudio PCM playback requires no independent raw OpenAL source manager.

Output-device reselection and the optional SPR observation were not required to close this gate. Full source capacity, precise renderer position, and multi-source synchronization remain EXP-003 / MILESTONE-003 work.
