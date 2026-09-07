# ADR-0008 — Add HighAudio speaker methods with a targeted GenericSource

**Status:** Proposed — requires `EXP-001` / `GATE-001`  
**Date:** 2026-09-07  
**Search tags:** `ADR-0008`, `GenericSource`, `SpeakerPeripheral`, `LUA-METHODS`

## Context

HighAudio needs to add Lua methods to the normal CC:T speaker while preserving CC:T's existing peripheral object, native methods, attach/detach behavior, equality, and wired-modem behavior.

Exact CC:T 1.120.0 exposes the public `ComputerCraftAPI.registerGenericSource` registration API. Its method supplier can apply a generic method to runtime objects assignable to the method's first target parameter. Therefore a source can target the exact internal `SpeakerPeripheral` class and contribute `highAudioProbe` without replacing the speaker peripheral or transforming CC:T bytecode.

The public registration API does not make `SpeakerPeripheral` public API: this remains intentional exact-version implementation coupling and must stay localized under `integration/cct` per ADR-0002.

## Proposed decision

Register one HighAudio `GenericSource` targeted at exact CC:T 1.120.0 `SpeakerPeripheral`.

For MILESTONE-001 expose only a diagnostic method such as:

```lua
speaker.highAudioProbe()
```

Do not implement media playback in this experiment.

## Why this currently leads

Compared with the superseded additive-Mixin candidate:

- CC:T bytecode is not transformed;
- no Mixin configuration/application failure mode is introduced;
- the original speaker peripheral object remains CC:T-owned;
- direct and wired method maps are built through CC:T's normal method supplier;
- block, turtle, and pocket speaker subclasses can be observed through one target type.

## Tradeoffs

- HighAudio must compile against the exact CC:T implementation artifact because `SpeakerPeripheral` is not public API.
- A future CC:T version may move/change that internal class or method-supplier behavior.
- CC:T's `disabled_generic_methods` configuration can disable this source or method. That is an intentional CC:T administrator control and must be documented if this approach is accepted.
- Because turtle/pocket speakers derive from the same base class, method exposure there must be observed and product behavior decided explicitly.

## Acceptance evidence required

`GATE-001` must prove in the assembled runtime:

- `highAudioProbe` is visible and callable on the normal speaker;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain usable;
- direct attachment works;
- wired-modem exposure works without a duplicate HighAudio peripheral;
- attach/detach, chunk unload/reload, and break/re-place do not corrupt method exposure;
- turtle/pocket visibility is observed;
- NeoForge 21.1.247 and 21.1.248 both build and start successfully.

## Fallback if rejected

Return first to the already-viable additive `SpeakerPeripheral` Mixin from ADR-0003, then evaluate more invasive forwarding/wrapping only if necessary.

## Acceptance rule

Change this ADR to `Accepted` only after the consolidated `TEST-BATCH-001` runtime gate passes and exact evidence is recorded.
