# TEST-BATCH-003 Part B — synchronization comparison — NeoForge 21.1.247

**Status:** PASS — both Minecraft-owned candidates measured zero intra-group sample-offset spread through 16 sources; architecture choice pending

## Exact runtime

- Minecraft 1.21.1
- Java 21.0.7
- CC:Tweaked 1.120.0
- NeoForge 21.1.247
- SPR absent
- OpenAL Soft device: `Speakers (4- USB Audio Device)`
- HighAudio sync-comparison branch: `milestone-003-exp-003-sync-compare`
- code candidate: `a5d8c6f766fa4d810d9a1b77a3bbf82cb8f91efa`
- CI run: `34107912428`
- JAR SHA-256: `54007a7adf3bb862732962c90139206fc051b8007474a946c2a58068331da59b`

The reservation rebalance remained active on the real client:

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

## Procedure

One command was used:

```text
/highaudio_exp3 sync_compare
```

The harness automatically compared two Minecraft-owned modes at 2, 4, 8, and 16 sources:

1. `high-level`: ordinary Minecraft `SoundManager.play(...)` scheduling with no direct OpenAL start control.
2. `vector`: capture already Minecraft-owned `Channel` source ids, immediately pause/rewind them on the sound thread during a silent preroll, then use one OpenAL vector-play call to start the group together.

For each trial the harness sampled `AL_SAMPLE_OFFSET` on the sound thread at start, t+2 ticks, t+5 ticks, and t+10 ticks. The recorded comparison metric is max `(maxOffset - minOffset)` across the post-start checkpoints. Stream byte consumption was not used as audible position.

## Runtime result

The user ran the full comparison twice in the same client session. Both complete sweeps passed.

### Sweep 1

| Mode | Sources | Captures | Max measured spread | Vector error | Closed streams |
|---|---:|---:|---:|---:|---:|
| high-level | 2 | 2/2 | 0 samples / 0.0 ms | n/a | 2 |
| vector | 2 | 2/2 | 0 samples / 0.0 ms | 0 | 2 |
| high-level | 4 | 4/4 | 0 samples / 0.0 ms | n/a | 4 |
| vector | 4 | 4/4 | 0 samples / 0.0 ms | 0 | 4 |
| high-level | 8 | 8/8 | 0 samples / 0.0 ms | n/a | 8 |
| vector | 8 | 8/8 | 0 samples / 0.0 ms | 0 | 8 |
| high-level | 16 | 16/16 | 0 samples / 0.0 ms | n/a | 16 |
| vector | 16 | 16/16 | 0 samples / 0.0 ms | 0 | 16 |

Sweep summary:

```text
completedTrials=8/8
highLevelMaxSpreadSamples=[2:0,4:0,8:0,16:0]
vectorMaxSpreadSamples=[2:0,4:0,8:0,16:0]
vectorMaxPrePauseOffsetSamples=0
sampleRate=48000
```

### Sweep 2

The same 2/4/8/16 matrix completed again with the same result:

```text
completedTrials=8/8
highLevelMaxSpreadSamples=[2:0,4:0,8:0,16:0]
vectorMaxSpreadSamples=[2:0,4:0,8:0,16:0]
vectorMaxPrePauseOffsetSamples=0
sampleRate=48000
```

Across both sweeps there were 16 finalized trials total. Every trial captured the full requested source count, every final state reported all corresponding streams closed, and every sampled source in each group reported the same sample offset at each checkpoint.

The 16-source high-level trials reported equal offsets across all 16 sources, including:

```text
start:      [0 x16]
t+2 ticks:  [4800 x16]
t+5 ticks:  [11520 x16] (one sweep) / equivalent equal-offset progression
t+10 ticks: [24000 x16]
```

The 16-source vector trials likewise reported equal offsets across all 16 sources. Vector calls completed with `vectorError=0`; observed vector-call durations were only diagnostic and ranged in the low tens of microseconds for the tested groups.

## Preparation-leak evidence for vector mode

Every vector capture recorded:

```text
prePauseOffsetSamples=0
postRewindOffsetSamples=0
```

and the final comparison reported:

```text
vectorMaxPrePauseOffsetSamples=0
```

Therefore this exact probe did not observe samples advancing before the pause/rewind arm step. The silent preroll also protects against a tiny audible preparation leak that could occur below the probe's observation granularity.

## Errors / cleanup

- no HighAudio ERROR/FATAL entry;
- no OpenAL error;
- no Mixin application failure;
- no vector error;
- all trials finalized with full stream closure;
- client/server shutdown after the comparison was clean.

Unrelated warnings remained the known YACL missing refmap / missing JetBrains annotation, offline user type, asset schema, goat-horn sound, and shader warnings.

## Interpretation

This test shows that **ordinary Minecraft high-level scheduling already achieved zero measurable intra-group skew at every sampled checkpoint through 16 simultaneous streams on the tested runtime**. The lower-level vector start did not improve the measured result.

This does **not** prove mathematical first-sample simultaneity below OpenAL Soft's reported `AL_SAMPLE_OFFSET` granularity or between the exact instants at which the probe samples. It proves that any such skew, if present, was below what the tested renderer reported and did not persist into the measured checkpoints.

Therefore the experiment no longer provides an evidence-based need for private OpenAL start control. The architecture decision remains explicit:

- **Option A — high-level Minecraft scheduling:** same measured result, substantially cleaner lifecycle/compatibility boundary, no private source-id/OpenAL start coupling.
- **Option B — narrow vector start:** stronger explicit simultaneous-start primitive in theory, but no measured improvement here and introduces version-sensitive private/OpenAL coupling.

Independent raw OpenAL ownership remains unnecessary and out of scope.
