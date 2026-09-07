# ADR-0009 — Rebalance Minecraft-owned streaming reservation

**Status:** Accepted — real-client 16/16 target passed repeatedly with combined reservation preserved  
**Date:** 2026-09-07  
**Related:** ADR-0004, EXP-003, RISK-004, RISK-005, RISK-008

## Context

EXP-002 proved that arbitrary HighAudio PCM can render cleanly through Minecraft-owned `SoundInstance + AudioStream + SoundManager` playback with normal position/category/pause/reload lifecycle behavior.

EXP-003 Part A then measured the actual vanilla streaming reservation on NeoForge 21.1.247 with SPR absent. Requested counts `1, 2, 4, 6, 8` all allocated completely. Requests `10, 12, 16` each allocated exactly 8 channels. Minecraft's debug string reached `... + 8/8` and no ninth `PlayStreamingSourceEvent` capture appeared.

This established a policy limit rather than a device-wide eight-source limit: the same Minecraft audio `Library` had a much larger static-side reservation on the tested runtime.

## Decision

HighAudio keeps Minecraft `Library`/`Channel` ownership and conservatively rebalances Minecraft's own static/streaming source reservation when the observed vanilla layout is eligible.

Rules:

- keep HighAudio's `SoundInstance + AudioStream + SoundManager` playback path;
- preserve the runtime's existing **combined** static+streaming reservation;
- when vanilla already provides its normal 8-stream reservation and sufficient static capacity exists, raise the streaming reservation to at most 16 by reducing static reservation by the same delta;
- do **not** force a 16-stream layout on lower-capacity devices where vanilla derives fewer than eight streaming slots;
- never increase the total source reservation as part of this policy;
- never create/delete independent HighAudio OpenAL sources.

On the measured 255-channel runtime the accepted transform is:

```text
247 static + 8 streaming
        ->
239 static + 16 streaming
```

Those exact counts are runtime evidence, not universal device constants. The implementation derives the original reservation and preserves its combined total.

## Why this is preferred

### Compared with staying vanilla-streaming-only

Vanilla streaming is simpler but empirically caps the tested runtime at 8 streams, below the project's intended 16-source stress target.

### Compared with a hybrid static + streaming HighAudio backend

A second static PCM path would create different buffer lifetime, seek, cache, and synchronization semantics before there is evidence it is needed. Rebalancing lets the already-proven streaming backend meet the target without adding that parallel path.

### Compared with independent raw OpenAL ownership

Independent ownership duplicates source allocation/deletion, category integration, F3+T/device/world cleanup, and source-pool coexistence. It also weakens natural compatibility with mods that hook Minecraft-owned channels. The accepted rebalance changes Minecraft's allocation policy but not source ownership.

## Prototype evolution

The first automatically green candidate (`fc4c63efc5377700d71a78683cd123dc60b7d635`, CI `34102697796`) was superseded before manual testing because it could be too aggressive on lower-capacity devices.

The conservative candidate is:

```text
code/CI commit:    82c195637de3987463c864c8f8493e9194410094
CI run:            34103604455
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256 on both matrix legs:
f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

Both matrix JARs were byte-identical. Development-client sound-engine initialization on `.247` and `.248` logged:

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

Packaged-JAR dedicated-server startup passed on both target versions. Bytecode inspection found no HighAudio raw source creation/deletion in the reservation Mixin.

## Real-client evidence

The conservative candidate was tested on the user's real NeoForge 21.1.247 Windows/OpenAL Soft client with SPR absent.

Capacity requests succeeded as follows:

```text
4  -> 4
8  -> 8
12 -> 12
16 -> 16
16 -> 16
16 -> 16  (explicit stop)
16 -> 16  (explicit stop)
16 -> 16
```

Thus the 16-channel target succeeded on five separate runs. Full-length 16-channel runs retained `captures=16`, `activeSounds=16`, and `soundDebug=... + 16/16` through the diagnostic snapshots. Explicit-stop runs captured all 16 channels first and then closed all 16 streams cleanly.

Ordinary static-side activity remained available while the streaming side was saturated, with observed states including:

```text
Sounds: 1/239 + 16/16
Sounds: 2/239 + 16/16
```

No stale-run capture, HighAudio/OpenAL allocation failure, Mixin failure, ERROR, or FATAL entry was observed in the supplied test logs.

Canonical evidence:

`docs/test-batches/evidence/TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md`

## Acceptance rationale

The policy is accepted because the questions it owns are now answered:

1. exact `.247` and `.248` builds/client initialization succeed;
2. the combined reservation is preserved automatically;
3. the real target runtime repeatedly obtains all 16 Minecraft-owned streaming channels;
4. cleanup succeeds after both natural completion and explicit stop;
5. the remaining static reservation can still serve ordinary Minecraft sounds during 16/16 streaming use;
6. HighAudio still does not own a second OpenAL source pool.

A separate SPR-on manual launch is **not** an acceptance prerequisite for this reservation-policy ADR. Broad Sound Physics Remastered coexistence has its own dedicated `MILESTONE-010`, and duplicating that test here would conflict with the project's minimum-manual-testing policy.

Likewise, the accepted policy does not claim that every future audio device has a 255-source layout or that every device will be eligible for 16 streams. The implementation's conservative eligibility check is part of the decision.

## Reload / compatibility boundary

EXP-002 already proved Minecraft sound-engine rebuild behavior and HighAudio playback recovery through F3+T. This ADR's client Mixin applies during Minecraft `Library` source-pool construction, and exact `.247/.248` automated client initialization proves the target construction point is valid.

A rebalance-specific real-device F3+T repetition is retained as later bundled regression coverage rather than a separate acceptance launch. Exact SPR 1.5.1 coexistence, acoustics, reload behavior, and 1/4/16-source performance belong to MILESTONE-010.

If later evidence shows the reservation does not reapply after a supported renderer rebuild, conflicts with SPR, or harms ordinary Minecraft source behavior, this ADR must be revisited rather than silently raising the total source budget.

## Scope boundary

Allowed:

- one narrow client-only Minecraft-audio reservation Mixin;
- runtime-derived reservation adjustment with total preserved;
- diagnostics and exact-version validation.

Not allowed by this ADR:

- increasing the total source budget;
- `alGenSources` / `alDeleteSources` ownership;
- a separate HighAudio OpenAL device/context;
- claiming exact SPR compatibility before MILESTONE-010;
- treating `239 + 16` as a universal hardware constant.
