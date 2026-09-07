# TEST-BATCH-001 evidence — NeoForge 21.1.247

**Date:** 2026-09-07  
**Status:** PARTIAL PASS — useful runtime evidence captured; full GATE-001 not yet complete  
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

### Direct block speaker

Four successful paired runs were observed against computer 3 / attachment `right` with the same block-speaker runtime class and the same CC:T native source/object identity:

```text
runtimeClass=dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral
nativeSource=eef8586b-1e10-46a8-a8ec-1749ecc96beb
identityHash=0x59ebfe48
```

Observed paired runs began at approximately:

- 08:03:11
- 08:03:15
- 08:04:08
- 08:04:13

The first two runs occurred before the user's logged `moving away` marker. The latter two occurred after `going back`.

### Wired block speaker

A wired remote speaker appeared as `speaker_27`. Three successful paired runs were observed:

```text
attachment=speaker_27
runtimeClass=dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral
nativeSource=b78b3e18-8c51-47df-a3f7-134febca537d
identityHash=0x47da06bd
```

This proves HighAudio's GenericSource method is callable through CC:T's wired peripheral path and the native speaker sequence remains usable there.

The wired speaker's source/object identity differs from the earlier direct `right` speaker. The logs alone do not prove whether this was the same physical speaker re-exposed through a modem or a second speaker. Therefore the exact "same physical speaker direct vs wired" identity check remains pending.

### Turtle speaker

Two successful paired runs were observed on turtle computer 8 / attachment `right`:

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

This proves the GenericSource reaches real turtle speaker peripherals and survives at least one recreation of that peripheral object. The supplied logs do not identify the exact action which caused the recreation, so it is not assigned to a specific lifecycle gate such as reboot or upgrade removal/re-addition.

### Later block-speaker instance

A final successful paired direct block-speaker run used a different source/object identity:

```text
nativeSource=b00e2df5-dc49-4df1-81a7-ae394519f028
identityHash=0x52ae0f7d
```

This proves a newly-created block speaker peripheral also receives the GenericSource method and completes the native method test sequence. The logs do not unambiguously identify whether this instance came from break/re-place, reload, or another action, so that exact lifecycle label remains pending.

## What is not yet proven

### Chunk unload/reload

The user logged `moving away` and later `going back`, and reduced view/simulation distance during the interval. However the post-return direct speaker retained the exact same Java object identity (`0x59ebfe48`) and native source UUID as before. That is not sufficient evidence that the speaker chunk actually unloaded and reconstructed its block entity/peripheral.

Treat the chunk unload/reload item as **pending**, not passed.

### Same physical speaker direct vs wired

Direct `right` used source `eef8586b-...` / identity `0x59ebfe48`, while wired `speaker_27` used source `b78b3e18-...` / identity `0x47da06bd`. This is consistent with two different speaker peripheral instances. The logs do not establish that one physical speaker was observed through both attachment paths.

Treat the direct-vs-wired same-speaker identity item as **pending**, while wired method exposure itself is **passed**.

### Pocket speaker

The logs contain a user marker for `pocket computer` followed by `no speaker obv`, but no HighAudio probe from a pocket speaker peripheral. Pocket-speaker runtime exposure is therefore **not tested** in this evidence set.

### NeoForge 21.1.248 manual subset

No 21.1.248 manual runtime evidence is included in this evidence set. Automatic CI for 21.1.248 remains green, but the manual compatibility subset is still pending.

## Noise not attributed to HighAudio

The supplied debug log contains an `org.jetbrains.annotations.ApiStatus$Internal` ClassNotFound warning during another mod's Mixin processing. Minecraft continues startup, HighAudio's GenericSource self-check and live registry checks both pass afterward, and no HighAudio-specific exception/failure is present in the supplied session.

## Current assessment

NeoForge 21.1.247 has strong runtime evidence for:

- real block-speaker Lua exposure;
- native `playNote`, `playSound`, `playAudio`, and `stop` remaining usable through the same test sequence;
- real wired-peripheral Lua exposure and native method usability;
- real turtle-speaker Lua exposure and native method usability;
- GenericSource exposure on newly-created block/turtle speaker peripheral instances;
- no observed HighAudio runtime exception in the supplied session.

GATE-001 remains **IN PROGRESS** because chunk unload/reload, same-physical-speaker direct/wired comparison, pocket exposure, and the NeoForge 21.1.248 manual subset are not yet proven by these logs.
