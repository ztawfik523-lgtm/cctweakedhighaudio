# ADR-0009 — Rebalance Minecraft-owned streaming reservation

**Status:** Proposed — selected for EXP-003 Part A2, not yet accepted for production  
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
- raise the streaming reservation to at most 16 by reducing the static reservation by the same delta;
- never increase the total reservation in this experiment;
- never create/delete independent HighAudio OpenAL sources.

On the measured runtime the expected transformation is `247 static + 8 streaming -> 239 static + 16 streaming`. Those numbers are runtime evidence, not universal constants; the implementation must derive the original reservation and preserve its combined total.

## Why this candidate is first

### Compared with staying vanilla-streaming-only

Vanilla-only is the cleanest possible boundary but empirically caps the tested runtime at 8 streaming sounds, below the intended 16-source stress target.

### Compared with a hybrid static + streaming backend

A hybrid backend could potentially exploit the larger static reservation for finite decoded media, but it would create a second PCM attachment/buffer-lifetime path with different seek/cache/sync semantics. That complexity is unnecessary if the existing proven streaming path can meet the target by changing only Minecraft's reservation policy.

### Compared with independent raw OpenAL ownership

Independent ownership gives maximum control but duplicates allocation/deletion, category integration, F3+T/device/world cleanup, and source-pool coexistence. It also bypasses the normal Minecraft-owned `Channel.play()` path that SPR already hooks. It remains a fallback only if Minecraft-owned approaches fail.

## SPR compatibility rationale

Exact SPR 1.21.1 source research shows that SPR hooks Minecraft audio classes including `Library`, `SoundEngine`, and `Channel`; it accesses the Minecraft-owned source id and applies acoustics from `Channel.play()`.

A reservation rebalance does not create a parallel source lifecycle. HighAudio sounds should still pass through the same Minecraft `Channel` surface SPR expects. This makes coexistence more plausible than independent raw-source ownership, but it is **not yet runtime proof**. EXP-003 Part A2 requires a dedicated SPR-on coexistence check after the SPR-off 16/16 baseline succeeds.

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

## Acceptance criteria

ADR-0009 may move from Proposed to Accepted only if EXP-003 Part A2 proves:

1. the exact `.247` and `.248` candidate builds and starts cleanly;
2. the runtime logs show the combined reservation is preserved;
3. `capacity 16` obtains 16 unique Minecraft-owned streaming channels with SPR absent;
4. cleanup and F3+T/device reload recreate the intended reservation cleanly;
5. a short SPR 1.5.1 coexistence run has no Mixin/OpenAL conflict and still obtains the required channels;
6. normal Minecraft/CC:T sound behavior is not obviously regressed in the targeted checks.

If any of these fail, keep ADR-0009 Proposed/Rejected and return to the documented alternative set rather than silently broadening the patch.
