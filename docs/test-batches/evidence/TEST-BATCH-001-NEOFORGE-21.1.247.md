# TEST-BATCH-001 evidence — NeoForge 21.1.247

**Date:** 2026-09-07  
**Status:** PASS — NeoForge 21.1.247 broad manual runtime coverage complete  
**Target:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247  
**HighAudio candidate:** `milestone-001-exp-001-genericsource` / EXP-001 GenericSource probe

## What the supplied logs prove

The client loaded the intended stack: CC:Tweaked 1.120.0, HighAudio 0.1.0-dev EXP-001, Minecraft 1.21.1, and NeoForge 21.1.247.

HighAudio startup checks passed in the real client/integrated-server runtime:

```text
[EXP-001] GenericSource method-supplier self-check PASS ... probeInvocation=true speakerOnly=true nativeMethodsPresent=[playNote, stop, playSound, playAudio]
[EXP-001] GenericSource registered id=cctweakedhighaudio:speaker
[EXP-001] live ServerContext self-check PASS ... probePresent=true speakerOnly=true nativeMethodsPresent=[playNote, stop, playSound, playAudio]
```

The Lua test program calls `highAudioProbe()` before its native speaker sequence and only calls `highAudioProbe()` a second time after `playNote`, `playSound`, `playAudio`, and `stop` have all completed without an exception or persistent `false` result. Therefore each matching pair of probe lines is evidence that the full local test sequence reached its end.

## Initial session evidence

### Direct block speaker

Multiple successful paired runs were observed against a directly attached normal block speaker. The GenericSource method remained visible/callable and the native speaker method sequence completed.

One repeatedly observed instance used:

```text
runtimeClass=dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral
nativeSource=eef8586b-1e10-46a8-a8ec-1749ecc96beb
identityHash=0x59ebfe48
```

### Wired block speaker

A real wired remote speaker appeared as `speaker_27`. Three successful paired runs were observed:

```text
attachment=speaker_27
runtimeClass=dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral
nativeSource=b78b3e18-8c51-47df-a3f7-134febca537d
identityHash=0x47da06bd
```

The user clarified that this was deliberately a different placed speaker from the earlier direct speaker. Therefore the differing identity is expected and is not evidence of identity churn or a wired-path defect.

This proves a real normal speaker works through direct attachment and a real normal speaker works through CC:T's wired peripheral path.

### Turtle speaker

Successful paired runs were observed on a turtle speaker:

```text
runtimeClass=dan200.computercraft.shared.turtle.upgrades.TurtleSpeaker$Peripheral
nativeSource=9d9031f3-e38a-45e5-8e76-0d13f888991b
identityHash=0x66771b33
```

A later successful paired run used a newly-created turtle speaker peripheral instance:

```text
nativeSource=41443e2f-189f-43a0-9660-96c10f3fbf00
identityHash=0x46bbb925
```

This proves the GenericSource reaches real turtle speaker peripherals and remains present after recreation of the turtle speaker peripheral object.

### Recreated block speaker

A later successful paired block-speaker run used a different source/object identity:

```text
nativeSource=b00e2df5-dc49-4df1-81a7-ae394519f028
identityHash=0x52ae0f7d
```

This established before the deterministic lifecycle retest that newly-created normal block speaker peripherals also receive the GenericSource method and complete the native method sequence.

## Deterministic lifecycle / pocket follow-up session

A second NeoForge 21.1.247 session was performed specifically to close the earlier chunk-lifecycle and pocket-speaker gaps.

### Pocket speaker — PASS

The follow-up session contained a real CC:T pocket speaker peripheral:

```text
computerId=9
attachment=back
emitterKind=pocket
runtimeClass=dan200.computercraft.shared.pocket.peripherals.PocketSpeakerPeripheral
nativeSource=51f6212c-5723-4772-81e7-996b88603a53
identityHash=0x1403dc14
```

Six probe lines were observed with the same instance, corresponding to three complete paired test executions. Because the second probe in each pair is reached only after the native method sequence succeeds, this proves `highAudioProbe`, `playNote`, `playSound`, `playAudio`, and `stop` all remained usable on the real pocket speaker peripheral.

This closes the pocket-speaker observation requirement for 21.1.247.

### Direct block speaker follow-up — PASS

A normal block speaker attached as `back` completed two paired runs:

```text
nativeSource=17f8a503-fbe2-4e1d-ba3f-e8d8c8a75a82
identityHash=0x203b4a80
```

### Wired block speaker before lifecycle reconstruction — PASS

A wired remote speaker appeared as `speaker_28` and completed a paired test run:

```text
attachment=speaker_28
nativeSource=fff67e62-5b36-4392-8560-89b3a187d866
identityHash=0x5baacffd
```

### Forced spawn-chunk lifecycle reconstruction — PASS

The follow-up session explicitly set:

```text
Gamerule spawnChunkRadius is now set to: 0
```

This removed the previous test's spawn-chunk ambiguity. After the away/return lifecycle exercise, the same wired remote name `speaker_28` was callable again, but its underlying CC:T speaker peripheral had been reconstructed:

```text
before:
nativeSource=fff67e62-5b36-4392-8560-89b3a187d866
identityHash=0x5baacffd

after:
nativeSource=b89d74b7-c4ea-4147-bcdc-9b569e83583a
identityHash=0x3ddf389b
```

The post-reconstruction instance completed two paired Lua/native test executions. The Minecraft log does not emit a dedicated block-entity-unloaded message for this case, so the evidence is based on the deliberately disabled spawn-chunk retention plus the observed replacement of both CC:T's per-instance source UUID and Java object identity while the same remote speaker name resumed functioning.

This is sufficient for MILESTONE-001's lifecycle purpose: a reconstructed real speaker peripheral still receives HighAudio's GenericSource method and preserves native speaker behavior. Persistent physical `EmitterId` semantics remain intentionally deferred to EXP-004 and later milestones.

## Runtime errors / noise

No HighAudio-specific `ERROR`, `Exception`, or fatal line was found in either supplied 21.1.247 follow-up log.

The debug log still contains an `org.jetbrains.annotations.ApiStatus$Internal` ClassNotFound warning during another mod's Mixin processing. Minecraft continues startup, HighAudio's self-check and live-registry check pass afterward, and all tested HighAudio speaker paths execute normally. It is not attributed to HighAudio.

## NeoForge 21.1.247 assessment

**PASS** for the broad MILESTONE-001 manual coverage:

- real direct block speaker exposure/callability;
- native `playNote`, `playSound`, `playAudio`, and `stop` remaining usable;
- real wired remote speaker exposure/callability;
- real turtle speaker exposure/callability;
- real pocket speaker exposure/callability;
- GenericSource exposure on newly-created/reconstructed block and turtle speaker peripheral instances;
- deterministic spawn-chunk-retention removal followed by successful speaker peripheral reconstruction and reuse;
- no observed HighAudio runtime exception.

GATE-001 remains **IN PROGRESS only because the NeoForge 21.1.248 manual compatibility subset is still pending**. Automatic evidence for 21.1.248 is already green.
