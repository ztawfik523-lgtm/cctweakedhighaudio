# CC:Tweaked HighAudio

High-quality, controllable, synchronized media playback through normal CC:Tweaked speakers.

> **Status:** MILESTONE-000 through MILESTONE-003 are complete. The integration and Minecraft-owned audio foundation is validated; the first finite-media product vertical slice (M4) has not been implemented.

## Fixed target

- Minecraft **1.21.1**
- Java **21**
- CC:Tweaked **1.120.0** (exact source tag `v1.21.1-1.120.0`)
- NeoForge **21.1.247** baseline and **21.1.248** compatibility
- Sound Physics Remastered **1.21.1-1.5.1** as a later compatibility target

Do not silently substitute CC:Tweaked 1.120.2/current or another Minecraft/NeoForge line when making architecture claims.

## Where the project stands

HighAudio is a single NeoForge mod with CC:Tweaked required. It extends CC:T's existing speaker through a targeted `GenericSource`, keeping CC:T-specific coupling localized. Completed M1–M3 work proved that integration, Minecraft-owned arbitrary PCM playback/lifecycle, a total-preserving reservation policy reaching the 16-stream project target, and viable local start-timing primitives.

Minecraft remains responsible for normal sound source, channel, device, and context lifecycle. HighAudio does not own a separate OpenAL engine. The existing experiment diagnostics and regression checks remain packaged as retained evidence.

M4 is next but not started. It will be the first real finite-file path—bounded upload, content-addressed storage and transfer, WAV decoding, and positional playback through one normal placed speaker. Upload/content/network/cache/WAV/session product code is therefore planned, not a current feature.

Read [`docs/CURRENT-STATE.md`](docs/CURRENT-STATE.md) first, then use [`docs/README.md`](docs/README.md) for the documentation map. Design detail belongs in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), and implementation order belongs in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Build

The committed Gradle 9.2.1 wrapper requires Java 21:

```text
./gradlew build
```

On Windows:

```text
gradlew.bat build
```

GitHub Actions builds the packaged mod against NeoForge 21.1.247 and 21.1.248 and preserves the established package, client-initialization, dedicated-server, and M1–M3 regression assertions. Manual Minecraft testing is batched only when a runtime question requires it; see [`docs/TESTING.md`](docs/TESTING.md).

## Evidence discipline

Exact-stack runtime results and verified facts take precedence over Accepted ADRs, then the architecture and roadmap. Proposed decisions and old research are not facts. Significant experiment history remains traceable in [`docs/PROTOTYPES.md`](docs/PROTOTYPES.md) and [`docs/test-batches/`](docs/test-batches/).

Current scaffold identifiers are `cctweakedhighaudio` and `dev.ztawfik.cctweakedhighaudio`, with `All Rights Reserved` metadata. They remain provisional pending a future coordinated identity/license decision.
