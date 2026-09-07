# HighAudio repository guide

HighAudio extends normal CC:Tweaked speakers with high-quality finite-media playback. The fixed targets are Minecraft 1.21.1, Java 21, CC:Tweaked 1.120.0, NeoForge 21.1.247 (baseline) and 21.1.248 (compatibility); Sound Physics Remastered 1.21.1-1.5.1 is a later compatibility target.

## Read and decide

Start with [`docs/CURRENT-STATE.md`](docs/CURRENT-STATE.md). For conflicts, exact-stack runtime evidence and [`docs/VERIFIED-FACTS.md`](docs/VERIFIED-FACTS.md) outrank Accepted ADRs, which outrank [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), the [`docs/ROADMAP.md`](docs/ROADMAP.md), and historical prose. Preserve contradictory historical evidence; update or supersede the affected decision instead of rewriting history.

Repository map:

- [`docs/decisions/`](docs/decisions/) — decision records and statuses
- [`docs/PROTOTYPES.md`](docs/PROTOTYPES.md) and [`docs/test-batches/`](docs/test-batches/) — experiment ledger and retained evidence
- [`docs/RISKS.md`](docs/RISKS.md) — known risks and mitigations
- [`docs/TESTING.md`](docs/TESTING.md) — automated and batched-runtime testing policy
- [`docs/SOURCES.md`](docs/SOURCES.md) — provenance and dependency research

## Invariants

- Minecraft owns normal audio source, channel, device, and context lifecycle; do not add an independent HighAudio OpenAL engine.
- Keep CC:T implementation coupling localized under `integration/cct`.
- ADR-0004 and ADR-0009 are Accepted; ADR-0010 is Proposed.
- Preserve the total-preserving streaming reservation policy and existing EXP-001/002/003 diagnostics, fixtures, and evidence.
- Do not casually change target versions, dependencies, the accepted integration boundary, or historical `exp*` packages.
- Keep planned product architecture clearly distinct from implemented or experimentally validated code.

## Build and validation

Use `./gradlew build` (`gradlew.bat build` on Windows). CI builds and inspects the packaged mod on NeoForge 21.1.247 and 21.1.248 and runs the established automated client/server regression assertions. Follow [`docs/TESTING.md`](docs/TESTING.md); do not request a manual Minecraft session for documentation-only or behavior-neutral changes.
