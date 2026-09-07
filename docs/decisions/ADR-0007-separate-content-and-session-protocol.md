# ADR-0007 — Separate large content transport from session/control protocol

**Status:** Accepted  
**Date:** 2026-09-07  
**Search tags:** `ADR-0007`, `NETWORK`, `CONTENT-TRANSFER`, `SESSION-SNAPSHOT`, `PAYLOAD-LIMIT`

## Context

A HighAudio session is small semantic state, while media content may be many megabytes. NeoForge custom payloads have strict size ceilings, so arbitrary songs cannot be carried in one packet. The same content may also be cached already, reused by many emitters, or requested by a late listener after the session has been running for minutes.

Coupling session commands directly to whole-file bytes would make replay, cache hits, late join, stop/seek, and multi-speaker deduplication expensive and fragile.

## Decision

Treat session/control state and finite-content transport as two related but separate protocol concerns.

Session/control payloads reference `ContentId` and remain small. Conceptual messages include:

```text
ProtocolHello / capabilities
SessionSnapshot
SessionDelta
SessionStop / ended revision
EmitterTransform
```

Finite content uses an independent transfer flow, conceptually:

```text
ContentManifest
ContentHave / ContentNeed
ContentChunk
ContentComplete
ContentVerified / ContentReject
transfer cancel/replacement as needed
```

Exact packet names/codecs are not frozen by this ADR.

Content chunks must leave safe overhead below NeoForge's payload ceiling. Transfers are scheduled per client with bounded queued bytes so one slow connection cannot retain unbounded server memory or block all other listeners.

The underlying Minecraft connection is ordered/reliable. Do **not** require a round-trip ACK for every chunk unless measurement demonstrates a concrete need for application-level flow control beyond bounded production/queueing.

A client does not treat transferred bytes as trusted cache content until the completed object verifies against the expected `ContentId` hash.

## Alternatives considered

### Include complete content in SessionStart

Rejected: violates payload limits for large media, duplicates transfer for replay/multiple speakers, and makes late-listener snapshots huge.

### Stream decoded PCM continuously from server to every client

Rejected for normal finite media: multiplies server CPU/network by listeners and speakers, defeats compressed-content caching, and couples playback quality to continuous network delivery.

### Create a new transfer for every speaker/session even when content matches

Rejected: `ContentId` exists specifically to make transfer/cache lifetime independent from one playback session.

### Per-chunk mandatory acknowledgements

Not selected by default. TCP/Netty already provide ordered reliable delivery; per-chunk ACKs create latency and packet overhead. A bounded application send window or coarse transfer progress/need mechanism may still be added if `MILESTONE-006` measurements require it.

## Consequences

Positive:

- session snapshots remain tiny and useful for late join/reconnect;
- one client transfer can serve many simultaneous/repeated sessions using the same content;
- server can stop/pause/seek a session independently of content transfer state;
- cache negotiation becomes straightforward;
- slow clients can be isolated by per-client transfer budgets.

Negative:

- more protocol state/components than one-shot packets;
- transfer cancellation, timeout, hash verification, and bounded reassembly/storage must be implemented carefully;
- content may arrive after a semantic session has already advanced, so the client renderer must seek/reconcile to current server position before becoming audible.

## Security/resource implications

The transport layer must independently limit:

- maximum accepted content size;
- concurrent transfers/client;
- queued transfer bytes/client;
- server content-store bytes/count;
- client cache bytes/count;
- stale transfer lifetime;
- chunk field lengths and reassembly expectations.

Limits are configuration/performance decisions, not fixed by this ADR.

## Validation

`MILESTONE-004` proves the first end-to-end transfer. `MILESTONE-006` validates deduplication, cache hits, slow/interrupted transfer cleanup, and bounded long-media behavior.
