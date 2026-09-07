# HighAudio current handoff

**Status:** canonical chat/session handoff  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Prepared:** 2026-09-07  
**Next milestone:** `MILESTONE-001` / `EXP-001`  
**Search tags:** `HANDOFF`, `CURRENT-STATE`, `MILESTONE-001`, `EXP-001`

This file exists so a new engineering chat can recover the project state without relying on previous chat history. It is deliberately concise and points to the canonical evidence elsewhere in the repository.

## Authority rule

Do **not** treat this handoff as more authoritative than exact evidence.

Use this order when anything conflicts:

1. completed exact-version prototype/runtime result;
2. `VERIFIED-FACTS.md`;
3. accepted ADRs in `decisions/`;
4. `ARCHITECTURE.md`;
5. `ROADMAP.md`;
6. this handoff;
7. old chat/research prose.

Before editing code, read:

1. [`VERIFIED-FACTS.md`](VERIFIED-FACTS.md)
2. [`decisions/ADR-0002-localize-cct-internals.md`](decisions/ADR-0002-localize-cct-internals.md)
3. [`decisions/ADR-0003-additive-speakerperipheral-mixin.md`](decisions/ADR-0003-additive-speakerperipheral-mixin.md)
4. [`PROTOTYPES.md`](PROTOTYPES.md), especially `EXP-001`
5. [`ROADMAP.md`](ROADMAP.md), `MILESTONE-001`
6. [`TESTING.md`](TESTING.md)
7. [`RISKS.md`](RISKS.md) entries related to CC:T internals/peripheral identity.

## Fixed project target

Do not silently substitute newer versions:

```text
Minecraft:     1.21.1
Java:          21
CC:Tweaked:    1.120.0
CC:T source:   tag v1.21.1-1.120.0
NeoForge:      21.1.247 baseline
Compatibility: 21.1.248 also checked
SPR future:    1.21.1-1.5.1
```

CC:T 1.120.2/current `main` may be used only as clearly labelled comparison, never as proof of 1.120.0 behavior.

## Product/architecture baseline

Current accepted direction:

- one NeoForge mod;
- CC:Tweaked is a required dependency for v1;
- the user-facing device remains the normal `computercraft:speaker`;
- HighAudio owns media/session/network/cache/decoder/synchronization/client playback systems;
- CC:T implementation coupling stays localized in a narrow integration layer;
- do not build a standalone computer/audio ecosystem;
- do not copy CC:HQ architecture as the design baseline.

Still **proposed, not accepted**:

- additive Mixin augmentation of CC:T `SpeakerPeripheral` (`ADR-0003`, must pass `EXP-001`);
- Minecraft-owned streaming playback backend (`ADR-0004`, later `EXP-002`);
- exact local multi-source synchronization mechanism (`EXP-003`).

## MILESTONE-000 status

`MILESTONE-000` is complete and has been re-audited.

The re-audit rechecked the bootstrap against the exact CC:T 1.120.0 release and official NeoForge 1.21.1 NeoGradle MDK conventions. No bad version pin or malformed dependency metadata was found.

Corrections/improvements made during the re-audit:

- committed standard Gradle 9.2.1 wrapper (`gradlew`, `gradlew.bat`, wrapper properties/JAR);
- local builds no longer require a separately installed Gradle;
- CI runs through the committed wrapper;
- wrapper JAR checksum is verified before execution;
- Gradle distribution checksum is pinned in wrapper configuration;
- CI builds both NeoForge 21.1.247 and 21.1.248;
- CI inspects the finished JAR, rejects unexpanded metadata placeholders, confirms exact CC:T 1.120.0 and Minecraft 1.21.1 requirements, confirms the declared NeoForge range, and confirms `HighAudio.class` is packaged;
- temporary wrapper-bootstrap workflow was removed after generation;
- docs were updated to record the re-audit evidence.

Validated build-relevant commit from the re-audit:

```text
fb754659c4f2a6abe918c5fa7fa6dcad35be9bd0
```

Recorded green CI run:

```text
34075809793
```

Both NeoForge matrix legs passed. MILESTONE-000 required **zero manual Minecraft launches**.

## Testing cadence — important user preference

Do not ask for Minecraft runtime tests after every small patch.

Default workflow:

1. make several related implementation edits;
2. keep compile/CI checks green automatically;
3. accumulate runtime observations into one instrumented checklist;
4. ask for one broader user-run test at a milestone gate or when runtime evidence is architecture-blocking;
5. record it as `TEST-BATCH-*` in the repo.

Prefer fewer, broader manual tests. Automatic compilation/CI/source inspection can run frequently.

## Next task: MILESTONE-001 / EXP-001

Purpose: prove or reject the proposed additive `SpeakerPeripheral` Mixin on the exact assembled target stack **without implementing media playback**.

The first implementation should expose only a diagnostic Lua method conceptually like:

```lua
speaker.highAudioProbe()
```

The exact return shape may be improved, but keep the probe intentionally small and useful for diagnostics.

### Required proof goals

The consolidated runtime gate should establish:

- HighAudio method is visible/callable on intended normal CC:T speaker peripherals;
- original CC:T methods remain present;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain behaviorally intact;
- directly adjacent computer attachment works;
- wired modem/network wrapping works;
- attach/detach does not create duplicate identity/state;
- chunk unload/reload does not break method exposure;
- block break/re-place behavior is understood;
- whether turtle/pocket speaker peripherals also inherit the method is observed rather than guessed;
- no Mixin application/startup failure on NeoForge 21.1.247 or 21.1.248.

### Implementation constraints

- Do **not** build upload/content/session/audio systems in MILESTONE-001.
- Do **not** turn `ADR-0003` to Accepted before the runtime gate passes.
- Do **not** replace CC:T's whole peripheral unless the additive path fails and alternatives are evaluated.
- Keep any use of non-API CC:T classes inside the integration package/mixin surface.
- Instrument the probe so one user test can answer several lifecycle/identity questions at once.
- Build/test artifacts must identify exact commit and NeoForge version.

### If the additive Mixin fails

Follow the fallback order from `ROADMAP.md` rather than improvising a fork:

1. more specific additive Mixin target;
2. forwarding/`IDynamicPeripheral` strategy if identity and native behavior can be preserved;
3. capability wrapping only after recursion/invalidation/equality behavior is explicitly proven.

Do not jump directly to a CC:T fork.

## What the new chat should do first

1. Fetch/read current repository `main` and this file.
2. Confirm `MILESTONE-000` re-audit changes are on `main` and CI is green.
3. Re-read exact CC:T 1.120.0 `SpeakerPeripheral`, its inheritance/implementations, and Lua method discovery before writing the Mixin.
4. Re-check the relevant NeoForge/Sponge Mixin configuration format for this exact stack.
5. Create a MILESTONE-001 implementation branch.
6. Implement the smallest instrumented `EXP-001` probe.
7. Let CI build both NeoForge versions.
8. Accumulate one consolidated runtime `TEST-BATCH-001`; do not ask for repeated manual launches for minor edits.
9. Record results in `PROTOTYPES.md` and accept/reject/supersede `ADR-0003` only from evidence.

## Explicit non-goals for the next chat

Do not start these during MILESTONE-001:

- real PCM/media playback;
- codecs;
- content hashing/cache;
- media transfer protocol;
- server authoritative sessions;
- synchronization groups;
- SPR integration implementation;
- URL/live streaming;
- Valkyrien Skies support.

Those follow later roadmap gates.
