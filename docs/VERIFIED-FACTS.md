# Verified facts ledger

**Status:** canonical exact-version fact source  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `FACT-CCT`, `FACT-NF`, `FACT-MC`, `FACT-AL`, `FACT-SPR`, `FACT-CODEC`

This file contains **facts, not preferences**. Architecture choices belong in ADRs. Runtime assumptions which still need proof belong in `PROTOTYPES.md`.

## CC:Tweaked

### FACT-CCT-001 — exact release pin [VERIFIED]

CC:Tweaked has a Minecraft 1.21.1 release/tag `v1.21.1-1.120.0`, commit prefix `98f3a71`.

Source: https://github.com/cc-tweaked/CC-Tweaked/releases/tag/v1.21.1-1.120.0  
Exact tree: https://github.com/cc-tweaked/CC-Tweaked/tree/v1.21.1-1.120.0

**Implication:** all CC:T implementation claims for this project must be checked against this tag unless explicitly marked comparative.

### FACT-CCT-002 — public API boundary [VERIFIED]

CC:T's own README tells integrations to use classes within `dan200.computercraft.api`; non-API classes may change at any point.

Source: https://github.com/cc-tweaked/CC-Tweaked#using

**Implication:** any Mixin/reference to `dan200.computercraft.shared.*` is deliberate version-sensitive integration debt and must stay localized.

### FACT-CCT-003 — normal block speaker owns a specific peripheral [VERIFIED]

At `v1.21.1-1.120.0`, `SpeakerBlockEntity` constructs a private `SpeakerPeripheral` implementation and `peripheral()` returns it.

Source: https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerBlockEntity.java

**Implication:** the normal speaker is not an empty generic-peripheral target.

### FACT-CCT-004 — generic peripheral methods are not a simple merge onto an existing IPeripheral [VERIFIED / maintainer clarification]

A CC:T maintainer answer states that when a block already has a specific `IPeripheral`, generic peripheral methods cannot simply be used to add methods to that peripheral.

Source: https://github.com/cc-tweaked/CC-Tweaked/discussions/2491

**Implication:** `GenericPeripheral` is not the primary solution for adding HighAudio methods to the existing normal speaker.

### FACT-CCT-005 — SpeakerPeripheral core behavior [VERIFIED]

At the pinned tag, `SpeakerPeripheral`:

- is an abstract `IPeripheral`;
- defines `SAMPLE_RATE = 48000`;
- creates a private random UUID source on peripheral construction;
- owns an `AttachedComputerSet`;
- exposes native `playNote`, `playSound`, `playAudio`, and stop behavior;
- sends native client speaker packets from server update logic;
- marks native `playAudio` as `@LuaFunction(unsafe = true)`;
- limits one native `playAudio` table to 128 × 1024 samples.

Source: https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/SpeakerPeripheral.java

**Implication:** HighAudio must not steal or redefine native speaker semantics accidentally. The random native source UUID is not automatically a persistent physical-emitter identity.

### FACT-CCT-006 — peripheral method/lifecycle threading [VERIFIED]

`IPeripheral` documentation states peripheral methods run on the computer thread by default. `attach` and `detach` may be called from both the server thread and the ComputerCraft Lua thread and therefore must be thread-safe/reentrant.

Source: https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/core-api/src/main/java/dan200/computercraft/api/peripheral/IPeripheral.java

**Implication:** HighAudio cannot mutate world/session state from arbitrary Lua/lifecycle callbacks without an explicit ownership/handoff design.

### FACT-CCT-007 — Lua byte arguments may have scoped/zero-copy lifetime [VERIFIED]

`IArguments.getBytes()` returns a read-only `ByteBuffer`. `IArguments` documents that some implementations may expose zero-copy views whose lifetime is limited to the call; `escapes()` exists for arguments which outlive the call.

Source: https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/core-api/src/main/java/dan200/computercraft/api/lua/IArguments.java

**Implication:** bounded media upload bytes should be copied into HighAudio-owned storage before asynchronous hashing/decoding/transfer.

### FACT-CCT-008 — no public arbitrary CC filesystem read through IComputerAccess [VERIFIED]

The public `IComputerAccess` API exposes computer identity, attachment name, event queueing, peripheral inspection, and mount/unmount operations. It does not expose an API for a peripheral to open an arbitrary path from the computer's own virtual filesystem.

Source: https://tweaked.cc/javadoc/1.120.0/dan200/computercraft/api/peripheral/IComputerAccess.html

**Implication:** ergonomic `playFile(path)` behavior belongs in a Lua helper which reads the file and uploads bytes; Java must not pretend the path is directly readable.

### FACT-CCT-009 — runtime method discovery scans public @LuaFunction methods [VERIFIED]

At the pinned source, `MethodSupplierImpl` obtains `klass.getMethods()`, finds methods annotated with `@LuaFunction`, and generates callable methods from them.

Source: https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/core/src/main/java/dan200/computercraft/core/asm/MethodSupplierImpl.java

