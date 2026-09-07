# HighAudio current handoff

**Status:** canonical chat/session handoff  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Prepared:** 2026-09-07  
**Stable `main` baseline:** `a83dd483a501dd2e1d665e735d11fcdb0dd522e0`  
**Current development branch:** `milestone-003-exp-003-timing-modes`  
**Current gate:** `MILESTONE-003` / `EXP-003` / `GATE-003`

Exact preserved runtime/prototype evidence remains more authoritative than this summary. The active M3 branch is intentionally ahead of `main`; a new engineering chat should inspect both and must not restart work from the old `main` milestone wording.

## Fixed project target

```text
Minecraft:     1.21.1
Java:          21
CC:Tweaked:    1.120.0
CC:T source:   tag v1.21.1-1.120.0
NeoForge:      21.1.247 baseline
Compatibility: 21.1.248
SPR future:    1.21.1-1.5.1
```

CC:T 1.120.2/current may be comparison material only, never exact-version proof.

## Completed gates

### MILESTONE-000 / GATE-000 — PASSED

Repository/bootstrap/version pinning and the exact `.247/.248` CI matrix are established.

### MILESTONE-001 / EXP-001 / GATE-001 — PASSED

Accepted integration is the targeted CC:T `GenericSource` in `ADR-0008`, not the older additive `SpeakerPeripheral` Mixin proposal.

Frozen accepted candidate:

```text
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI:     34082746562
SHA:    0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

Broad real `.247` evidence covered direct/wired block speakers, native CC:T methods, turtle, pocket speaker, and recreated/lifecycle peripherals. `ADR-0003` is superseded fallback history; `ADR-0008` is Accepted.

### MILESTONE-002 / EXP-002 / GATE-002 — PASSED

HighAudio-owned 48 kHz PCM was proven through Minecraft-owned `SoundInstance + AudioStream + SoundManager + Channel` lifecycle without an independent raw OpenAL source manager.

Frozen candidate:

```text
commit: 4e31bbd08cc8c4e314637d857098c02232f41ff4
CI:     34088822441
SHA:    515ced7cb14d0ac94131388997c23547cd907a0348a2777a90abf5467d312913
```

Real `.247` evidence covered audibility, attenuation/category volume, natural/explicit stop, pause/resume, disconnect cleanup, F3+T renderer rebuild/replay, and clean lifecycle behavior.

Important carried facts:

1. stream bytes consumed are not audible playhead;
2. Java `SoundEngine` object identity is not a renderer-generation identity;
3. effective volume zero may prevent channel allocation and is not a guaranteed arming mechanism.

`ADR-0004` remains Proposed until M3 closes the renderer capacity/timing boundary.

## MILESTONE-003 / EXP-003 — current work

M3 now has two largely solved parts: capacity and local start timing. Production media/session/network work remains deferred.

### Capacity baseline — PASS

Vanilla Minecraft-owned streaming on the real `.247` runtime was measured at 8 simultaneous streaming channels:

```text
1  -> 1
2  -> 2
4  -> 4
6  -> 6
8  -> 8
10 -> 8
12 -> 8
16 -> 8
```

Minecraft reported `... + 8/8`; this is a Minecraft streaming-reservation boundary, not an OpenAL hardware-source maximum.

Evidence:

`docs/test-batches/evidence/TEST-BATCH-003-NEOFORGE-21.1.247.md`

### Reservation rebalance to 16 — real-client PASS with SPR absent

A narrow `LibraryStreamingReservationMixin` keeps Minecraft ownership and preserves the existing combined static+streaming reservation. On the measured 255-channel runtime it changes `247 static + 8 streaming` to `239 static + 16 streaming`; it does not increase the total source budget and does not create/delete HighAudio-owned OpenAL sources.

Frozen conservative Part A2 candidate:

```text
commit: 82c195637de3987463c864c8f8493e9194410094
CI:     34103604455
SHA:    f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

