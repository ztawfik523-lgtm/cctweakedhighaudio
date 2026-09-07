# HighAudio architecture

**Status:** current architecture model  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `ARCHITECTURE`, `SERVER-AUTHORITY`, `MINECRAFT-AUDIO`, `CONTENT-ID`, `SESSION-ID`, `EMITTER-ID`, `SYNC`

## 1. Objective

HighAudio extends normal CC:Tweaked speakers with high-quality finite-media playback while preserving native speaker behavior and Minecraft's audio lifecycle. The long-term product needs bounded media transfer/cache/decode, truthful server-authoritative controls, late-listener recovery, multi-speaker synchronization, moving-emitter support, and Sound Physics Remastered compatibility.

The design is deliberately **CC:T-focused but not CC:T-internal everywhere**.

Accepted/proposed decisions:

- `ADR-0001`: CC:T required for v1.
- `ADR-0002`: isolate CC:T internals.
- `ADR-0003`: superseded additive `SpeakerPeripheral` Mixin fallback.
- `ADR-0004`: **Accepted** Minecraft-owned client audio.
- `ADR-0005`: server-authoritative semantic sessions.
- `ADR-0006`: content-addressed media.
- `ADR-0007`: content transport separated from session/control.
- `ADR-0008`: **Accepted** targeted `SpeakerPeripheral` GenericSource.
- `ADR-0009`: **Accepted** conservative Minecraft streaming-reservation rebalance.
- `ADR-0010`: **Proposed** playback timing intents (`immediate`, `together`, optional `scheduled`).

Implementation boundary: the targeted CC:T integration, Minecraft-owned diagnostic playback/lifecycle path, capacity/timing diagnostics, and total-preserving reservation policy exist today. The content, upload, network, cache, decoder, session, audience, and production synchronization components below are planned unless a section explicitly says validated or Accepted. See [`CURRENT-STATE.md`](CURRENT-STATE.md) for the authoritative status summary.

## 2. Target product system boundary

This diagram combines validated foundations with planned product components; it is not an inventory of currently implemented classes.

```text
+-----------------------------------------------------------+
| CC:Tweaked 1.120.0                                       |
| normal speaker / turtle speaker / pocket speaker classes  |
+----------------------------+------------------------------+
                             |
                             | localized CC:T integration
                             v
+-----------------------------------------------------------+
| HighAudio CC:T adapter                                   |
| - GenericSource Lua surface                              |
| - argument validation/copy                               |
| - computer/emitter resolution                            |
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
                             | bounded HighAudio protocol
                             v
+-----------------------------------------------------------+
| HighAudio client                                         |
| SessionMirror       CompressedContentCache               |
| DecodeWorkers       DecoderSession implementations       |
| DecodedCache/Ring   HighAudioSoundInstance/AudioStream   |
| TimingCoordinator   Renderer diagnostics                 |
+----------------------------+------------------------------+
                             |
                             v
+-----------------------------------------------------------+
| Minecraft SoundEngine / Library / Channel                |
| - Minecraft owns normal source/device lifecycle          |
| - HighAudio may use narrow timing access over its source |
| - total-preserving stream reservation policy             |
+----------------------------+------------------------------+
                             |
                             v
                         OpenAL / SPR
```

## 3. Dependency rule

Allowed direction:

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

Minecraft/NeoForge types are allowed where they are the actual platform abstractions. The rule is to isolate **CC:T implementation internals**, not to pretend HighAudio is a generic standalone media framework.

The accepted GenericSource still targets exact CC:T 1.120.0's non-public `SpeakerPeripheral`; that compile-time implementation dependency stays under `integration/cct`.

## 4. CC:T integration — Accepted

HighAudio registers a targeted CC:T `GenericSource` (`ADR-0008`). The normal peripheral object remains CC:T-owned; HighAudio contributes additive Lua methods without replacing direct/wired/turtle/pocket speaker identity or native methods.

Real M1 evidence proved the method on block, wired, turtle, pocket, and reconstructed speaker peripherals while native `playNote`, `playSound`, `playAudio`, and `stop` remained usable.

The older SpeakerPeripheral Mixin remains fallback history only.

## 5. Server authority model — Planned

The server owns **semantic media truth**, not client renderer state.

A future session conceptually contains:

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

Client buffering/decoding state is not global session truth. A slow client must not set the whole server session to `BUFFERING`.

## 6. Timeline model — Planned