**Implication:** if an additive Mixin truly merges a public `@LuaFunction` method into the runtime `SpeakerPeripheral` class, CC:T's method discovery has a strong source-level reason to see it. `EXP-001` still verifies the real assembled mod.

### FACT-CCT-010 — turtle and pocket speakers share SpeakerPeripheral ancestry [VERIFIED]

`UpgradeSpeakerPeripheral extends SpeakerPeripheral`; the turtle speaker's internal peripheral extends `UpgradeSpeakerPeripheral`, and `PocketSpeakerPeripheral` also extends it.

Sources:

- https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/peripheral/speaker/UpgradeSpeakerPeripheral.java
- https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/turtle/upgrades/TurtleSpeaker.java
- https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/main/java/dan200/computercraft/shared/pocket/peripherals/PocketSpeakerPeripheral.java

**Implication:** a base `SpeakerPeripheral` Mixin may expose HighAudio Lua methods on block, turtle, and pocket speakers. Initial support behavior must be deliberate.

### FACT-CCT-011 — CC:T already uses Minecraft-owned custom streaming audio [VERIFIED]

At the pinned tag, CC:T client speaker audio creates a custom `SpeakerSound`, plays it through Minecraft's `SoundManager`, and provides a custom `AudioStream` (`DfpwmStream`) which feeds PCM to a Minecraft `Channel`.

Sources:

- https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/client/java/dan200/computercraft/client/sound/SpeakerInstance.java
- https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/client/java/dan200/computercraft/client/sound/SpeakerSound.java
- https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/client/java/dan200/computercraft/client/sound/DfpwmStream.java

**Implication:** Minecraft-owned `SoundInstance` + `AudioStream` is a proven viable integration pattern for arbitrary CC speaker PCM on this exact CC:T/Minecraft line.

## Mixin

### FACT-MIXIN-001 — Mixin merges new methods and annotations [VERIFIED concept]

Sponge Mixin's applicator merges normal mixin methods into the target class, and its annotation utilities merge method annotations (excluding Mixin's own special annotations where appropriate).

Sources:

- https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/mixin/transformer/MixinApplicatorStandard.java
- https://github.com/SpongePowered/Mixin/blob/master/src/main/java/org/spongepowered/asm/util/Annotations.java

**Implication:** the proposed additive `@LuaFunction` Mixin is technically plausible. Exact CC:T + NeoForge runtime validation remains `EXP-001`; this fact does not replace the experiment.

## NeoForge

### FACT-NF-001 — custom payload size limits [VERIFIED]

NeoForge networking documentation states custom payloads sent to clients may contain at most **1 MiB**, while payloads sent to the server must contain **less than 32 KiB**.

Source: https://docs.neoforged.net/docs/networking/payload/

**Implication:** arbitrary songs need chunked content transport; one giant media packet is invalid architecture.

### FACT-NF-002 — CC Lua upload is not a Minecraft C2S payload [VERIFIED architectural context]

A CC:T peripheral Lua call executes on the logical server/computer side. Passing bytes from a CC computer program to its attached Java peripheral is not equivalent to sending a Minecraft client custom payload to the server.

Evidence: CC:T peripheral execution model in `IPeripheral`/Lua APIs plus NeoForge payload direction rules.

**Implication:** NeoForge's <32 KiB serverbound payload ceiling does not define the low-level Lua→Java upload chunk size, though Lua/computer memory and call overhead still do.

### FACT-NF-003 — client sound events exist [VERIFIED API concept]

NeoForge 1.21.x exposes `PlaySoundEvent`, `PlaySoundSourceEvent`, `PlayStreamingSourceEvent`, and `SoundEngineLoadEvent`. `PlayStreamingSourceEvent` is fired when a streaming sound is being played and provides source-event context including the Minecraft audio channel. `SoundEngineLoadEvent` fires when the sound engine is constructed/reloaded, including output-device changes.

Reference Javadocs: https://nekoyue.github.io/ForgeJavaDocs-NG/javadoc/1.21.x-neoforge/net/neoforged/neoforge/client/event/sound/package-summary.html

**Caveat:** this public Javadoc mirror is not specifically generated from NeoForge 21.1.247. `EXP-002` must compile/run the exact target builds.

**Implication:** HighAudio should first try official sound events to capture/rebuild Minecraft-owned channels before adding broad SoundEngine Mixins.

### FACT-NF-004 — CC:T 1.120.0 was built against an older NeoForge [VERIFIED]

The pinned CC:T 1.120.0 version catalog sets its NeoForge version to `21.1.9`.

Source: https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/gradle/libs.versions.toml

**Implication:** CC:T's own build version is not our desired NeoForge floor. HighAudio must compile/runtime-test 21.1.247 and 21.1.248 explicitly instead of assuming patch-level compatibility.

## Minecraft/OpenAL

### FACT-MC-001 — CC:T positional speaker sound uses Minecraft sound semantics [VERIFIED]

Pinned CC:T's `SpeakerSound` extends `AbstractSoundInstance`, uses `SoundSource.RECORDS`, linear attenuation, and updates its position when bound to a moving entity.

