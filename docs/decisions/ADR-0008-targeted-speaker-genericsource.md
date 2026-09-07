# ADR-0008 — Add HighAudio speaker methods with a targeted GenericSource

**Status:** Accepted — `EXP-001` / `GATE-001` passed  
**Date:** 2026-09-07  
**Accepted:** 2026-09-07  
**Search tags:** `ADR-0008`, `GenericSource`, `SpeakerPeripheral`, `LUA-METHODS`

## Context

HighAudio needs to add Lua methods to the normal CC:T speaker while preserving CC:T's existing peripheral object, native methods, attach/detach behavior, equality, and wired-modem behavior.

Exact CC:T 1.120.0 exposes the public `ComputerCraftAPI.registerGenericSource` registration API. Its method supplier can apply a generic method to runtime objects assignable to the method's first target parameter. Therefore a source can target the exact internal `SpeakerPeripheral` class and contribute `highAudioProbe` without replacing the speaker peripheral or transforming CC:T bytecode.

The public registration API does not make `SpeakerPeripheral` public API: this remains intentional exact-version implementation coupling and must stay localized under `integration/cct` per ADR-0002.

## Decision

Register one HighAudio `GenericSource` targeted at exact CC:T 1.120.0 `SpeakerPeripheral`.

MILESTONE-001 exposed only the diagnostic method:

```lua
speaker.highAudioProbe()
```

Future HighAudio speaker methods may use the same integration point, while media playback/session/network implementation remains owned by HighAudio rather than CC:T.

## Why this was accepted

Compared with the superseded additive-Mixin candidate:

- CC:T bytecode is not transformed;
- no HighAudio Mixin configuration/application failure mode is introduced;
- the original speaker peripheral object remains CC:T-owned;
- direct and wired method maps are built through CC:T's normal method supplier;
- block, turtle, and pocket speaker subclasses are reached through one target type.

## Tradeoffs

- HighAudio must compile against the exact CC:T implementation artifact because `SpeakerPeripheral` is not public API.
- A future CC:T version may move/change that internal class or method-supplier behavior.
- CC:T's `disabled_generic_methods` configuration can disable this source or method. That is an intentional CC:T administrator control, not an implementation failure.
- Turtle/pocket speakers derive from the same base class, so product support policy for moving emitters remains a later explicit decision.

## Acceptance evidence

The frozen candidate was:

```text
branch: milestone-001-exp-001-genericsource
code/evidence commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI run: 34082746562
JAR SHA-256:
0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Automatic evidence passed on both NeoForge 21.1.247 and 21.1.248, including compilation, exact CC:T method-supplier invocation, speaker-only targeting, live `ServerContext` registration, development-server startup, and clean installed packaged-JAR dedicated-server startup.

NeoForge 21.1.247 then received broad real-client runtime coverage. The supplied logs proved:

- normal direct block speaker exposure/callability;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain usable;
- wired remote speaker exposure/callability;
- turtle speaker exposure/callability, including recreated peripheral instances;
- real `PocketSpeakerPeripheral` exposure/callability;
- reconstructed block-speaker peripheral instances continue to receive HighAudio's GenericSource method after the deterministic lifecycle test;
- no observed HighAudio-specific runtime exception.

The additional NeoForge 21.1.248 gameplay repetition was waived after re-audit. The exact finished candidate already passed both development and installed packaged-server runtime checks on 21.1.248, and NeoForge's official 21.1.248 changelog shows that the only change from 21.1.247 is a `SolidBucketItem#getPlaceSound` backport, unrelated to CC:T GenericSource/peripheral dispatch or the sound-engine integration used here.

This satisfies GATE-001's compatibility requirement without pretending a second manual client session occurred.

## Fallback

`ADR-0003` remains a technically viable superseded fallback if a future exact CC:T version makes the targeted GenericSource approach unusable. More invasive forwarding/wrapping should still be considered only after that fallback.

## Consequences

MILESTONE-001 / GATE-001 is complete. The next implementation gate is MILESTONE-002 / EXP-002: prove Minecraft-owned high-quality PCM playback and sound-engine lifecycle access.
