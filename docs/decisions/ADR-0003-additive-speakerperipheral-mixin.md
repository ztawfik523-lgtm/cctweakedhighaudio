# ADR-0003 — Add HighAudio methods with an additive SpeakerPeripheral Mixin

**Status:** Proposed — requires `EXP-001`  
**Date:** 2026-09-07  
**Search tags:** `ADR-0003`, `MIXIN`, `SpeakerPeripheral`, `LUA-METHODS`

## Context

The exact CC:T 1.120.0 block speaker already exposes a concrete `SpeakerPeripheral`. Generic peripheral methods do not simply merge onto an existing specific `IPeripheral`. Replacing/wrapping the capability risks changing object identity, `equals`, attach/detach, method discovery, and wired-modem behavior.

CC:T runtime method discovery scans public methods on the runtime class for `@LuaFunction`, while Sponge Mixin supports merging new methods and method annotations into target classes.

## Proposed decision

Use a **minimal additive Mixin** targeting exact CC:T 1.120.0 `SpeakerPeripheral` to add the HighAudio Lua-facing methods. The Mixin should be a thin facade into HighAudio-owned integration/services, not contain the media engine.

Do not overwrite native `playAudio`, `playSound`, `playNote`, or stop behavior.

Because upgrade speakers derive from `SpeakerPeripheral`, emitter kind must be resolved explicitly; initial turtle/pocket support policy is a separate product choice.

## Why only Proposed

Source-level evidence is strong, but the assembled runtime must prove:

- `@LuaFunction` survives transformation and is discovered;
- direct and wired attachment remain stable;
- native methods remain intact;
- NeoForge 21.1.247 and 21.1.248 both load;
- turtle/pocket exposure is understood.

`EXP-001` is the acceptance gate.

## Alternatives

### GenericPeripheral

Rejected for this use because a specific existing `IPeripheral` blocks the simple generic-method extension path.

### Capability wrapper/priority provider

Possible in principle but higher risk: delegation recursion, provider ordering, invalidation, equality, side queries, and conflicts with other wrappers must all be solved.

### Full forwarding/replacement peripheral

Can add arbitrary methods but reproduces/delegates more CC:T lifecycle behavior and risks changing peripheral equivalence.

### Fork/replace CC:T speaker implementation

Too invasive for the problem unless all smaller integration paths fail.

## Consequences if accepted

- small, obvious CC:T non-API compatibility surface;
- normal speaker peripheral object/lifecycle remains CC:T-owned;
- version pin/testing becomes mandatory;
- future CC:T updates may require changing one Mixin target;
- added methods may appear on speaker upgrades and require gating/support.

## Acceptance rule

Change status to `Accepted` only after `EXP-001` passes and its exact-version evidence is recorded. If it fails, mark this ADR `Rejected` or `Superseded`; do not edit the failure out of history.
