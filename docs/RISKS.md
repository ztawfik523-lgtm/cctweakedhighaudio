# Architecture and implementation risks

**Status:** canonical risk ledger  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `RISK`, `SEVERITY`, `MITIGATION`, `TECH-DEBT`

Severity and likelihood are preliminary until prototypes produce data.

| ID | Risk | Severity | Likelihood | Primary mitigation |
|---|---|---:|---:|---|
| RISK-001 | HighAudio targets CC:T internal `SpeakerPeripheral`; exact implementation may change | High | Medium | EXP-001; isolate all CC:T internals; exact version pin |
| RISK-002 | Base `SpeakerPeripheral` target also exposes methods on turtle/pocket speakers | Medium | High | EXP-001; explicit emitter-kind gating/product choice |
| RISK-003 | Minecraft-owned `AudioStream` lifecycle cannot provide enough control for pause/seek/sync | High | Medium | EXP-002/003; accepted narrow timing access; keep production controls session-driven |
| RISK-004 | Minecraft streaming/static channel pools cap HighAudio earlier than expected | High | Medium | Measured 8-stream baseline; accepted total-preserving 16-stream rebalance; retain graceful limits |
| RISK-005 | Tight group synchronization cannot be achieved without breaking Minecraft/SPR lifecycle | High | Medium | M3 vector-start evidence; avoid generalizing initial-start proof to production drift/SPR |
| RISK-006 | Emitter identity cannot distinguish unload/reload from block destruction/replacement cleanly | Medium | Medium | EXP-004; avoid using native random source UUID as durable identity |
| RISK-007 | Media clock semantics around pause/TPS freeze feel wrong or desynchronize clients | High | Medium | `MediaClock` abstraction; EXP-005; deliberate product choice |
| RISK-008 | SPR performance or EFX ownership conflicts with many HighAudio sources | High | Medium | Minecraft-owned sources; exact-version SPR milestone; source budget benchmark |
| RISK-009 | Lua→Java uploads create excessive copying/memory pressure/computer stalls | Medium | Medium | bounded upload sessions; EXP-006; server quotas |
| RISK-010 | Decoded cache/stream queues explode memory with long files or many unique contents | High | High | budget-driven static/streaming policy; LRU/refcounts; EXP-007/009 |
| RISK-011 | MP3 decoder seek/duration/gapless behavior is not accurate enough for sample-frame timeline | Medium | Medium | defer MP3 until EXP-008; codec-neutral interface |
| RISK-012 | One slow client consumes server network memory or delays others | High | Medium | per-client bounded transfer scheduler; cancellation; no global blocking |
| RISK-013 | Malicious Lua/media input causes allocation/decode/network abuse | High | Medium | quotas, validation, decode limits/timeouts, bounded queues |
| RISK-014 | F3+T/device reload destroys local audio state and causes stale resurrection/leaks | High | Medium | server-authoritative session mirror; EXP-002; rebuild from revision/snapshot |
| RISK-015 | Exact NeoForge 21.1.247/248 APIs differ from nearby docs/source assumptions | Medium | Low–Medium | compile/runtime matrix on both; don't publish version range before proof |
| RISK-016 | License/provenance contamination from CC:HQ/SPR/copied code | High | Low if disciplined | original implementation; `SOURCES.md`; file-level provenance if copied |
| RISK-017 | HTTP/live streaming scope overwhelms finite-media architecture | Medium | High if started early | defer; separate live-stream architecture track |
| RISK-018 | Native CC:T speaker behavior and HighAudio playback fight over one physical peripheral | Medium | Medium | define concurrency/stop semantics deliberately; regression tests |
| RISK-019 | Session ownership between multiple attached computers is ambiguous | Medium | High | explicit cooperative/owner/session-token semantics before public API freeze |
| RISK-020 | Documentation becomes stale and old research is mistaken for truth | High | Medium | stable IDs, ADR statuses, experiment ledger, update rules |
| RISK-021 | CC:T `disabled_generic_methods` can disable HighAudio's GenericSource method | Medium | Low–Medium | document source id; detect/explain missing method; gate under default config |

---

## RISK-001 — CC:T internal SpeakerPeripheral coupling

