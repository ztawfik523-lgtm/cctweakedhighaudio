# ADR-0006 — Identify finite media by SHA-256 of original content bytes

**Status:** Accepted  
**Date:** 2026-09-07  
**Search tags:** `ADR-0006`, `ContentId`, `SHA-256`, `CACHE`, `DEDUPE`

## Context

The same finite song may be played by many speakers, replayed later, or requested by several clients. Treating each speaker/session as if it owned a private copy causes redundant server storage, network transfer, decode work, and client memory.

HighAudio also needs a trustworthy way to determine whether a client's cached object is exactly the media object referenced by a server session.

## Decision

For finite uploaded media:

```text
ContentId = SHA-256(original uploaded media/file bytes)
```

`ContentId` is independent of `SessionId`, `EmitterId`, filename, display metadata, and sync-group identity.

The server stores finite content in a bounded content store keyed by `ContentId`. Clients may keep bounded compressed-content caches keyed by the same ID. Completed transfers are verified against the expected hash before becoming trusted cache entries.

Decoded PCM is a separate cache layer and may use additional format/decode parameters as part of its internal cache key. `ContentId` alone names the original bytes, not one particular decoded representation.

## Alternatives considered

### Random content IDs

Simple, but cannot naturally detect identical media uploaded more than once and gives no integrity check. Rejected.

### Filename/path as identity

Incorrect because names can collide/change and CC virtual filesystem paths are local to a computer. Rejected.

### Hash decoded PCM instead of original bytes

Could deduplicate differently encoded but acoustically equivalent content, but requires decoding before stable identity, complicates upload/transfer, and makes codec normalization part of identity semantics. Rejected for v1.

### Weak/fast non-cryptographic hash only

Potentially faster but less useful as an integrity identifier for untrusted programmable inputs. SHA-256 cost is acceptable for media-sized objects and can be streamed incrementally. Rejected as primary identity.

## Consequences

Positive:

- exact duplicate uploads naturally deduplicate;
- one transferred compressed object can serve many sessions/emitters;
- clients can advertise `ContentHave(ContentId)` safely after verification;
- transfer corruption/mismatch is detectable;
- server/session packets stay small by referencing an ID.

Negative:

- hashing consumes CPU proportional to upload size;
- byte-identical dedupe does not merge semantically identical media encoded differently;
- caches still need strict byte/count/eviction limits;
- changing original bytes, even metadata, produces a new ContentId.

## Security/resource implications

Hashing does not make content safe to decode. Size, duration, channel/sample-rate, decoder-time, and cache quotas remain separate controls.

Do not allocate a full second copy merely to hash: hash incrementally while bounded upload chunks are written into HighAudio-owned storage.

## Validation

Unit-test stable hash generation, upload restart/cancel behavior, corruption rejection, and cache hit/miss behavior in the finite-content milestones.