Real `.247` Windows/OpenAL Soft evidence passed 4/4, 8/8, 12/12, and repeated 16/16 allocation. The 16-channel target succeeded five separate times, including explicit-stop cycles. Static Minecraft sounds remained available while streaming was saturated.

Evidence:

`docs/test-batches/evidence/TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md`

`ADR-0009` is still formally Proposed under its current text because exact SPR coexistence/reload has not been run. The user has explicitly asked for minimum manual testing, so a standalone SPR launch should not block current timing work unless new evidence makes it architecture-blocking. Broad SPR acoustic correctness remains a later compatibility milestone.

### Part B immediate vs vector sync comparison — real-client PASS

A single diagnostic compared normal Minecraft/high-level start against a narrow vector start over Minecraft-owned sources at 2/4/8/16 sources.

The user ran the full comparison twice. Both modes measured zero relative `AL_SAMPLE_OFFSET` spread at the sampled start/t+2/t+5/t+10 points through 16 sources. The vector path had full captures, no vector OpenAL errors, no preparation offset leak in the diagnostic, and clean stream closure.

Evidence:

`docs/test-batches/evidence/TEST-BATCH-003-PARTB-NEOFORGE-21.1.247.md`

This result does **not** justify throwing away the vector path: the high-level diagnostic streams were already ready immediately, while future decoded/cached/network media may resolve asynchronously.

## Current timing model — ADR-0010 Proposed

Playback timing is now modeled by **intent**, not one global A/B/C engine mode.

### `immediate`

Meaning: play as soon as the individual sound is ready. This is the default and lowest-latency path.

Leading implementation: normal Minecraft-owned playback with no group barrier or artificial scheduling delay.

Typical use: button sounds, SFX, voice lines, independent ambience, and ordinary one-speaker playback.

### `together`

Meaning: wait until every required local participant is prepared, then start the group together as soon as possible.

Leading implementation: reuse Minecraft-owned channels/sources, hold/rewind the prepared group, then use core OpenAL `alSourcePlayv` once all required participants are ready.

The 16-source vector primitive is already real-client proven. A one-member `together` request should normally collapse to `immediate` because there is nothing local to synchronize against.

### `scheduled`

Meaning: audible media sample zero should line up with a specific HighAudio/session timeline point.

Leading implementation candidate: share the same readiness/arming machinery as `together`, then capability-gate and use OpenAL Soft device-clock scheduling through `alSourcePlayAtTimevSOFT`.

Important semantics:

- scheduled playback is optional and is not the default;
- there is no fixed 100 ms public delay tax;
- the current diagnostic uses a 100 ms silent preparation preroll only because NeoForge exposes the channel after Minecraft has initiated source playback;
- the renderer compensates for that preroll so the meaningful target is media sample zero, not the start of hidden silence;
- source-start device time, media-zero renderer time, and estimated physical-output time are distinct;
- local output latency matters for future session/multi-client "heard at T" semantics;
- raw OpenAL device clocks remain internal and should not be exposed directly to Lua scripts.

A one-speaker ordinary SFX remains `immediate`; a one-speaker `scheduled` request can still be meaningful when synchronizing to an external/session timeline.

See:

`docs/decisions/ADR-0010-playback-timing-intents.md`

## Scheduled/device-clock implementation status

Automatic feasibility is already proven on the exact target stack.

Corrected capability probe:

```text
commit: dc1a62265f1ba4165929938a41e7f460acd70301
CI:     34114885331
```

After the OpenAL device initializes, both `.247` and `.248` CI clients report on Minecraft's sound thread:

```text
sourceStartDelay=true
sourceLatency=true
deviceClock=true
available=true
```

and successfully query the Minecraft-owned OpenAL device clock.

