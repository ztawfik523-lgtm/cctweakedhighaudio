# Source, version, and provenance ledger

**Status:** canonical source/provenance index  
**Last reviewed:** 2026-09-07  
**Search tags:** `SOURCE`, `VERSION-PIN`, `LICENSE`, `PROVENANCE`, `THIRD-PARTY`

This file answers two questions:

1. Which external source should be consulted for a subsystem?
2. If code/algorithms are reused, what provenance/license obligations must be checked before merge?

Researching public behavior is not the same as copying implementation code. If HighAudio copies/adapts source, add a **file-level provenance entry** before merging it.

---

## Pinned platform sources

### SRC-CCT-001 — CC:Tweaked 1.120.0 for Minecraft 1.21.1

- Repository: https://github.com/cc-tweaked/CC-Tweaked
- Exact tag: https://github.com/cc-tweaked/CC-Tweaked/tree/v1.21.1-1.120.0
- Release: https://github.com/cc-tweaked/CC-Tweaked/releases/tag/v1.21.1-1.120.0
- Commit prefix: `98f3a71`
- Primary licenses: repository uses REUSE/SPDX with multiple licenses; implementation files inspected here commonly identify `MPL-2.0`, while API files may use CC's API license (`LicenseRef-CCPL`) or other declared licenses. **Read the SPDX header of any file before copying it.**
- Use: hard runtime dependency for v1; exact implementation research; minimal version-sensitive integration target.

High-value exact files:

- `SpeakerPeripheral.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral.java
- `SpeakerBlockEntity.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerBlockEntity.java
- `UpgradeSpeakerPeripheral.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/UpgradeSpeakerPeripheral.java
- `TurtleSpeaker.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/turtle/upgrades/TurtleSpeaker.java
- `PocketSpeakerPeripheral.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/pocket/peripherals/PocketSpeakerPeripheral.java
- `IPeripheral.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/core-api/src/main/java/dan200/computercraft/api/peripheral/IPeripheral.java
- `IArguments.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/core-api/src/main/java/dan200/computercraft/api/lua/IArguments.java
- `MethodSupplierImpl.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/core/src/main/java/dan200/computercraft/core/asm/MethodSupplierImpl.java
- `SpeakerInstance.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/client/java/dan200/computercraft/client/sound/SpeakerInstance.java
- `SpeakerSound.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/client/java/dan200/computercraft/client/sound/SpeakerSound.java
- `DfpwmStream.java`  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/client/java/dan200/computercraft/client/sound/DfpwmStream.java
- CC:T NeoForge version catalog  
  https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/gradle/libs.versions.toml

Maintainer clarification on generic peripherals + existing `IPeripheral`:

- https://github.com/cc-tweaked/CC-Tweaked/discussions/2491

CC:T API stability guidance:

- https://github.com/cc-tweaked/CC-Tweaked#using

**Provenance rule:** HighAudio may target internal class names through a minimal Mixin, but should not paste CC:T implementation code into core systems unless there is an explicit documented reason and SPDX/license review.

---

### SRC-NF-001 — NeoForge 1.21.1

- Documentation root: https://docs.neoforged.net/
- Networking payload docs: https://docs.neoforged.net/docs/networking/payload/
- Maven/version repository: https://maven.neoforged.net/releases/net/neoforged/neoforge/
- Target builds: `21.1.247`, `21.1.248`

Important concepts to verify in exact build during implementation:

- `RegisterPayloadHandlersEvent` / payload registration;
- client/server payload thread handoff;
- configuration/login compatibility negotiation;
- client sound events;
- block/entity lifecycle events used for emitter identity;
- capability registration behavior if the Mixin approach fails.

Useful 1.21.x Javadoc mirror for sound-event discovery:

- package: https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/client/event/sound/package-summary.html

**Caveat:** a generic 1.21.x Javadoc mirror is not substitute for compiling/running 21.1.247/248.

---

### SRC-MC-001 — Minecraft 1.21.1 sound classes

Important classes/mappings:

```text
net.minecraft.client.sounds.SoundEngine
net.minecraft.client.sounds.SoundManager
net.minecraft.client.sounds.AudioStream
net.minecraft.client.sounds.SoundBufferLibrary
com.mojang.blaze3d.audio.Channel
com.mojang.blaze3d.audio.ChannelAccess
com.mojang.blaze3d.audio.Library
```

Use legally available mappings/decompiled development source generated by the NeoForge toolchain for exact method/field behavior. Do not copy Mojang implementation source into documentation/code unnecessarily.

Important investigation targets:

- static vs streaming channel pools;
- Channel executor/thread requirements;
- AudioStream queue pumping;
- SoundEngine reload/device lifecycle;
- private OpenAL source handle access needed for EXP-003.

---

## Audio runtime sources

### SRC-AL-001 — OpenAL Soft