**Problem:** `SpeakerPeripheral` is outside `dan200.computercraft.api`. CC:T explicitly warns non-API classes may change.

The current `GenericSource` registration API itself is public, but the method target type is the exact internal `SpeakerPeripheral` class. HighAudio therefore still has deliberate version-sensitive implementation coupling even though it no longer transforms CC:T bytecode.

**Why acceptable for current target:** the project is intentionally pinned to CC:T 1.120.0 and wants to enhance the actual normal speaker rather than create a parallel block. Targeting the real speaker class through CC:T's method supplier is less invasive than replacing peripheral identity/capabilities.

**Mitigation:**

- keep all `dan200.computercraft.shared.*` / `dan200.computercraft.core.*` imports inside `integration/cct`;
- run EXP-001 before production media code;
- exact-version CI/startup matrix;
- fail clearly on unsupported/broken CC:T versions;
- never let decoder/session/network/client code import CC:T internals;
- keep the superseded additive Mixin as a documented fallback, not a hidden second path.

---

## RISK-002 — base target affects upgrade speakers

`UpgradeSpeakerPeripheral` derives from `SpeakerPeripheral`; turtle/pocket speaker implementations derive from it. A GenericSource method targeted at the base type may therefore appear on them too.

Possible product policies after exposure is observed:

1. **Support all emitter types eventually:** clean architecture, more early work.
2. **Expose methods but reject unsupported emitter kinds initially:** fastest block-speaker vertical slice, but API exists before feature support.
3. **Move to a more specific block-only target/integration:** narrow initial behavior, less reusable for moving emitters.

This is a meaningful product/implementation tradeoff. Do not silently choose it inside EXP-001; first observe exact runtime behavior.

---

## RISK-003 — insufficient control through Minecraft SoundEngine

Minecraft ownership is preferred for lifecycle/category/acoustics, but HighAudio needs more than simple play/stop:

- seek;
- pause/resume;
- exact group start;
- position measurement;
- queued streaming control.

EXP-003 established that the renderer boundary does **not** require a second independent OpenAL source manager for the current capacity/initial-start questions. Keep Minecraft source/device lifecycle ownership and use only the narrow timing/measurement access already justified by evidence. Production pause/seek/session reconstruction still needs to be proven in its later authoritative-session milestones rather than inferred from the M3 start primitives.

---

## RISK-004 — channel pool exhaustion

Do not hardcode “32 sources”. Minecraft has separate static/streaming channel pools and other sounds compete with HighAudio.

EXP-003 measured the vanilla streaming reservation at 8 on the baseline runtime and repeatedly proved 16/16 after the accepted conservative rebalance, while preserving the measured combined source budget and observing static-side Minecraft activity during streaming saturation.

**Mitigation:** retain the accepted total-preserving reservation policy, do not generalize the measured 255-source layout to every device, keep graceful refusal/voice policy for resource pressure, and drive later static-vs-streaming decisions from real content/cache/source budgets. Exact SPR performance remains a later dedicated gate.

---

## RISK-005 — sync promise exceeds implementation

A common server tick is not sufficient evidence of perceptually tight sync. Network delivery, decoder readiness, Minecraft Channel creation, OpenAL queue state, device latency, underruns, and long-running renderer drift all matter.

EXP-003 now provides objective initial-start evidence: ordinary/high-level and synchronized vector starts were measured through 16 already-ready synthetic streams, and the vector primitive is viable over Minecraft-owned sources. That closes the M3 architecture question but is not a production sync-quality guarantee for asynchronous real media or long-running playback.

**Mitigation:** keep `immediate` and explicit `together` semantics distinct; preserve the server/session timeline independently from local OpenAL offsets; validate production readiness barriers, scheduled mapping if retained, drift/underrun correction, and exact SPR behavior in their later session/synchronization milestones before making broader sync-quality promises.

---

## RISK-006 — emitter identity ambiguity

Coordinate-only IDs conflate break/re-place with reload. Native CC:T source UUID is created with the peripheral instance and is not proven persistent.

**Mitigation:** instrument actual lifecycle first. Only add persisted block data if needed.

---

## RISK-007 — pause/freeze semantics

Real-time media may continue while simulation is paused; game-time media may slow with TPS. Both can surprise users.