The scheduled harness candidate at `dc2fbaad0f383bbbbe9d17350c003b4e0a53a62f` passed the full automatic `.247/.248` matrix in CI `34115260138` and packaged byte-identical JARs. Bytecode audit confirmed timing control over Minecraft-owned sources while excluding HighAudio-owned source/device/context creation/destruction.

The branch subsequently strengthened scheduled diagnostics so:

- hidden preroll is compensated when defining media-zero timing;
- device output latency is recorded separately;
- `AL_SAMPLE_OFFSET_CLOCK_SOFT` is used for atomic source-offset/device-clock measurement;
- sequential source queries are clock-compensated before group spread is calculated;
- sound-engine reload aborts an in-flight scheduled diagnostic rather than carrying stale device/source state across generations.

Current diagnostic command:

```text
/highaudio_exp3 scheduled <1..16>
```

This command is experiment-only; it is not the final Lua API.

Canonical timing docs/evidence:

- `docs/test-batches/TEST-BATCH-003-TIMING-MODES.md`
- `docs/test-batches/evidence/TEST-BATCH-003-TIMING-C-AUTOMATIC.md`
- `docs/decisions/ADR-0010-playback-timing-intents.md`

## Current implementation branch / CI note

The current development branch is `milestone-003-exp-003-timing-modes`. Resolve its latest head and latest CI when starting a new chat rather than assuming a SHA from this handoff is still current.

The latest timing hardening added package checks for the accessors, `alSourcePlayv`, timed-start/device-clock operations, and the rule that the timing layer must not create/destroy OpenAL sources/devices/contexts. One intermediate hardening run (`34120712452`) failed only because the workflow grepped Java enum **names** which `javac` had inlined as numeric constants; compilation succeeded and the actual expected LWJGL calls were present in the produced bytecode. The follow-up commit `9cc1304f45ba173b295b216f548def4fc9e55c0f` changes the audit to check the actual method calls instead. Re-resolve the follow-up CI result before freezing a new candidate.

## What remains for M3

The renderer architecture is no longer an open three-way backend fork. Minecraft ownership remains the leading boundary.

The remaining M3 work is mainly:

1. keep the strengthened `.247/.248` timing candidate green through full CI/package/client/server checks;
2. re-evaluate the scheduled diagnostic and exact timing semantics after the latest clock/latency corrections;
3. decide whether real audible scheduled-start behavior is still architecture-blocking enough to justify **one** consolidated manual M3 run;
4. if a manual run is justified, make it self-measuring and broad enough to close the remaining M3 timing questions in one launch rather than creating another test ladder;
5. update stale lower-level ledgers after the result, then accept/adjust the relevant ADRs only to the extent the evidence supports.

Long-running drift/underrun correction is not solved by initial-start timing and remains future session/synchronization work.

## Documentation staleness warning

The newer timing documents and real evidence above supersede older M3 wording which may still appear in:

- `docs/PROTOTYPES.md` EXP-003;
- `docs/ROADMAP.md` MILESTONE-003;
- the original `docs/test-batches/TEST-BATCH-003.md` Part B section.

Those older sections still contain pre-result language such as "manual runtime not run", "Part B undecided", or the old A/B/C backend fork. Reconcile them carefully when editing; preserve the rest of the ledgers and avoid replacing/truncating whole long documents just to update one milestone section.

## Manual-test policy

`docs/TESTING.md` is authoritative for cadence: manual Minecraft launches are the expensive last resort. Prefer exact source/bytecode research, build/package checks, automated development-client initialization, CI instrumentation, and self-measuring combined probes first.

Do not request a standalone launch merely to rediscover capability information already proven automatically. If a real-device M3 run becomes necessary, aim for one already-green JAR and one self-running command/session that answers all remaining architecture-blocking questions.

## Still deferred

Production media upload, codecs, ContentId/cache/transfer, server `MediaSession`, public timing/session Lua APIs, long-running drift correction, moving emitters, broad SPR implementation, URL/live streaming, and VS2 support remain later milestone work.
