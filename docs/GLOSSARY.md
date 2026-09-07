# HighAudio glossary

**Status:** canonical terminology  
**Last reviewed:** 2026-09-07  
**Search tags:** `GLOSSARY`, `ContentId`, `SessionId`, `EmitterId`, `SyncGroupId`, `MediaClock`

Use these terms consistently in code, packets, logs, tests, and future discussions.

| Term | Meaning | Explicitly not |
|---|---|---|
| **HighAudio** | Working project/product name for the new CC:T speaker media system. | Final mod id/package/license. |
| **CC:T adapter** | Narrow code which knows about CC:T peripheral classes, Lua calls, `IComputerAccess`, and emitter resolution. | The decoder, cache, network protocol, or client renderer. |
| **Emitter** | A logical thing in the world which emits a HighAudio session at a position/transform. Initially a normal CC:T speaker block. | A media file or playback session. |
| **EmitterId** | HighAudio identity for an emitter across the lifetime semantics we choose. | CC:T's native random speaker audio-source UUID unless explicitly mapped/validated. |
| **Content** | Finite original media bytes uploaded/stored for reuse. | A currently-playing session. |
| **ContentId** | Content-address identity, currently `SHA-256(original media bytes)`. | Filename, speaker id, session id. |
| **MediaSession** | Server-authoritative semantic playback of one content item through one emitter (or a coordinated model built on that). | Client decoder/rendering state. |
| **SessionId** | Unique identity of a logical playback session. | ContentId or EmitterId. |
| **SyncGroup** | Set of sessions/emitters coordinated against a common schedule/timeline. | An assumption that all clients are simultaneously ready. |
| **SyncGroupId** | Identity of one coordinated group. | Speaker name or ContentId. |
| **controller/computer identity** | CC computer involved in issuing session commands when ownership rules need it. | Permanent physical speaker identity. |
| **semantic state** | Server truth such as playing, paused, stopped, anchor frame, volume, loop, revision. | Whether one client is buffering or has an OpenAL source. |
| **render state** | One client's local state such as downloading, decoding, buffering, rendering, out-of-range. | Global server session truth. |
| **sample frame** | One sample instant across all channels of decoded media; HighAudio's preferred semantic position unit. | One byte, one codec frame, or one Minecraft tick. |
| **anchorFrame** | Known media sample-frame position at an `anchorClockTime`. | Current raw OpenAL source offset. |
| **MediaClock** | Server-side abstraction used to advance semantic media position from anchors. | Necessarily `System.nanoTime()`; pause semantics are undecided. |
| **session revision** | Monotonic version/change marker used so clients can reject stale session state. | Network packet sequence number for content chunks. |
| **ContentStore** | Bounded server-side store for finite uploaded compressed/original content. | Unlimited permanent media library. |
| **CompressedContentCache** | Client cache of original/transferred content bytes keyed by ContentId. | Decoded PCM cache. |
| **decoded cache** | Bounded client cache of reusable decoded PCM/segments. | Guaranteed full decode of every song. |
| **ring buffer** | Bounded decoded PCM window used for long/streamed finite content. | HTTP live stream specifically; same mechanism may later be reused. |
| **DecoderSession** | Codec-neutral decoder instance that reads/seeks sample frames and exposes metadata. | A SoundInstance or OpenAL source. |
| **HighAudioSoundInstance** | Client Minecraft sound object representing a positional HighAudio renderer. | Server session state. |
| **AudioStream** | Minecraft client streaming PCM producer contract used by the leading playback design. | Network stream or HTTP stream. |
| **Channel** | Minecraft/OpenAL-backed audio channel owned by the Minecraft sound engine. | HighAudio semantic session. |
| **source ID** | Raw OpenAL source handle when discussing low-level sync/measurement. | EmitterId or SessionId. Always qualify as `OpenAL source id` in logs/docs. |
| **audience** | Clients currently relevant for a session based on dimension/range/prefetch policy. | Exactly vanilla chunk-tracking set. |
| **AudienceManager** | Server system which determines who should know/render/prefetch an active session. | Minecraft's chunk tracker itself. |
| **TransferScheduler** | Server component which bounds/prioritizes per-client content transfer. | Acknowledgement protocol by definition. |
| **authoritative stop** | Server state/revision stating a session is over. A returning/reloading client must not revive it. | Merely destroying one client's local source. |
| **late listener** | Client which enters range/dimension/server after a session already started. | Error case; architecture must reconstruct current timeline. |
| **static playback** | Fully decoded/prepared buffer approach appropriate for smaller media if budgets allow. | Always better than streaming. |
| **streaming playback** | Bounded queued/ring PCM feeding a Minecraft streaming Channel. | Necessarily an Internet/live stream. |
| **native CC:T audio** | Existing CC:T `playNote`, `playSound`, `playAudio`, `stop` behavior. | HighAudio media API. Must remain regression-tested. |
| **SPR** | Sound Physics Remastered. Target compatibility investigation: Minecraft 1.21.1 / SPR 1.5.1. | The old custom compatibility bridge architecture. |
| **probe** | Small throwaway/minimal implementation intended to answer one architecture question. | Production feature just because it happens to work once. |
| **gate** | Observable pass/fail condition required before the roadmap proceeds with a dependent architecture. | A vague milestone checkbox. |

## State vocabulary

Server semantic state candidates:

```text
PREPARING
SCHEDULED
PLAYING
PAUSED
ENDED
STOPPED
FAILED
```

`SEEKING` may be a transient command/revision rather than a persistent state. `TRANSFERRING`, `DECODING`, and `BUFFERING` are primarily client/content-pipeline states and should not automatically become global session state.

## Naming rule

Never name a variable simply `source` when it could mean more than one thing. Prefer:

```text
emitterId
sessionId
contentId
syncGroupId
openAlSourceId
soundInstance
channel
```

This is specifically intended to prevent the identity confusion that occurred in earlier compatibility work.
