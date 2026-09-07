# HighAudio architecture

**Status:** current architecture model; proposed mechanisms are labelled  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `ARCHITECTURE`, `SERVER-AUTHORITY`, `MINECRAFT-AUDIO`, `CONTENT-ID`, `SESSION-ID`, `EMITTER-ID`, `SYNC`

## 1. Architectural objective

HighAudio should make normal CC:Tweaked speakers capable of high-quality finite-media playback with truthful controls, late-listener recovery, deduplicated transfer/decode, and perceptually tight multi-speaker synchronization.

The architecture must preserve native CC:T speaker methods and must not make future moving emitters or Sound Physics Remastered integration prohibitively expensive.

The system is deliberately **CC:T-focused but not CC:T-internal everywhere**.

See:

- `ADR-0001`: CC:T required for v1.
- `ADR-0002`: isolate CC:T internals.
- `ADR-0003`: superseded additive `SpeakerPeripheral` Mixin fallback.
- `ADR-0004`: proposed Minecraft-owned client audio.
- `ADR-0005`: server-authoritative semantic sessions.
- `ADR-0006`: content-addressed media.
- `ADR-0007`: content transport separated from session control.
- `ADR-0008`: proposed targeted `SpeakerPeripheral` GenericSource.

## 2. System boundary

```text
+-----------------------------------------------------------+
| CC:Tweaked 1.120.0                                       |
| normal speaker / turtle speaker / pocket speaker classes  |
+----------------------------+------------------------------+
                             |
                             | CC:T integration only
                             v
+-----------------------------------------------------------+
| HighAudio CC:T adapter                                   |
| - GenericSource Lua method surface                       |
| - IComputerAccess/event bridge                           |
| - emitter resolution                                     |
| - bounded argument copy/upload facade                    |
+----------------------------+------------------------------+
                             |
                             v
+-----------------------------------------------------------+
| HighAudio server                                         |
| ContentStore        UploadManager                        |
| EmitterRegistry     MediaSessionManager                  |
| MediaClock          SyncGroupManager                     |
| AudienceManager     TransferScheduler                    |
+----------------------------+------------------------------+
                             |
                             | NeoForge HighAudio protocol
                             v
+-----------------------------------------------------------+
| HighAudio client                                         |
| SessionMirror       CompressedContentCache               |
| DecodeWorkers       DecoderSession implementations       |
| DecodedCache/Ring   HighAudioSoundInstance/AudioStream   |
+----------------------------+------------------------------+
                             |
                             v
+-----------------------------------------------------------+
| Minecraft SoundEngine / Library / Channel                |
| - Minecraft owns normal source lifecycle                 |
| - NeoForge PlayStreamingSourceEvent used where possible  |
| - tiny AL accessor only if EXP-003 proves necessary      |
+----------------------------+------------------------------+
                             |
                             v
                         OpenAL / SPR
```

## 3. Dependency rule

Allowed dependency direction:

```text
integration.cct -> server/core concepts
client -> shared protocol/core model
codec -> codec-neutral media interfaces
network -> shared wire model
```

Forbidden direction:

```text
decoder/cache/session/network -> CC:T SpeakerPeripheral internals
```

Minecraft/NeoForge types such as `ResourceKey<Level>`, `Vec3`, `ServerPlayer`, and network codecs are allowed where they are the real platform abstractions. We are isolating **CC:T implementation internals**, not pretending this is a platform-independent media framework.

The current `GenericSource` candidate requires the exact CC:T implementation artifact at compile time because its first method parameter is the internal `SpeakerPeripheral` class. That dependency must remain confined to `integration/cct`; it must not leak into media/session/network/client systems.

## 4. CC:T integration [PROPOSED]

Current leader: register a `GenericSource` targeted at exact CC:T 1.120.0 `SpeakerPeripheral` (`ADR-0008`).

Why it leads:

- the normal block speaker already exposes a concrete `SpeakerPeripheral`;
- exact CC:T 1.120.0 offers public `ComputerCraftAPI.registerGenericSource` registration;
- CC:T's generic method system applies methods based on the target parameter type and uses the same peripheral method supplier used for normal direct and wired peripheral method maps;
- targeting `SpeakerPeripheral` therefore contributes HighAudio methods without replacing the existing `IPeripheral` object;
- no CC:T bytecode transformation or Mixin configuration is required;
- native attach/detach/equality and native speaker methods remain CC:T-owned.

This remains gated by `EXP-001` / `GATE-001`.

Important limits/tradeoffs:

- `SpeakerPeripheral` is not public CC:T API, so this is still exact-version internal coupling;
- CC:T's `disabled_generic_methods` configuration can disable this source/method;
- turtle and pocket speaker peripherals derive from `SpeakerPeripheral`, so the method may appear there too and must be observed/gated deliberately.

Historical fallback: the additive base-class Mixin from `ADR-0003` passed automatic startup/application checks but was superseded before the manual gate because GenericSource is less invasive. If GenericSource fails real runtime proof, that Mixin is the first fallback before forwarding/capability replacement.

## 5. Server authority model [ACCEPTED]

The server owns **semantic media truth**, not client rendering state.

A server session conceptually contains:

```text
SessionId
EmitterId
ContentId
state
revision
volume
loop
sampleRate
anchorFrame
anchorClockTime
optional SyncGroupId
```

Semantic states may include:

```text
PREPARING -> SCHEDULED -> PLAYING -> PAUSED -> PLAYING -> ENDED
     |            |          |          |
     +------------+----------+----------+--> FAILED / STOPPED
```

`SEEKING` may be represented as a transient command/revision rather than a long-lived server state. Do not force the client state machine to equal the server state machine.

A client may simultaneously be:

```text
server session: PLAYING
client renderer: DOWNLOADING / DECODING / BUFFERING / RENDERING / OUT_OF_RANGE
```

A slow client must not change the global server session to `BUFFERING`.

## 6. Playback timeline [ACCEPTED CONCEPT, CLOCK POLICY UNDECIDED]

Semantic media position is represented in **sample frames**.

For a playing session:

```text
currentFrame = anchorFrame + elapsedClockTime * sampleRate
```

For a paused session, `anchorFrame` is fixed.

Do not store truth merely as “started at Minecraft tick N”. A server TPS drop should not accidentally redefine media time unless we deliberately choose game-time semantics.

Use a `MediaClock` abstraction. Do not scatter `System.nanoTime()` throughout session code.

Unresolved product choice:

- real monotonic clock: playback advances through a server/singleplayer pause;
- pause-aware clock: semantic playback pauses with the integrated game.

This is tracked separately and must be chosen deliberately.

## 7. Identity model

Keep these IDs separate:

- `ContentId` — hash of compressed/original finite content bytes.
- `SessionId` — one logical playback session.
- `EmitterId` — identity of the thing emitting audio.
- `SyncGroupId` — identity of a coordinated playback group.
- controller/computer identity — used only where ownership/conflict semantics require it.

Do **not** use CC:T's native speaker source UUID as durable `EmitterId` without lifecycle proof. It is generated by `SpeakerPeripheral` construction and is not proven to survive reconstruction/server restart.

Block-speaker persistent identity across chunk unload vs block replacement remains an experiment/design task.

## 8. Content model [ACCEPTED]

Finite media is content-addressed:

```text
ContentId = SHA-256(original compressed/file bytes)
```

The server keeps a bounded compressed-content store. Each client keeps a bounded compressed cache and a bounded decoded cache/ring.

Desired scaling property:

```text
16 speakers + same song
!= 16 full transfers + 16 full decodes

Target:
1 compressed object/client
1 reusable decode/cache pipeline where practical
N lightweight positional render sources
```

Do not always fully decode long content. Mono signed 16-bit PCM at 48 kHz is about 96,000 bytes/s, around 57.6 MB for ten minutes and 345.6 MB for an hour before overhead.