Semantic media position is represented in sample frames:

```text
currentFrame = anchorFrame + elapsedClockTime * sampleRate
```

Use a `MediaClock` abstraction. Do not scatter `System.nanoTime()` or Minecraft tick numbers as authoritative media truth.

Pause-aware versus real-monotonic server clock remains a deliberate M5 product decision.

Renderer clocks are separate from server/session time. OpenAL device clock values are local per client/device and must never be exposed as the global session clock.

## 7. Identity model — Planned

Keep identities distinct:

- `ContentId` — SHA-256 of original finite file bytes.
- `SessionId` — one logical playback session.
- `EmitterId` — identity of the emitting object.
- `SyncGroupId` — coordinated playback group.
- controller/computer identity — ownership/conflict only where required.

Do not use CC:T's native speaker source UUID as durable `EmitterId` without explicit persistence/lifecycle proof.

## 8. Content model — Planned

Finite media is content-addressed:

```text
ContentId = SHA-256(original file bytes)
```

The server keeps a bounded content store. Clients keep bounded compressed and decoded caches/rings as later milestones require.

Desired scaling:

```text
16 speakers + same song
!= 16 transfers + 16 full decodes

Target:
1 compressed object/client
shared decode/cache where practical
N lightweight positional render sources
```

Do not always fully decode long media. Static-vs-streaming decisions must be budget-aware.

## 9. Lua media ingestion — Planned

Java cannot assume a CC filesystem path is directly readable through `IComputerAccess`.

Low-level direction:

```text
begin upload -> bounded write chunks -> finish -> ContentId
```

Any Lua argument bytes that must outlive the Lua call are copied into HighAudio-owned memory/storage during the call before asynchronous processing. Do not retain unsafe `IArguments`/Lua-table-backed state across threads/callbacks.

A bundled Lua helper can later provide:

```lua
highaudio.playFile(speaker, "/music/song.wav")
```

by reading the CC filesystem itself and feeding the bounded low-level upload API.

## 10. Network architecture — Planned

Session/control traffic and large content transport remain separate.

Conceptual families:

```text
ProtocolHello / capabilities
SessionSnapshot / SessionDelta / SessionStop
ContentManifest
ContentNeed / ContentHave
ContentChunk
ContentComplete / ContentVerified / ContentReject
EmitterTransform
```

Transport rules:

- bounded chunk sizes with overhead margin below platform payload limits;
- bounded queued bytes per client;
- one slow client must not block others;
- verify completed hash;
- cancel stale transfers;
- do not add per-chunk ACK round trips unless measurement justifies them.

M4 should implement only the minimum bounded content-transfer subset needed for one finite-file vertical slice.

## 11. Audience and recovery — Planned

`AudienceManager` is distinct from vanilla chunk tracking. A client entering an active session's audience eventually receives authoritative state plus content if needed and reconstructs the correct play position once ready.

Later lifecycle work must cover join during playback, range/dimension changes, reconnect, F3+T/device reload, and stopped sessions while absent.

## 12. Client playback backend — Accepted

`ADR-0004` is Accepted.

HighAudio supplies decoded PCM and a positional `SoundInstance`/`AudioStream`; Minecraft owns `SoundManager`, `SoundEngine`, `Library`, `Channel`, OpenAL source allocation, device/context lifecycle, normal volume/category integration, and source destruction.

Validated lifecycle/event boundary:

```text
PlayStreamingSourceEvent -> associate HighAudio sound with Minecraft Channel
SoundEngineLoadEvent     -> renderer reconstruction/reload signal
```

HighAudio does **not** own an independent OpenAL engine.

Narrow low-level access is permitted only for timing/measurement over already Minecraft-owned sources when an explicit playback intent needs it.

## 13. Streaming capacity policy — Accepted

M3 measured vanilla streaming at exactly 8 simultaneous streaming channels on the real target runtime. `ADR-0009` therefore accepts a conservative total-preserving reservation rebalance.

On the measured layout:

```text
247 static + 8 streaming = 255
->
239 static + 16 streaming = 255
```

The implementation only opts in on eligible normal layouts and leaves weaker-than-normal streaming reservations unchanged. It does not raise the total source budget and does not create/delete HighAudio-owned OpenAL sources.

Real client testing repeatedly achieved 16/16 streams and still observed static-side Minecraft sound allocation.

## 14. Playback timing intents — Proposed

