# HighAudio current-chat migration

**Status:** CURRENT — read this first in the next chat  
**Prepared:** 2026-09-07  
**Project:** `ztawfik523-lgtm/cctweakedhighaudio`  
**Completed through:** MILESTONE-003 / GATE-003 PASSED  
**Next:** MILESTONE-004 — finite local media vertical slice

This file exists because the project crossed chat/UI boundaries. `docs/HANDOFF.md` now contains the detailed canonical state. This migration note is intentionally short and only identifies the current next action.

## Read first

1. `docs/HANDOFF.md`
2. `docs/TESTING.md`
3. `docs/decisions/ADR-0004-minecraft-owned-audio.md`
4. `docs/decisions/ADR-0009-rebalance-minecraft-streaming-reservation.md`
5. `docs/decisions/ADR-0010-playback-timing-intents.md`
6. `docs/ARCHITECTURE.md`
7. `docs/ROADMAP.md` — MILESTONE-004
8. relevant `docs/RISKS.md` entries before M4 implementation

## Fixed exact target

```text
Minecraft:       1.21.1
Java:            21
CC:Tweaked:      1.120.0
CC:T source tag: v1.21.1-1.120.0
NeoForge base:   21.1.247
Compatibility:   21.1.248
SPR later exact: 1.21.1-1.5.1
```

Do not silently use CC:T 1.120.2/current as exact-version proof.

## M0–M3 closure

- M0: complete — reproducible exact-stack build/CI baseline.
- M1: complete — targeted `GenericSource` speaker augmentation (`ADR-0008`).
- M2: complete — arbitrary PCM through Minecraft-owned `SoundInstance` / `AudioStream` / `Channel` lifecycle.
- M3: complete — real vanilla streaming reservation measured at 8; conservative total-preserving rebalance repeatedly achieved 16/16; real 2/4/8/16 synchronized vector start over Minecraft-owned sources passed.

Accepted architecture after M3:

- `ADR-0004`: **Accepted** — Minecraft owns normal source/channel/device lifecycle; no parallel HighAudio OpenAL engine.
- `ADR-0009`: **Accepted** — conservatively rebalance Minecraft's existing source reservation to permit up to 16 streaming slots on eligible layouts while preserving the combined total.
- `ADR-0010`: **Proposed** — public playback timing should be intent-driven: `immediate` default, `together` explicit local synchronized ASAP start, `scheduled` optional timeline/device-clock start.

The strengthened optional scheduled-start implementation/capability candidate at `ecb6c9d8a787184033f08082f888a0283b1d6ec5` passed CI `34121266402` on both NeoForge 21.1.247 and 21.1.248. It correctly distinguishes source-start time, media-zero renderer time, and device output latency. This is automatic feasibility evidence, **not** a claim that audible C/scheduled playback has been manually proven on the user's device.

No separate C-only Minecraft launch is required now. Validate scheduled timing later when the real session clock/media pipeline exists and one test can prove meaningful product behavior.

## Manual-test rule

The user explicitly wants minimum testing to mean minimum.

Do not ask for a Minecraft launch merely to reconfirm a mechanism already sufficient for the current architecture. Prefer exact source inspection, CI, bytecode/package checks, automated client initialization, and combined self-measuring probes. One manual launch should close a meaningful gate, not one implementation detail.

## Next work — MILESTONE-004

Goal: one normal placed CC:T speaker plays one real finite uploaded file end-to-end.

First vertical path:

```text
CC Lua file
  -> bounded begin/write/finish upload
  -> copy chunk bytes before leaving IArguments call scope
  -> SHA-256 ContentId over original file bytes
  -> bounded server ContentStore
  -> bounded/chunked server->client transfer
  -> compressed client cache
  -> WAV PCM decoder baseline
  -> Minecraft-owned HighAudio AudioStream
  -> positional normal computercraft:speaker
```

Before coding:

1. re-read exact CC:T 1.120.0 `IArguments`/Lua method threading/lifetime semantics;
2. re-read exact NeoForge 21.1.247/21.1.248 payload registration, `CustomPacketPayload`, `StreamCodec`, and packet-distribution APIs;
3. re-read ADR-0005/0006/0007 and relevant upload/network/cache risks;
4. choose conservative upload/content/payload bounds explicitly rather than accepting defaults by accident;
5. preserve CC:T implementation coupling under `integration/cct` only.

M4 should start with WAV PCM for correctness. Do not pull MP3, sync groups, moving speakers, URL/live streaming, or broad SPR compatibility into the first vertical slice.

## Process boundary

Do not restart old experiments or capacity-limit research. Do not build an independent OpenAL engine. Do not expose raw OpenAL clock/source details to Lua. Do not add production complexity that belongs to M5+ just to make M4 look feature-complete.