Static-vs-streaming selection must ultimately be budget-aware rather than based on one permanent hard duration threshold.

## 9. Media ingestion from Lua

Java cannot assume a CC computer path is directly readable through `IComputerAccess`.

Low-level direction:

```text
begin upload -> bounded write chunks -> finish -> ContentId
```

The peripheral method must copy any argument bytes which need to outlive the Lua call into HighAudio-owned storage/memory before asynchronous processing.

A bundled Lua helper can provide ergonomic semantics such as:

```lua
highaudio.playFile(speaker, "/music/song.ogg")
```

by reading the CC filesystem itself and feeding the low-level upload API.

Direct `playMedia(bytes)` can later be a convenience for small content, not the only ingestion mechanism.

## 10. Network architecture [ACCEPTED]

Session/control traffic and large content transport are separate protocols.

Reasons:

- NeoForge custom payload size limits rule out sending arbitrary songs in one payload;
- a session snapshot must be small and useful even if content is already cached;
- content must be transferable once and reused across multiple sessions/emitters.

Conceptual payload families:

```text
ProtocolHello / capabilities
SessionSnapshot / SessionDelta / SessionStop
ContentManifest
ContentNeed / ContentHave
ContentChunk
ContentComplete / ContentVerified / ContentReject
EmitterTransform
```

Exact names and codecs are not frozen.

Transport rules:

- chunk sizes remain below the clientbound payload ceiling with overhead margin;
- bound queued bytes per client;
- one slow client must not block everyone else;
- verify completed content hash;
- cancel stale transfers;
- do not add per-chunk ACK round trips unless measurements show they are necessary (the underlying connection is ordered/reliable).

## 11. Audience and recovery model

`AudienceManager` is distinct from vanilla chunk tracking.

A client entering an active session's audience receives current authoritative state and, if needed, content. It should begin rendering at the correct timeline position once ready.

Must eventually handle:

- join during playback;
- leave range and return;
- dimension out/back;
- disconnect/reconnect;
- F3+T/resource reload;
- audio output-device reload;
- session stopped while client was absent.

A returning client reconstructs from server truth; stale client sound state is never authoritative.

Exact audible and prefetch range are configuration/product decisions, not copied blindly from CC:HQ.

## 12. Client playback backend [PROPOSED]

Leading design (`ADR-0004`): Minecraft owns the `SoundInstance`, `AudioStream`, and `Channel` lifecycle.

HighAudio supplies:

- codec-neutral decoded PCM;
- a custom `AudioStream` or equivalent producer;
- a positional HighAudio `SoundInstance`;
- reconstruction logic from server session truth.

Use NeoForge sound-source events (notably `PlayStreamingSourceEvent`) to capture the Minecraft-owned `Channel` when possible. Use `SoundEngineLoadEvent`/equivalent lifecycle signals for reload/device reconstruction.

Only add a tiny Mixin/accessor to the Minecraft `Channel`/OpenAL source if an experiment proves public/event APIs insufficient for precise sync/offset measurement.

Primary alternative (rejected as default): fully independent raw OpenAL source ownership. It gives precise low-level control but takes responsibility for volume-category integration, source lifecycle, device reloads, F3+T, and acoustic-mod visibility.

## 13. Spatial audio policy

A physical positional speaker should render positional **mono** by default. Stereo/multichannel input must be downmixed for one positional emitter unless a future explicit multi-emitter/channel-routing feature is designed.

Volume is conceptually composed from:

```text
session gain
x Minecraft sound-category gain
x master gain
x distance/attenuation
x future acoustic processing
```

Exact category is expected to follow speaker-like `RECORDS` behavior unless runtime/product testing argues otherwise.

## 14. Synchronization [EXPERIMENT]

The architecture requires tighter sync than “same server tick”, but the final mechanism is deliberately not accepted yet.

Proposed experiment path:

