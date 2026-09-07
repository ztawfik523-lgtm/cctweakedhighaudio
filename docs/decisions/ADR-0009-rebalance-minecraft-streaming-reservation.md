# ADR-0009 — Rebalance Minecraft-owned streaming reservation

**Status:** Proposed — SPR-off real-client EXP-003 Part A2 PASS; exact SPR coexistence/reload still required  
**Date:** 2026-09-07  
**Related:** ADR-0004, EXP-003, RISK-004, RISK-005, RISK-008

## Context

EXP-002 proved that arbitrary HighAudio PCM can render cleanly through Minecraft-owned `SoundInstance + AudioStream + SoundManager` playback with normal position/category/pause/reload lifecycle behavior.

EXP-003 Part A then measured the actual vanilla streaming reservation on NeoForge 21.1.247 with SPR absent. Requested counts `1, 2, 4, 6, 8` all allocated completely. Requests `10, 12, 16` each allocated exactly 8 channels. Minecraft's debug string reached `... + 8/8` and no ninth `PlayStreamingSourceEvent` capture appeared.

This means the vanilla streaming policy does not meet the project's 16-source stress target, but it does **not** show that the OpenAL device is limited to eight sources. The same Minecraft audio `Library` has a much larger static-side reservation on the tested runtime.

## Decision under test

Before introducing a second static-buffer backend or independent raw OpenAL ownership, test a narrow client-side change to Minecraft's own channel reservation:

- keep Minecraft `Library`/`Channel` ownership;
- keep HighAudio's existing `SoundInstance + AudioStream + SoundManager` path;
- preserve the runtime's existing combined static+streaming reservation;
- on eligible vanilla layouts, raise the streaming reservation to at most 16 by reducing the static reservation by the same delta;
- do not force the 16-stream rebalance on lower-capacity layouts where vanilla itself derives fewer than eight streaming slots;
- never increase the total reservation in this experiment;
- never create/delete independent HighAudio OpenAL sources.

On the measured 255-channel runtime the transformation is `247 static + 8 streaming -> 239 static + 16 streaming`. Those numbers are runtime evidence, not universal constants; the implementation derives the original reservation and preserves its combined total.

## Why this candidate is first

### Compared with staying vanilla-streaming-only

Vanilla-only is the cleanest possible boundary but empirically caps the tested runtime at 8 streaming sounds, below the intended 16-source stress target.

### Compared with a hybrid static + streaming backend

A hybrid backend could potentially exploit the larger static reservation for finite decoded media, but it would create a second PCM attachment/buffer-lifetime path with different seek/cache/sync semantics. That complexity is unnecessary if the existing proven streaming path can meet the target by changing only Minecraft's reservation policy.

### Compared with independent raw OpenAL ownership

Independent ownership gives maximum control but duplicates allocation/deletion, category integration, F3+T/device/world cleanup, and source-pool coexistence. It also bypasses the normal Minecraft-owned `Channel.play()` path that SPR already hooks. It remains a fallback only if Minecraft-owned approaches fail.

## SPR compatibility rationale

Exact SPR 1.21.1 source research shows that SPR hooks Minecraft audio classes including `Library`, `SoundEngine`, and `Channel`; it accesses the Minecraft-owned source id and applies acoustics from `Channel.play()`.

A reservation rebalance does not create a parallel source lifecycle. HighAudio sounds still pass through the same Minecraft `Channel` surface SPR expects. This makes coexistence more plausible than independent raw-source ownership, but it is **not yet runtime proof**. EXP-003 Part A2 still requires the exact SPR-on coexistence/reload comparison.

## Scope boundary

Allowed in the prototype:

- one narrow client-only Minecraft-audio Mixin/access patch;
- runtime calculation/diagnostics of original and adjusted reservations;
- exact automatic NeoForge 21.1.247/21.1.248 checks;
- existing EXP-003 capacity probe reuse.

Not allowed:

- changing CC:T speaker bytecode;
- increasing total source count;
- independent `alGenSources` / `alDeleteSources` ownership;
- codecs, media upload, network/session/sync production code;
- claiming SPR compatibility before the combined runtime test.

## Prototype evolution

The first automatically green rebalance candidate (`fc4c63efc5377700d71a78683cd123dc60b7d635`, CI `34102697796`) was superseded before user manual testing. Re-evaluation showed it could be too aggressive on lower-capacity devices because it could attempt to raise a vanilla streaming reservation below eight to 16.

The frozen conservative candidate is:

```text
code/CI commit:    82c195637de3987463c864c8f8493e9194410094
CI run:            34103604455
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256 on both matrix legs:
f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

Both matrix JARs are byte-identical. Exact development-client sound-engine initialization on `.247` and `.248` logged the expected eligible-runtime transform:

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

The client smoke used OpenAL Soft `No Output`; OpenAL initialized successfully and Minecraft's sound engine started after the rebalance. Packaged-JAR dedicated-server startup passed on both target versions. Bytecode/symbol inspection found no HighAudio raw source creation/deletion in the reservation Mixin.

## Real-client SPR-off evidence

The conservative candidate was then tested on the user's real NeoForge 21.1.247 Windows client with SPR absent. OpenAL initialized on:

```text
OpenAL Soft on Speakers (4- USB Audio Device)
```

The same runtime transform applied with `combinedPreserved=true` and `rebalanceApplied=true`.

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

Thus the 16-channel target succeeded on five separate runs. Natural 16-channel runs retained `captures=16`, `activeSounds=16`, and `soundDebug=... + 16/16` through the t+5/t+20/t+40 snapshots. Both explicit-stop runs captured all 16 channels and closed all 16 streams cleanly. Every completed run reached finalization after the 10-inactive-tick grace, and no stale-run capture was observed.

The static pool remained available while all 16 streaming slots were occupied, with observed states including `Sounds: 1/239 + 16/16` and `Sounds: 2/239 + 16/16`.

Across the supplied logs there were no `ERROR` or `FATAL` entries and no HighAudio/OpenAL/Mixin allocation failure.

Canonical evidence:

`docs/test-batches/evidence/TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md`

## Acceptance criteria

ADR-0009 may move from Proposed to Accepted only if EXP-003 Part A2 proves:

1. the exact `.247` and `.248` candidate builds and starts cleanly — **PASS**;
2. the automatic runtime logs show the combined reservation is preserved — **PASS**;
3. `capacity 16` obtains 16 unique Minecraft-owned streaming channels with SPR absent — **PASS, repeated five times on the real `.247` client**;
4. cleanup behaves cleanly and F3+T/device reload recreates the intended reservation — **cleanup PASS; real-client reload reapplication still pending in the SPR-on comparison**;
5. a short exact SPR 1.5.1 coexistence run has no Mixin/OpenAL conflict and still obtains the required channels — **NOT RUN**;
6. normal Minecraft/CC:T sound behavior is not obviously regressed in the targeted checks — **automatic M1/server regressions PASS; static-pool activity coexisted with 16/16 HighAudio streams; exact SPR-on real-client observation still pending**.

If either remaining reload/SPR criterion fails, keep ADR-0009 Proposed/Rejected and return to the documented alternative set rather than silently broadening the patch.
