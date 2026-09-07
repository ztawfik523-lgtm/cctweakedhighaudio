# TEST-BATCH-003 evidence — NeoForge 21.1.247

**Milestone:** MILESTONE-003  
**Experiment:** EXP-003 Part A  
**Runtime:** Minecraft 1.21.1 / Java 21.0.7 / NeoForge 21.1.247 / CC:Tweaked 1.120.0  
**HighAudio candidate:** `a500f3bee773e4e5558fe3473367927ece637f9b`  
**JAR SHA-256:** `553919083f8d998fd7d3b0e143f8e76ad3da7b78862ee84c93d886110be41055`  
**SPR:** absent  
**Result:** PART A BASELINE PASS — vanilla Minecraft streaming reservation measured at 8 channels on this runtime

## What was tested

The strengthened capacity probe was run in one client launch with requested counts:

```text
1, 1, 2, 4, 6, 8, 10, 12, 16
```

The duplicate `1` run is harmless and provides repeat baseline evidence. The probe refused attempts to start a new run before the prior run's 10-inactive-tick finalization completed, so asynchronous release did not contaminate later counts.

## Measured allocation

| Requested | Unique Minecraft streaming channel captures | Active sounds at early snapshots | Minecraft sound debug |
|---:|---:|---:|---|
| 1 | 1 | 1 | `... + 1/8` |
| 1 | 1 | 1 | `... + 1/8` |
| 2 | 2 | 2 | `... + 2/8` |
| 4 | 4 | 4 | `... + 4/8` |
| 6 | 6 | 6 | `... + 6/8` |
| 8 | 8 | 8 | `... + 8/8` |
| 10 | 8 | 8 | `... + 8/8` |
| 12 | 8 | 8 | `... + 8/8` |
| 16 | 8 | 8 | `... + 8/8` |

Every capture was delivered from the `Sound engine` thread. For requests above 8, only indices 0 through 7 obtained real channels; no ninth capture appeared.

## Lifecycle / cleanup evidence

For every run that allocated channels:

- `t+5ticks`, `t+20ticks`, and `t+40ticks` snapshots preserved the same capture/active count;
- the final snapshot was emitted only after `inactiveTicks=10`;
- `activeSounds=0` at final;
- `closedStreams` equaled the number of actually allocated channels;
- the Minecraft debug string returned to `... + 0/8` after completion.

The test also demonstrated that an early next-run command is refused until finalization instead of stopping/replacing the prior probe.

## Interpretation

This runtime does **not** support the project's 16-speaker stress target through vanilla Minecraft's ordinary streaming reservation. The measured boundary is:

```text
requested <= 8  -> all requested streams allocate
requested > 8   -> exactly 8 streams allocate
```

The `8` is a Minecraft streaming-channel policy/counter limit on this runtime, not evidence that the OpenAL device itself can create only eight sources. The same debug string simultaneously reported a much larger static-side reservation.

Do not interpret `heapDeltaBytes` as per-channel memory cost; observed values include ordinary JVM allocation and GC behavior.

## Architectural consequence

Before abandoning Minecraft-owned playback, EXP-003 will test a narrower candidate: keep Minecraft/OpenAL ownership and the existing total source budget, but rebalance the source reservation so at least 16 slots are available to the streaming side. The first prototype must **not** raise the total source count and must retain the existing `SoundInstance + AudioStream + SoundManager` path.

Part B synchronization remains unresolved until the capacity policy candidate is either proven or rejected.