**Mitigation:** `MediaClock` interface and explicit product choice after EXP-005. Server timeline representation remains sample-frame based either way.

---

## RISK-008 — Sound Physics cost/ownership

SPR may perform expensive acoustics work per source and owns/changes EFX state. A HighAudio source-count policy that is fine without SPR may be unusable with it.

**Mitigation:** preserve Minecraft source lifecycle, benchmark SPR exact target, never double-own EFX without an explicit integration agreement.

---

## RISK-009 — Lua upload pressure

Large binary strings can create several temporary copies if API design is careless.

**Mitigation:** upload sessions, bounded chunks, immediate copy once, hash/store incrementally, per-computer/per-server quotas, benchmark sizes rather than selecting one arbitrarily.

---

## RISK-010 — memory blow-up

At mono 48 kHz 16-bit, decoded PCM is about 96 kB/s before overhead. A one-hour track is hundreds of MB.

**Mitigation:** never universally full-decode; ring/segment streaming for long content, shared compressed object, bounded decoded cache, refcount + eviction.

---

## RISK-011 — MP3 timeline accuracy

Frame-oriented MP3 libraries may provide approximate seeks while HighAudio wants sample-frame semantic position and consistent loop/resume behavior.

**Mitigation:** keep MP3 out of the first audio vertical slice; prove decoder behavior against a test corpus.

---

## RISK-012 — slow-client backpressure

Minecraft networking is reliable, but generating queued chunks faster than one connection can send can consume server memory.

**Mitigation:** bounded per-client pending bytes, transfer cancellation/replacement, no global decode/transfer lock, prefetch only for relevant audience.

---

## RISK-013 — hostile media/resource exhaustion

Inputs originate from programmable computers and may be intentionally abusive.

Validate/limit:

- compressed size;
- decoded frame count/duration;
- channel count/sample rate;
- upload concurrency;
- content count/cache bytes;
- session count;
- decoder workers;
- packet reassembly;
- malformed/decode time;
- rapid start/stop churn;
- URL access if ever added.

Exact numbers remain provisional until performance tests.

---

## RISK-014 — reload resurrection/leak

Client audio state can disappear independently of server semantic state during F3+T/device/world changes.

**Mitigation:** local sources are disposable renderers. A `SessionSnapshot`/revision reconstructs or confirms absence. Stop revisions/tombstones prevent stale revival.

---

## RISK-015 — patch-version assumption

Documentation for NeoForge 1.21.x may describe APIs that changed in details by 21.1.247/248.

**Mitigation:** compile against the chosen baseline and run both exact builds before support claims.

---

## RISK-016 — licensing/provenance

The old CC:HQ project and SPR are useful prior art but license obligations differ.

**Mitigation:** write HighAudio architecture/code independently unless there is a reason to reuse source. Record any copied/adapted file/algorithm in `SOURCES.md` before merging it.

---

## RISK-018 — native CC:T/HighAudio concurrency

A speaker may receive native `playAudio` while a HighAudio session is active. Possible policies:

- native call interrupts HighAudio;
- HighAudio rejects while native sound is active;
- independent simultaneous renderers;
- explicit mode ownership.

This affects user expectations and resource use. Must be resolved before public API freeze.

---

## RISK-019 — multiple computers controlling one speaker

A wired network may expose one speaker to several computers. Ownership choices include:

- cooperative last-writer control;
- session IDs usable by any attached computer;
- creator-only control token;
- explicit exclusive lock.

Each has compatibility/usability tradeoffs. Defer final choice until the basic session model exists, then document it with an ADR.

---

## RISK-021 — GenericSource may be administratively disabled

CC:T's `disabled_generic_methods` setting can disable a whole GenericSource id or an individual method. The current source id is intended to be `cctweakedhighaudio:speaker`.

This is different from a runtime integration failure: an administrator may deliberately choose to suppress the method.

**Mitigation:**

- document the source id and method name;
- run GATE-001 under normal/default configuration;
- when diagnosing a missing `highAudioProbe`, distinguish configuration-disable from registration/method-discovery failure;
- if ADR-0008 is accepted, make this behavior visible in user/admin documentation rather than attempting to bypass CC:T's control.
