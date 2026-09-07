# HighAudio current handoff

**Status:** canonical chat/session handoff  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Prepared:** 2026-09-07  
**Current branch:** `milestone-001-exp-001-genericsource`  
**Next gate:** `MILESTONE-001` / `EXP-001` / `TEST-BATCH-001`

This file is the concise recovery point for a new engineering chat. Exact evidence remains more authoritative than this handoff.

## Authority order

1. completed exact-version prototype/runtime evidence;
2. `VERIFIED-FACTS.md`;
3. accepted ADRs;
4. proposed/current ADR for the active experiment;
5. `ARCHITECTURE.md`;
6. `ROADMAP.md`;
7. this handoff;
8. old chat/research prose.

Before editing code, read:

1. `VERIFIED-FACTS.md`;
2. `decisions/ADR-0002-localize-cct-internals.md`;
3. `decisions/ADR-0003-additive-speakerperipheral-mixin.md` for superseded-history context;
4. `decisions/ADR-0008-targeted-speaker-genericsource.md` for the current candidate;
5. `PROTOTYPES.md`, especially EXP-001;
6. `ROADMAP.md`, especially MILESTONE-001 / GATE-001;
7. `TESTING.md`;
8. relevant `RISKS.md` entries;
9. `ARCHITECTURE.md` as needed.

## Fixed project target

Do not silently substitute newer versions:

```text
Minecraft:     1.21.1
Java:          21
CC:Tweaked:    1.120.0
CC:T source:   tag v1.21.1-1.120.0
NeoForge:      21.1.247 baseline
Compatibility: 21.1.248
SPR future:    1.21.1-1.5.1
```

CC:T 1.120.2/current main may be used only as clearly-labelled comparison, never as proof for 1.120.0.

## Product/architecture baseline

Accepted direction remains unchanged:

- one NeoForge mod;
- CC:T is required for v1;
- the user-facing device remains the normal `computercraft:speaker`;
- HighAudio owns future media/session/network/cache/decoder/synchronization/client playback systems;
- CC:T implementation coupling stays localized under `integration/cct`;
- no standalone replacement speaker ecosystem;
- no CC:HQ architecture copy.

## MILESTONE-000

`MILESTONE-000` remains COMPLETE / `GATE-000 PASSED`.

Nothing in the EXP-001 pivot invalidates the fixed stack, Gradle/bootstrap work, metadata, wrapper checks, or NeoForge build matrix.

Recorded re-audit evidence includes:

```text
build-relevant commit: fb754659c4f2a6abe918c5fa7fa6dcad35be9bd0
CI run:               34075809793
manual MC launches:   0
```

Do **not** redo MILESTONE-000 unless new evidence specifically invalidates it.

## MILESTONE-001 pivot history

The first candidate was an additive Mixin into exact CC:T 1.120.0 `SpeakerPeripheral` (`ADR-0003`). Automatic checks showed that candidate could build/apply and start on both target NeoForge versions.

Before the consolidated manual gate, exact CC:T 1.120.0 source was re-evaluated and a less invasive route was found: register a `GenericSource` whose first target parameter is the exact internal `SpeakerPeripheral` class.

A separate comparison branch proved automatically that this route can:

- compile against exact CC:T 1.120.0;
- have CC:T's own method supplier generate `highAudioProbe` for a `SpeakerPeripheral` subtype;
- retain native `playNote`, `playSound`, `playAudio`, and `stop` in the generated method map;
- register successfully;
- reach dedicated-server ready state on NeoForge 21.1.247 and 21.1.248.

Comparison branch/run:

```text
branch: exp-001-genericsource-comparison
commit: ecacab361416c0bbdbd1bd789806f567317773db
CI run: 34078670979
JAR SHA-256 from both matrix legs:
e9a82ee4f4403881c01c4901b2dff88d86fc16cafa882581430064f9c2f1471a
```

That evidence superseded the Mixin as the leading candidate; it did **not** complete GATE-001 because real Lua/direct/wired/lifecycle behavior still needs one manual session.

## Current MILESTONE-001 implementation

Use branch:

```text
milestone-001-exp-001-genericsource
```

This branch was created fresh from current `main` specifically to avoid accidentally carrying Mixin resources/classes/config into the GenericSource candidate.

Current implementation scope is intentionally tiny:

```lua
local speaker = peripheral.find("speaker")
speaker.highAudioProbe()
```

The Java integration:

- registers `SpeakerGenericSource` with `ComputerCraftAPI.registerGenericSource`;
- targets exact CC:T 1.120.0 internal `SpeakerPeripheral`;
- keeps all non-public CC:T imports under `integration/cct`;
- exposes diagnostics only;
- does not implement PCM, codecs, uploads, ContentId, sessions, networking, sync, SPR, URL media, or VS2.

The startup self-check uses exact CC:T method-supplier machinery and fails clearly if the added method or required native methods are not generated.

## Decision status

- `ADR-0003` — **Superseded**, not failed. The Mixin remains the first fallback if GenericSource fails.
- `ADR-0008` — **Proposed** until GATE-001 passes.
- Do not mark `ADR-0008` Accepted from automatic CI alone.

Important tradeoff: `GenericSource` uses a public CC:T registration API, but targeting `SpeakerPeripheral` is still deliberate non-public implementation coupling. CC:T's `disabled_generic_methods` configuration can also disable the registered source/method; account for that in testing/documentation rather than treating it as a mysterious failure.

## GATE-001 / TEST-BATCH-001

Prefer one consolidated manual Minecraft session after automatic CI is green.

Required real-runtime proof:

- `highAudioProbe` is visible/callable on a normal speaker;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain usable;
- direct attachment works;
- wired modem exposure works without a duplicate HighAudio peripheral;
- detach/reattach or computer reboot remains sane;
- speaker chunk unload/reload keeps correct method exposure;
- break/re-place behavior is observed;
- turtle speaker visibility is observed;
- pocket speaker visibility is observed if practical;
- compatibility subset passes on NeoForge 21.1.248.

Use `docs/test-batches/TEST-BATCH-001.md` and `tools/test-batch-001.lua`.

The Lua script retries native boolean-returning speaker calls when CC:T legitimately reports temporary busy state instead of treating the first `false` as an automatic HighAudio failure.

## After GATE-001

If PASS:

1. record exact commit/JAR SHA/logs and runtime observations in `PROTOTYPES.md`;
2. mark GATE-001/MILESTONE-001 complete in `ROADMAP.md`;
3. change ADR-0008 to Accepted;
4. add only genuinely proven runtime facts to `VERIFIED-FACTS.md`;
5. then proceed to MILESTONE-002.

If FAIL:

1. preserve the failure evidence;
2. determine whether the failure is GenericSource-specific or a test/config issue;
3. first fallback is the already-viable additive SpeakerPeripheral Mixin from ADR-0003;
4. only then consider forwarding/capability wrapping;
5. do not jump to a CC:T fork.

## Manual-test cadence

Do not ask for Minecraft launches after small patches. Keep CI/source/static checks frequent and accumulate user-only observations into the broad milestone gate.

## Explicit non-goals until GATE-001 passes

Do not start:

- real PCM/media playback;
- codecs;
- uploads/content hashing/cache;
- content transfer/network protocol;
- server media sessions;
- synchronization;
- SPR implementation;
- URL/live streaming;
- Valkyrien Skies support.
