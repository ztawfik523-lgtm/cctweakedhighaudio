# ADR-0001 — CC:Tweaked is required for v1; ship one CC:T-focused mod

**Status:** Accepted  
**Date:** 2026-09-07  
**Search tags:** `ADR-0001`, `CC:T-REQUIRED`, `SINGLE-JAR`, `STANDALONE`

## Context

The product goal is high-quality media playback through normal CC:Tweaked speakers. CC:T already provides the computer/Lua runtime, peripherals, wired modem discovery, attachment/event model, normal speaker block, turtle/pocket speaker upgrades, filesystem API, and HTTP API.

A fully standalone HighAudio mod would require its own player-facing block/control model and then still need a CC:T integration to satisfy the original use case. A deeply CC:T-internal implementation, on the other hand, would spread unstable implementation dependencies through media/network/client code.

## Decision

For v1:

- CC:Tweaked **1.120.0** is a required dependency.
- HighAudio ships as **one NeoForge mod/JAR**.
- The public product experience uses the existing `computercraft:speaker` rather than a replacement HighAudio speaker block.
- Internally, HighAudio remains modular enough that media/session/network/client systems are not defined by CC:T implementation internals (see ADR-0002).

This is not a commitment to a universal standalone audio framework.

## Alternatives considered

### Deep CC:T integration everywhere

**Benefit:** less abstraction and possibly less glue initially.  
**Cost:** decoder/network/session/lifecycle code becomes tied to classes CC:T itself labels non-API; harder upgrades and testing.

Rejected as the default architecture, though a small deliberate internal hook is allowed.

### Fully standalone HighAudio with optional CC:T addon

**Benefit:** maximum reuse without CC:T.  
**Cost:** creates a second product problem (own speaker/control ecosystem) before solving the requested CC:T speaker problem; still needs an integration layer later.

Rejected for v1. Revisit only if the project intentionally becomes a general Minecraft audio platform.

## Consequences

Positive:

- normal CC programs/users keep the device they already understand;
- no duplicate speaker block/computer ecosystem;
- v1 scope stays focused on audio quality/control/sync;
- internal separation still leaves room for future other emitters.

Negative:

- HighAudio cannot run without CC:T in v1;
- exact CC:T version behavior matters;
- at least one integration point may have to target a CC:T non-API class.

## Validation/evidence

- CC:T 1.120.0 exact source/release is pinned in `VERIFIED-FACTS.md`.
- CC:T explicitly documents its API boundary and warns about non-API classes.
- The original user/product requirement is normal CC:T speaker playback.

## Revisit when

- there is a concrete non-CC:T user/product requirement;
- CC:T integration becomes more work than implementing a separate primary device model;
- HighAudio is intentionally repositioned as a general Minecraft media/audio framework.
