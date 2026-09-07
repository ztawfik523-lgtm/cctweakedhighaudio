# TEST-BATCH-002 — EXP-002 Minecraft-owned PCM playback gate

**Status:** READY FOR MANUAL GATE — strengthened automatic evidence PASS  
**Milestone:** MILESTONE-002  
**Experiment:** EXP-002  
**Branch:** `milestone-002-exp-002-minecraft-audio`

This batch is intentionally client-focused. Do not repeat Minecraft launches after small code changes; use the frozen candidate below and cover playback, channel capture, attenuation, stop, and sound-engine reload in one session.

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
- all EXP-002 client classes are packaged;
- `assets/cctweakedhighaudio/sounds.json` is packaged;
- no old HighAudio Mixin declaration/class/config is present;
- development-server startup remains healthy;
- the finished packaged JAR starts in a clean installed dedicated server, proving EXP-002 client classes do not contaminate dedicated-server loading.

## Prototype scope

EXP-002 contains only:

- a deterministic 8-second 48 kHz / 16-bit / mono generated PCM chirp;
- a custom positional `SoundInstance`;
- a custom `AudioStream`;
- normal `Minecraft.getInstance().getSoundManager().play(...)` ownership;
- `PlayStreamingSourceEvent` diagnostics for the real Minecraft `Channel`;
- `SoundEngineLoadEvent` diagnostics for sound-engine construction/reload;
- a client-only test command.

It deliberately does **not** contain codecs, file IO, uploads, content hashing, server sessions, networking, synchronization, direct raw OpenAL source creation, SPR integration code, or VS2 support.

## Test command

Use:

```text
/highaudio_exp2 play
/highaudio_exp2 stop
/highaudio_exp2 status
```

A successful play should produce a client log line containing:

```text
[EXP-002] PlayStreamingSourceEvent PASS
```

The event proves NeoForge handed HighAudio the real Minecraft-owned `Channel` associated with the generated `SoundInstance`.

## Baseline session — SPR absent

Use the normal target client stack with Sound Physics Remastered absent.

1. **Startup** — enter a world and confirm the client log contains `[EXP-002] SoundEngineLoadEvent observed`.
2. **Basic playback** — run `/highaudio_exp2 play`. Confirm the generated chirp is audible and the log records `PlayStreamingSourceEvent PASS`.
3. **Positional attenuation** — run `play` again and move well away from the point where the command was issued while the 8-second chirp is playing. Confirm the fixed sound position attenuates as distance increases.
4. **RECORDS category** — set the Records/Jukebox sound-category volume to 0, run `play`, and confirm the probe is inaudible while the channel event still occurs. Restore the slider and confirm audibility returns.
5. **Master volume** — repeat once with master volume at 0, then restore it.
6. **Explicit stop** — run `play`, then `/highaudio_exp2 stop` before the chirp finishes. After a moment run `/highaudio_exp2 status`; no probe should remain active.
7. **Natural end** — let one chirp finish normally. The log should eventually record `[EXP-002] playback inactive ...` and should not leave an active channel/sound.
8. **Sound-engine/resource reload** — run `play` and trigger F3+T while it is active. Confirm `SoundEngineLoadEvent` is observed, no old chirp remains stuck, then run `play` again and confirm a new `PlayStreamingSourceEvent PASS` occurs.
9. **World lifecycle** — leave the world, rejoin, and run `play` once more. Confirm playback/channel capture still works and no pre-disconnect probe remains.
10. **Output device** — if changing/reselecting the Minecraft output device is practical, do it once and repeat `play`. This is useful evidence but do not block the entire batch on hardware/OS UI limitations.

Preserve `latest.log` and `debug.log` from the session.

## SPR basic-observation subset

Only after the SPR-absent baseline is clean, a second short launch with exact SPR 1.21.1-1.5.1 may be used to answer one narrow EXP-002 question: does the same Minecraft-owned generated sound remain audible and receive the normal channel event with SPR installed?

This is **not** MILESTONE-010 compatibility certification. Reverb/occlusion/EFX correctness and performance remain deferred to the dedicated SPR milestone.

## GATE-002 pass conditions

GATE-002 passes when evidence establishes:

- arbitrary HighAudio-owned PCM is rendered through Minecraft's normal sound path;
- the sound is positional and obeys expected Minecraft master/category volume controls;
- `PlayStreamingSourceEvent` reliably associates the HighAudio sound with a Minecraft-owned `Channel`;
- stop/natural completion do not leave a persistent probe sound/channel;
- `SoundEngineLoadEvent` is observed and playback can be reconstructed after sound-engine/resource reload;
- world/disconnect lifecycle does not leave stale playback;
- basic playback requires no independent raw OpenAL source manager.

If a narrow private accessor is later needed for synchronization, that belongs to EXP-003 and does not invalidate a successful EXP-002 basic-playback result.