- Repository: https://github.com/kcat/openal-soft
- Core header: https://github.com/kcat/openal-soft/blob/master/include/AL/al.h
- `AL_SOFT_source_latency` extension source/docs: repository `include/AL/alext.h` and extension documentation
- Example player: https://github.com/kcat/openal-soft/blob/master/examples/alffplay.cpp
- License: LGPL-2.0-or-later for the library overall with project-specific exceptions/components; verify if redistributing code. HighAudio normally calls the runtime through LWJGL rather than bundling OpenAL Soft code.

Research use:

- vector `alSourcePlayv`/pause/stop;
- source offsets;
- queued streaming buffers;
- latency/device timing;
- extension discovery.

**Rule:** extension existence in OpenAL Soft source does not prove Minecraft's active device exposes it. Use runtime extension checks.

### SRC-LWJGL-001 — LWJGL

- Site: https://www.lwjgl.org/
- Javadocs: https://javadoc.lwjgl.org/
- STB Vorbis binding: https://javadoc.lwjgl.org/org/lwjgl/stb/STBVorbis.html

Use: OpenAL calls already available through Minecraft runtime; potential Ogg Vorbis decoding through stb_vorbis.

Check Minecraft-bundled LWJGL version before relying on newer binding methods.

---

## Sound Physics Remastered

### SRC-SPR-001

- Repository: https://github.com/henkelmax/sound-physics-remastered
- License: GPL-3.0
- Target behavior version: Minecraft 1.21.1 / SPR 1.5.1

Use:

- understand source discovery/interception;
- EFX/reverb/occlusion ownership;
- moving-source behavior;
- Simple Voice Chat integration pattern;
- lifecycle cleanup;
- source-count performance characteristics.

**Provenance rule:** do not copy GPL implementation into HighAudio unless the project intentionally adopts compatible licensing. Studying public behavior/architecture is not permission to paste source.

---

## Prior art: CC:HQ

### SRC-CCHQ-001

- Repository: https://github.com/tiktop101/CC-HQ-Speakers
- License observed in prior audit: MPL-2.0

Use only as prior art/reference for:

- file decoding approaches;
- packet/sync ideas;
- previous method surface;
- failures to avoid (one-shot dispatch, server/client playback truth mismatch, incomplete loop/pause/restoration semantics, duplicate per-speaker content).

**Rule:** HighAudio is not a CC:HQ fork by default. If a covered CC:HQ source file is copied/modified, record the file path/commit and keep applicable MPL obligations.

---

## Codec candidates

### SRC-CODEC-001 — Java Sound

- Java 21 API: https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/sound/sampled/package-summary.html

Use: simple sampled PCM/container support; useful WAV baseline. Do not assume installed MP3/Vorbis providers.

### SRC-CODEC-002 — stb_vorbis through LWJGL

- Javadoc: https://javadoc.lwjgl.org/org/lwjgl/stb/STBVorbis.html

Use: leading early Ogg Vorbis candidate due sample seek/length functions and existing LWJGL environment.

### SRC-CODEC-003 — JLayer (candidate only)

- Repository/distribution varies; verify current canonical upstream before dependency selection.
- Common license: LGPL-2.1.

Use: MP3 candidate only. `EXP-008` must decide whether seek/VBR/gapless behavior is sufficient and record exact artifact/source/license.

Do not add a production dependency entry here until exact coordinates/version are selected.

### SRC-CODEC-004 — Concentus (future Opus candidate)

Pure-Java Opus implementation; permissive license in common distributions. Verify canonical current repository/artifact before use.

Status: deferred.

---

## Documentation-method sources

### SRC-DOC-001 — arc42

- Overview: https://arc42.org/overview/
- Documentation/docs-as-code: https://arc42.org/documentation/
- Architecture decisions: https://docs.arc42.org/section-9/
- Risks/technical debt: https://docs.arc42.org/section-11/

HighAudio does not reproduce the full arc42 template. We borrow useful principles:

- put different architecture knowledge in predictable places;
- keep docs as Markdown in git;
- record only significant decisions;
- include rationale/status/consequences;
- make risks explicit;
- keep a glossary.

---

## File-level provenance table

Add rows here if implementation source is copied/adapted rather than independently written.

| HighAudio file | Source project/file | Exact source commit/tag | Source license | Nature of reuse | Required notices/actions |
|---|---|---|---|---|---|
| _none yet_ | | | | | |

## Dependency decision rule

Before adding any third-party runtime decoder/library:

1. record exact Maven coordinates/version/canonical repository;
2. record license;
3. confirm Java 21 + NeoForge packaging behavior;
4. confirm dedicated server does not load client/native-only code accidentally;
5. confirm seek/duration/malformed-input behavior through a prototype where applicable;
6. update this ledger and the relevant ADR/risk.
