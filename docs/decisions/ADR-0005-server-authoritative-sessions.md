# ADR-0005 — Server owns semantic playback sessions; clients own disposable renderers

**Status:** Accepted  
**Date:** 2026-09-07  
**Search tags:** `ADR-0005`, `SERVER-AUTHORITY`, `SESSION`, `REVISION`, `LATE-LISTENER`

## Context

A one-shot “play this sound now” packet is not enough for controllable long-form media. Clients may join late, leave range, change dimension, disconnect/reconnect, reload the sound engine, or lose local audio state while the logical song continues. Conversely, a server must be able to stop/pause/seek a session even if one client is currently absent or buffering.

The old compatibility work demonstrated the failure mode of client-only playback ownership: once local source state is destroyed, there is no authoritative description from which to reconstruct it.

## Decision

The server owns **semantic media truth**. A logical `MediaSession` contains or references at least:

```text
SessionId
EmitterId
ContentId
semantic state
revision
sample rate / media timeline metadata
anchorFrame
anchorClockTime
volume
loop
optional SyncGroupId
```

The client owns only disposable rendering state:

```text
content availability
local decode state
PCM queues/cache
Minecraft SoundInstance
Minecraft Channel/OpenAL source
local renderer position/error
```

A client's local states such as `DOWNLOADING`, `DECODING`, `BUFFERING`, or `OUT_OF_RANGE` do not automatically change the server session from `PLAYING`.

Clients reconstruct from authoritative session snapshots/deltas. Server revisions (and an explicit stop/end state or equivalent tombstone/revision rule) prevent stale client state from resurrecting playback after an authoritative stop.

Semantic media position is represented in sample frames anchored to a server `MediaClock`; the exact pause/freeze policy of that clock is a later explicit decision.

## Alternatives considered

### Client-authoritative playback

The client starts/stops locally based on one-shot commands and is expected to remember the rest.

Rejected: late join, reconnect, dimension return, and sound-engine reload become unreliable; server `isPlaying`/pause/seek truth diverges from what listeners hear.

### Server streams every decoded audio sample as the authoritative source of truth

Could make server timing central but creates unnecessary bandwidth/CPU coupling and makes caching/late clients worse. Rejected for finite media. The server owns the **timeline and state**, not every rendered PCM frame.

### Global BUFFERING/PREPARING until every client is ready

Rejected: one slow or distant client must not stall the semantic session for everybody.

## Consequences

Positive:

- late listeners can start at the correct current frame;
- F3+T/device reload becomes renderer reconstruction, not session loss;
- server-side pause/resume/seek/stop remain truthful while clients are absent;
- content transfer/decode can be independently optimized per client;
- synchronization groups can share a semantic schedule without requiring identical client readiness.

Negative:

- requires session revisions/snapshots and an explicit audience/recovery protocol;
- client and server state machines are intentionally different, which must be documented/tested;
- semantic clock and lifecycle persistence rules must be chosen explicitly.

## Validation

This architectural pattern is independent of the exact audio backend, but runtime lifecycle behavior must be validated in `MILESTONE-005` across join/range/dimension/reconnect/F3+T/device reload and authoritative stop cases.

## Follow-up decisions

Still unresolved:

- real-time vs pause-aware `MediaClock`;
- session behavior on emitter chunk unload/removal;
- server-restart persistence;
- multiple-computer ownership/conflict policy.