`ADR-0010` remains Proposed because the optional scheduled path is not yet end-to-end proven with a real session timeline. The semantic model is nevertheless the current design direction.

### Immediate

Default and lowest latency. Play each sound when ready through normal Minecraft-owned playback. No group barrier or artificial sync delay.

### Together

Explicit local group intent: wait until all required participants are ready, then start together ASAP. The leading primitive is core OpenAL `alSourcePlayv` over Minecraft-owned source IDs on Minecraft's sound thread.

Real M3 evidence proved 2/4/8/16 vector start with zero measured relative sample-offset spread at the sampled checkpoints and no OpenAL vector error.

A one-member together group should normally collapse to immediate.

### Scheduled

Optional timeline intent: audible media sample zero should align with a session target time. The exact target stack automatically exposes `AL_SOFT_source_start_delay`, `AL_SOFT_source_latency`, and `ALC_SOFT_device_clock` after Minecraft initializes the device.

The diagnostic implementation distinguishes:

1. source-start device-clock time;
2. media-zero renderer time after hidden preparation preroll;
3. estimated physical-output media-zero after device output latency.

It uses atomic source-offset/device-clock measurement and must not falsely treat a future-scheduled source as advancing before its target.

No fixed 100 ms public delay exists. Lead time is only a preparation/scheduling budget. Raw OpenAL clock/source concepts remain hidden from Lua.

End-to-end scheduled timing is deferred until real production session timing exists; it is not required for ordinary immediate playback or M3 closure.

## 15. Long-running synchronization — Planned

Initial synchronized start does **not** prove a long stream can never drift or underrun.

Production sync (M7) still needs:

```text
shared authoritative session timeline
buffer health
actual renderer position
error measurement
drift/underrun policy
conservative correction
```

OpenAL source offsets/device clocks are observations, not global session authority.

## 16. Decoder architecture — Planned

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

Implementation order:

1. WAV PCM — M4 correctness baseline.
2. Ogg Vorbis — after the WAV vertical slice.
3. MP3 — later, after VBR/duration/seek/gapless behavior of the selected decoder is proven.
4. FLAC/Opus/AAC/M4A only as later justified expansion.

Physical positional speakers should render mono by default; stereo/multichannel input is downmixed for one positional emitter unless a future explicit routing design says otherwise.

## 17. Moving emitters — Planned

Media/session state is separate from emitter transform:

```text
Session -> EmitterId
EmitterId -> dimension + current transform
```

Content is never resent merely because an emitter moves. Turtle/pocket/VS2 production behavior remains M9.

## 18. Sound Physics Remastered — Deferred to M10

Exact SPR 1.21.1-1.5.1 compatibility belongs to M10.

Keeping Minecraft ownership is intended to maximize natural compatibility with SPR's Minecraft-channel interception model. Do not duplicate M10 manual testing during every earlier milestone. If exact M10 evidence shows an adapter is required, keep it narrow and lifecycle-aware.

## 19. Threading ownership — Planned rules

| Thread/domain | Responsibility |
|---|---|
| CC computer/Lua thread | validate arguments; copy upload chunk; enqueue semantic request |
| Minecraft server thread | authoritative world/emitter/session mutations; audience selection |
| network handlers | decode bounded payloads; hand state to owning thread |
| worker pool | hashing, media probing/decoding, cache I/O where safe |
| Minecraft client/main thread | session/content coordination and SoundManager interaction |
| Minecraft sound executor/thread | Channel/OpenAL operations for Minecraft-owned audio |

Do not casually share mutable upload/session/render state across these domains. Define ownership/queues explicitly as M4+ implementation expands.

## 20. Explicitly unresolved product decisions

Do not infer these from implementation convenience:

- exact M4 upload/content/cache limits;
- server media clock pause semantics;
- chunk-unload/block-replacement session behavior;
- final public Lua names/session ergonomics;
- exact audible/prefetch radius;
- scheduled-mode fallback policy;
- long-running drift correction threshold;
- persistent server-restart behavior;
- final MP3 decoder;
- SPR adapter need;
- moving-emitter identity/persistence.

## 21. Next implementation milestone

M0–M3 are complete. M4 has not started and is the first true finite-file vertical slice. It should add only enough upload/content/network/cache/WAV/session glue to make one normal placed speaker play one real file end-to-end while preserving the accepted architecture and bounded-resource rules.

Do not pull MP3, production synchronization groups, moving emitters, URL/live streaming, or broad SPR compatibility into that first slice.
