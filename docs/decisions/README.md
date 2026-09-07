# Architecture decision index

**Status:** canonical ADR index  
**Last reviewed:** 2026-09-07  
**Search tags:** `ADR`, `DECISION`, `STATUS`, `CONSEQUENCES`

Architecture Decision Records document only choices which materially affect structure, important dependencies/interfaces, lifecycle, compatibility, or major quality goals.

Statuses follow a small Nygard-style vocabulary:

- `Proposed` — leading choice, not yet sufficiently validated/accepted.
- `Accepted` — current architecture decision.
- `Rejected` — evaluated but not selected.
- `Superseded` — once accepted/proposed, replaced by a later ADR.
- `Deprecated` — still present for compatibility but no longer preferred.

## Current decisions

| ADR | Status | Decision |
|---|---|---|
| [ADR-0001](ADR-0001-cc-t-required-single-mod.md) | Accepted | CC:T 1.120.0 is mandatory for v1; ship one CC:T-focused NeoForge mod rather than a standalone audio framework. |
| [ADR-0002](ADR-0002-localize-cct-internals.md) | Accepted | Keep CC:T implementation internals confined to a narrow integration layer. |
| [ADR-0003](ADR-0003-additive-speakerperipheral-mixin.md) | Superseded | Additive SpeakerPeripheral Mixin was automatically viable but replaced before the manual gate by ADR-0008. |
| [ADR-0004](ADR-0004-minecraft-owned-audio.md) | Accepted | Let Minecraft own SoundInstance/AudioStream/Channel; EXP-002 and EXP-003 validated the required playback, lifecycle, capacity, and synchronization boundary. |
| [ADR-0005](ADR-0005-server-authoritative-sessions.md) | Accepted | Server owns semantic playback state/timeline; clients own disposable rendering state. |
| [ADR-0006](ADR-0006-content-addressed-media.md) | Accepted | Finite media is identified by SHA-256 of original bytes and transferred/cached by ContentId. |
| [ADR-0007](ADR-0007-separate-content-and-session-protocol.md) | Accepted | Large content transfer and session/control state are separate protocol concerns. |
| [ADR-0008](ADR-0008-targeted-speaker-genericsource.md) | Accepted | Add HighAudio Lua methods through a GenericSource targeted at exact CC:T 1.120.0 SpeakerPeripheral; EXP-001/GATE-001 passed. |
| [ADR-0009](ADR-0009-rebalance-minecraft-streaming-reservation.md) | Accepted | Conservatively rebalance Minecraft's static/streaming reservation so up to 16 streaming slots are available on eligible layouts without raising the total source budget. |
| [ADR-0010](ADR-0010-playback-timing-intents.md) | Proposed | Keep immediate playback as the fastest default, retain measured vector `together` start, and add capability-gated device-clock `scheduled` start as distinct script/session intents. |

## Decisions deliberately not made yet

Do not invent ADR status for these until evidence/product choice exists:

- exact pause/freeze semantics of `MediaClock`;
- session behavior on block-speaker chunk unload;
- final emitter identity persistence mechanism;
- initial turtle/pocket support policy;
- exact public Lua method names/signatures for immediate/together/scheduled timing intents;
- final long-running drift correction policy;
- exact source/cache/content/upload limits;
- MP3 decoder dependency;
- server restart persistence;
- final mod id/package/license;
- HTTP/live streaming architecture.

## ADR update rule

If a completed `EXP-*` contradicts an ADR:

1. preserve the old ADR;
2. mark it `Rejected` or `Superseded`;
3. write the replacement ADR when the new choice is architecturally significant;
4. update `ARCHITECTURE.md`, `ROADMAP.md`, `RISKS.md`, and `VERIFIED-FACTS.md` as needed.

Do not rewrite history to make the original decision look correct.