Source: https://github.com/cc-tweaked/CC-Tweaked/blob/v1.21.1-1.120.0/projects/common/src/client/java/dan200/computercraft/client/sound/SpeakerSound.java

**Implication:** HighAudio can preserve Minecraft category/position lifecycle while replacing DFPWM with higher-quality PCM.

### FACT-MC-002 — Minecraft has distinct static and streaming channel pools [VERIFIED concept]

Minecraft's `Library` manages distinct static and streaming channel pools.

Reference mapping/source research: Minecraft 1.21.1 `com.mojang.blaze3d.audio.Library`.

**Implication:** raw OpenAL hardware source count is not by itself the HighAudio source budget. `EXP-003` must measure actual streaming/static acquisition behavior on the target runtime.

### FACT-AL-001 — OpenAL supports vector source start/pause [VERIFIED]

OpenAL exposes vector source operations including `alSourcePlayv` and `alSourcePausev`; vector play is defined to play a list of sources together as one API operation.

Source: https://github.com/kcat/openal-soft/blob/master/include/AL/al.h

**Implication:** atomic multi-source start is a promising primitive for tight local sync if Minecraft-owned channels can be safely prepared and their source IDs accessed.

### FACT-AL-002 — OpenAL source offsets exist [VERIFIED]

OpenAL exposes `AL_SEC_OFFSET` and `AL_SAMPLE_OFFSET`. OpenAL Soft implements sample-offset behavior for streaming sources relative to queued playback state.

Sources:

- https://github.com/kcat/openal-soft/blob/master/include/AL/al.h
- https://github.com/kcat/openal-soft

**Implication:** source offsets can measure renderer position, but they do not automatically define HighAudio's server-authoritative timeline.

### FACT-AL-003 — audible position may include latency beyond raw offset [VERIFIED reference implementation pattern]

OpenAL Soft's example player uses source offset/queued-buffer timing together with latency/device timing when estimating what is currently being heard.

Source: https://github.com/kcat/openal-soft/blob/master/examples/alffplay.cpp

**Implication:** synchronization/drift logic must not equate one raw OpenAL offset query with perfect audible global time.

## Spatial audio

### FACT-MC-003 — one positional OpenAL-style emitter should be mono [VERIFIED platform behavior]

Minecraft/NeoForge sound documentation describes stereo sounds as non-positional/listener-relative; positional attenuation behavior is intended for mono sound data.

Reference: https://docs.neoforged.net/docs/resources/client/sounds/

**Implication:** HighAudio should downmix stereo/multichannel input to mono for a single physical positional speaker unless an explicit multi-emitter stereo feature is designed.

## Sound Physics Remastered

### FACT-SPR-001 — SPR repository license [VERIFIED]

The current `henkelmax/sound-physics-remastered` repository is GPL-3.0 licensed.

Source: https://github.com/henkelmax/sound-physics-remastered

**Implication:** behavior/source can be studied, but copying GPL implementation into an incompatible differently licensed HighAudio codebase has licensing consequences. Track provenance.

### FACT-SPR-002 — many simultaneous sources can be expensive with SPR [VERIFIED reported behavior]

SPR has user reports of substantial performance degradation with many simultaneous sources on Minecraft 1.21.1 / SPR 1.5.1-class environments.

Issue tracker: https://github.com/henkelmax/sound-physics-remastered/issues

**Implication:** source-count defaults must be measured with acoustics enabled, not derived solely from OpenAL capacity.

## Codec facts

### FACT-CODEC-001 — Java Sound is not a complete MP3/Vorbis solution [VERIFIED platform limitation]

Java Sound provides standard sampled-audio support such as PCM container formats but does not make MP3/Vorbis decoding a guaranteed built-in Java 21 capability suitable as HighAudio's universal decoder plan.

Reference: https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/sound/sampled/package-summary.html

**Implication:** codec-specific decoder dependencies are required beyond simple WAV/PCM.

### FACT-CODEC-002 — LWJGL STB Vorbis exposes sample-oriented seek/length APIs [VERIFIED library capability]

LWJGL bindings for stb_vorbis expose Vorbis decoding and sample-seek/length functions.

Reference: https://javadoc.lwjgl.org/org/lwjgl/stb/STBVorbis.html

**Implication:** Ogg Vorbis is a strong early codec for a sample-frame-based `DecoderSession`.

## Facts intentionally NOT claimed

The following are **not verified facts** and must not appear elsewhere as if they were:

- HighAudio can definitely support 32 active sources.
- 16 simultaneous long streaming speakers are guaranteed by Minecraft.
- the proposed `SpeakerPeripheral` Mixin works in the assembled target mod.
- `PlayStreamingSourceEvent` alone gives every low-level OpenAL control we need on 21.1.247/248.
- `alSourcePlayv` can be used after Minecraft/SPR setup with zero escaped samples and no lifecycle side effects.
- server real-time playback should or should not advance while the integrated game is paused.
- speaker block position is sufficient durable identity across unload/break/replace.
- one specific MP3 library has accurate enough seek/gapless semantics for the final design.
- any current cache/source/content size recommendation is final.
