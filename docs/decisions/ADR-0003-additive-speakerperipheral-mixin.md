# ADR-0003 — Add HighAudio methods with an additive SpeakerPeripheral Mixin

**Status:** Superseded by `ADR-0008` before manual runtime gate  
**Date:** 2026-09-07  
**Search tags:** `ADR-0003`, `MIXIN`, `SpeakerPeripheral`, `LUA-METHODS`

## Context

The exact CC:T 1.120.0 block speaker already exposes a concrete `SpeakerPeripheral`. The original leading proposal was a minimal additive Mixin which would add HighAudio Lua methods without replacing the existing peripheral object.

Automatic EXP-001 work showed this approach was technically viable: the Mixin could apply, the added method and annotation were present, native speaker methods remained present in reflective checks, and dedicated-server startup succeeded on NeoForge 21.1.247 and 21.1.248.

Before spending the consolidated manual Minecraft gate, the project re-evaluated CC:T 1.120.0's exact `GenericSource`/method-supplier path. A narrower alternative was found which can add methods to `SpeakerPeripheral` targets without transforming CC:T bytecode.

## Superseded decision

Do **not** use the additive Mixin as the current MILESTONE-001 candidate.

Keep it as the first fallback if the targeted GenericSource approach fails its real runtime gate or proves unsuitable later.

## Why superseded instead of rejected

The Mixin did not fail its automatic proof. It was superseded because a less invasive candidate became available before the manual gate:

- no CC:T class transformation;
- no Mixin application lifecycle;
- still preserves the original `IPeripheral` object;
- still keeps CC:T internal coupling localized.

The replacement has its own tradeoffs: it still targets the non-public `SpeakerPeripheral` class at compile time, and CC:T's `disabled_generic_methods` configuration can disable registered generic methods.

## Historical alternatives considered

- capability/provider wrapping: higher identity/invalidation/ordering risk;
- full forwarding/replacement peripheral: reproduces more CC:T lifecycle behavior;
- CC:T fork: too invasive unless smaller approaches fail.

## Replacement

See `ADR-0008-targeted-speaker-genericsource.md` and `EXP-001`.