1. create all HighAudio `SoundInstance`s through Minecraft normally;
2. capture their Channels through NeoForge streaming-source events;
3. ensure each channel is prepared without audible leakage (zero gain / pause / reset strategy to be tested);
4. obtain raw OpenAL source IDs only if necessary;
5. atomically start a ready group with OpenAL vector start (`alSourcePlayv`) if reliable;
6. measure start skew and later drift;
7. reconcile client rendering to the server sample-frame timeline when deviation exceeds an empirically chosen threshold.

OpenAL source offset is an observation, not the authoritative session clock. Audible position may also involve queued-buffer duration and device latency.

Do not pitch-shift for drift correction unless experiments demonstrate a perceptual benefit.

## 15. Source capacity [EXPERIMENT]

Do not assume “32 HighAudio sources”. Minecraft maintains separate static and streaming channel pools and other game/mod sounds compete for them.

`EXP-003` must measure actual capacity at 1/4/8/16 HighAudio streams, with and without SPR where applicable. A 16-speaker scenario is a required stress target, not a guaranteed supported count.

This measurement may influence the static-vs-streaming policy.

## 16. Decoder architecture

Codec-specific logic sits behind a common concept such as:

```text
DecoderSession
  metadata()
  sampleRate()
  channelCount()
  totalFrames()
  readFrames(...)
  seekFrame(...)
  close()
```

Initial implementation order:

1. WAV PCM — simplest correctness baseline.
2. Ogg Vorbis — strong candidate using LWJGL STB Vorbis.
3. MP3 — only after seek/VBR/gapless behavior of the chosen decoder is proven.

FLAC/Opus/AAC/M4A are later extensions, not prerequisites for the first vertical slice.

## 17. Moving emitters

Media/session state is separate from emitter transform:

```text
Session -> EmitterId
EmitterId -> dimension + current transform
```

This prevents media payloads from being resent just because a turtle/ship moves.

Block speakers are milestone-one emitters. Turtle/pocket/VS2 behavior is deferred, but the protocol must not make moving emitters impossible.

## 18. URL/live streaming [DEFERRED]

Finite uploaded content and live HTTP streams are different problems.

V1 should prefer:

```text
Lua HTTP/file download -> finite HighAudio upload -> content-addressed playback
```

Server fetching, client fetching, Icecast/ICY, HLS, credentials, redirects, SSRF controls, reconnect, and live synchronization are a later architecture track.

## 19. Sound Physics Remastered

SPR compatibility is a future milestone, not a reason to duplicate the old CC:HQ compatibility bridge.

Keeping HighAudio inside Minecraft's normal sound/channel ownership is intended to maximize natural compatibility. Exact SPR 1.21.1-1.5.1 interception and EFX behavior must be verified at the SPR milestone.

HighAudio should have clear internal source lifecycle points even if no public compatibility API exists yet:

```text
created -> prepared -> started -> moved -> paused/resumed -> stopped -> destroyed
```

## 20. Threading ownership

Conceptual ownership:

| Thread/domain | Responsibility |
|---|---|
| CC computer/Lua thread | validate Lua arguments; copy upload chunk; enqueue semantic command |
| Minecraft server thread | authoritative world/emitter/session mutations; audience selection |
| network handlers | decode bounded payloads; hand off state mutation to appropriate thread |
| worker pool | hashing, media probing, decoding, disk-cache work where safe |
| Minecraft client/main thread | session mirror coordination and SoundManager interaction |
| Minecraft sound executor/thread | Channel/OpenAL operations required by Minecraft's audio engine |

No shared mutable session state should be casually touched across these domains. Define ownership/queues before implementation expands.

## 21. Explicitly unresolved decisions

Do not infer answers from implementation convenience:

- session behavior on chunk unload;
- pause-aware vs real-time `MediaClock`;
- initial turtle/pocket support policy;
- synchronized-group Lua API;
- exact resource limits/cache sizes;
- exact audible/prefetch radius;
- final MP3 decoder;
- final arm/start/drift mechanism;
- server-restart persistence;
- final license/mod id/package root.

See `PROTOTYPES.md`, `RISKS.md`, and the ADR statuses before resolving any of these.
