# ADR-0002 — Localize CC:T implementation internals

**Status:** Accepted  
**Date:** 2026-09-07  
**Search tags:** `ADR-0002`, `CCT-INTERNALS`, `BOUNDARY`, `INTEGRATION-LAYER`

## Context

The normal CC:T speaker is implemented by classes outside `dan200.computercraft.api`, while CC:T explicitly warns that non-API classes may change. Enhancing the existing speaker may still require one such hook.

HighAudio's decoder, session, cache, network, synchronization, and client media logic do not inherently require CC:T implementation classes.

## Decision

Any dependency on CC:T non-API classes must be confined to a narrow CC:T integration area.

Core/server/client/media/network code must communicate through HighAudio-owned concepts such as:

```text
EmitterId
ContentId
SessionId
MediaSession command/state
upload/content operations
```

It must not import `dan200.computercraft.shared.*` merely for convenience.

Minecraft/NeoForge platform types are allowed where they are the real platform abstraction; this ADR isolates CC:T internals, not Minecraft itself.

## Alternatives considered

### Use CC:T internals directly throughout the project

Simpler initially, but turns every subsystem into version-sensitive integration code. Rejected.

### Build a platform-independent generic Java audio framework

Avoids both CC:T and Minecraft types, but introduces fake abstractions with no current product benefit. Rejected.

## Consequences

- replacing a Mixin/capability hook later should not rewrite decoding/networking;
- upgrade to another CC:T version has a small obvious compatibility surface;
- some adapter objects/translation code are required;
- architecture/package reviews must reject leakage of CC:T internal imports outside the integration area.

## Validation

Use static/package dependency checks once code exists. `RISK-001` tracks the unavoidable version-sensitive boundary.
