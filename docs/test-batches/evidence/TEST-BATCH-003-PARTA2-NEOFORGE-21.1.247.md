# TEST-BATCH-003 Part A2 evidence — NeoForge 21.1.247

**Date:** 2026-09-07  
**Milestone:** MILESTONE-003 / EXP-003  
**Part:** A2 — Minecraft-owned streaming reservation rebalance  
**Result:** **PASS — SPR-off real-client 16-channel target proven**  
**NeoForge:** 21.1.247  
**Minecraft:** 1.21.1  
**Java:** Microsoft OpenJDK 21.0.7  
**CC:Tweaked:** 1.120.0  
**HighAudio candidate:** `82c195637de3987463c864c8f8493e9194410094`  
**Candidate CI:** `34103604455`  
**Candidate JAR SHA-256:** `f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7`  
**SPR:** absent from the mod list for this run

## Runtime device and rebalance

The real Windows client initialized OpenAL on:

```text
OpenAL Soft on Speakers (4- USB Audio Device)
```

HighAudio then logged the exact reservation transform:

```text
reportedChannelCount=255
originalStatic=247
originalStreaming=8
newStatic=239
newStreaming=16
combinedPreserved=true
rebalanceApplied=true
targetStreaming=16
```

This is direct runtime evidence that the conservative Part A2 Mixin applied on the user's actual audio device and preserved the combined Minecraft-owned reservation while changing the split from `247 + 8` to `239 + 16`.

It is **not** evidence that every OpenAL device can provide this reservation. The candidate deliberately leaves lower-capacity vanilla layouts unchanged when vanilla derives fewer than eight streaming slots.

## Capacity runs

The user exercised more than the minimum requested baseline:

| Run token | Requested | Unique captures | Early active sounds | Debug streaming side | Completion |
|---:|---:|---:|---:|---|---|
| 1 | 4 | 4 | 4 | `4/16` | natural final, 4/4 streams closed |
| 2 | 8 | 8 | 8 | `8/16` | natural final, 8/8 streams closed |
| 3 | 12 | 12 | 12 | `12/16` | natural final, 12/12 streams closed |
| 4 | 16 | 16 | 16 | `16/16` | natural final, 16/16 streams closed |
| 5 | 16 | 16 | 16 | `16/16` | natural final, 16/16 streams closed |
| 6 | 16 | 16 | 16 | `16/16` | explicit stop, then 16/16 streams closed |
| 7 | 16 | 16 | 16 | `16/16` | explicit stop, then 16/16 streams closed |
| 8 | 16 | 16 | 16 | `16/16` | natural final, 16/16 streams closed |

Every channel-capture event reported `eventThread=Sound engine`.

For natural 16-channel runs 4, 5, and 8, the t+5/t+20/t+40 snapshots retained:

```text
captures=16
activeSounds=16
closedStreams=0
soundDebug=... + 16/16
```

Run 6 also retained 16 active sounds through t+40 before the explicit stop. Run 7 was explicitly stopped earlier; its t+40 snapshot therefore correctly showed inactive/closed state before finalization.

## Cleanup and sequencing

All completed runs reached `phase=final` only after `inactiveTicks=10` and all allocated probe streams were closed.

The two explicit 16-channel stop cycles behaved cleanly:

```text
runToken=6 requested=16 captures=16
stop requested
phase=final activeSounds=0 closedStreams=16 inactiveTicks=10
```

and:

```text
runToken=7 requested=16 captures=16
stop requested
phase=final activeSounds=0 closedStreams=16 inactiveTicks=10
```

The controller also refused attempted new runs before the preceding probe had finalized. This confirms the strengthened sequencing guard remained active after the pool rebalance.

No stale-run capture was observed.

## Coexistence with ordinary Minecraft static sounds

The debug counter showed ordinary static-side activity during some 16-stream runs while HighAudio simultaneously occupied all 16 streaming slots, for example:

```text
Sounds: 1/239 + 16/16
Sounds: 2/239 + 16/16
```

This is useful coexistence evidence that the static pool remained available during the stress probe. It is not a comprehensive normal-SFX regression test.

## Errors and warnings

Across `latest.log` and `debug.log`:

- no `ERROR` entries were present;
- no `FATAL` entries were present;
- no HighAudio/OpenAL allocation exception was present;
- no HighAudio Mixin application failure was present.

Observed warnings were unrelated to the Part A2 audio path:

- YACL missing refmap warning;
- missing `org.jetbrains.annotations.ApiStatus$Internal` metadata class warning;
- offline user-type warning;
- vanilla pack-resource union-schema warnings;
- vanilla goat-horn missing-sound warnings;
- one shader sampler warning.

These are not attributed to HighAudio.

## Interpretation

**EXP-003 Part A2 SPR-off capacity baseline: PASS.**

The tested real NeoForge 21.1.247 runtime supports the project's **16 simultaneous HighAudio streaming-channel stress target** after the conservative Minecraft-owned reservation rebalance.

What this proves:

- the real device accepted the `239 static + 16 streaming` Minecraft reservation;
- 16 distinct Minecraft-owned streaming channels were allocated/started;
- 16 remained active through the measurement snapshots;
- the 16-channel result was repeatable across five runs;
- both natural completion and explicit stop cleaned all 16 streams;
- the combined source reservation was not increased by HighAudio.

What this does **not** yet prove:

- SPR 1.21.1-1.5.1 coexistence;
- sound-engine reload/F3+T reapplication on this real device after gameplay begins;
- final multi-source synchronization quality;
- universal 16-channel support on lower-capacity audio devices.

The next gate is the short exact SPR 1.21.1-1.5.1 coexistence/reload comparison, followed by EXP-003 Part B synchronization measurement.