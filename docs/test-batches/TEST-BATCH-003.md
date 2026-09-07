# TEST-BATCH-003 — EXP-003 capacity and synchronization proof

**Status:** PART A BASELINE PASS — vanilla streaming cap measured at 8; PART A2 SPR-OFF REAL-CLIENT PASS — exact SPR coexistence/reload NEXT  
**Milestone:** MILESTONE-003  
**Experiment:** EXP-003  
**Branch:** `milestone-003-exp-003-capacity-sync-rebalance`

This milestone remains proof-first. Do not add production sessions, media upload, codecs, or a broad raw-OpenAL manager while source capacity and synchronization are still unresolved.

## Previous Part A candidate — superseded before manual test

The earlier automatic candidate was intentionally **not** sent through the manual gate after re-audit:

```text
code/CI commit: abe6063f46077fd74c0a83d05660cf07e0f3a33c
CI run:         34092379023
JAR SHA-256:
3e4b487221a39b13cfe5fc2382c6317c2f4ec60e38b8342fe0fffa3f38991b15
```

It compiled, packaged, and booted correctly on both target NeoForge versions, but the measurement procedure had avoidable weaknesses. It could stop a still-running previous probe immediately before starting the next count, allocated/generated a full 8-second PCM array per source, allowed only the four baseline counts, and mixed sound-thread event mutation with render-thread measurement state. It is therefore superseded **before any manual runtime evidence**.

## Frozen strengthened Part A candidate

```text
code commit:      a500f3bee773e4e5558fe3473367927ece637f9b
CI run:           34095196833
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256 on both matrix legs:
553919083f8d998fd7d3b0e143f8e76ad3da7b78862ee84c93d886110be41055
```

Automatic evidence on both exact target NeoForge versions passed:

- Java 21 / Minecraft 1.21.1 / exact CC:T 1.120.0 compilation;
- strengthened MILESTONE-003 Part A classes packaged, including `Exp3CapacityStream`;
- accepted MILESTONE-002 playback classes/resource retained as regression fixtures;
- accepted EXP-001 GenericSource regression checks;
- development-server startup;
- finished packaged-JAR clean dedicated-server startup;
- no old HighAudio Mixin declaration/config/class reintroduced.

The two matrix artifacts produced byte-identical HighAudio JARs with the SHA-256 above. Direct artifact inspection additionally confirmed the EXP-003 classes contain no raw LWJGL OpenAL/source-control symbols and no `Channel.stopped()` call.

## Part A — Minecraft-owned streaming capacity — PASS

Real NeoForge 21.1.247 client evidence was collected with SPR absent. The strengthened probe was run for requested counts:

```text
1, 1, 2, 4, 6, 8, 10, 12, 16
```

Measured result:

| Requested | Unique channel captures | Active sounds | Minecraft debug streaming side |
|---:|---:|---:|---|
| 1 | 1 | 1 | `1/8` |
| 1 | 1 | 1 | `1/8` |
| 2 | 2 | 2 | `2/8` |
| 4 | 4 | 4 | `4/8` |
| 6 | 6 | 6 | `6/8` |
| 8 | 8 | 8 | `8/8` |
| 10 | 8 | 8 | `8/8` |
| 12 | 8 | 8 | `8/8` |
| 16 | 8 | 8 | `8/8` |

Therefore the tested vanilla Minecraft runtime has a **measured streaming reservation/counter limit of 8 channels**. This is not an OpenAL hardware maximum claim.

Evidence: `docs/test-batches/evidence/TEST-BATCH-003-NEOFORGE-21.1.247.md`.

### Diagnostics validated by the manual run

- each real `PlayStreamingSourceEvent` capture corresponds to an allocated/started Minecraft streaming `Channel`;
- all captures arrived from the `Sound engine` thread;
- counts remained stable at t+5/t+20/t+40 snapshots;
- finalization waited for 10 consecutive inactive client ticks;
- every actually allocated stream closed cleanly;
- attempts to start the next count too early were refused rather than contaminating the measurement;
- no ninth streaming capture appeared for 10/12/16 requests;
- `heapDeltaBytes` remains diagnostic only and is not per-channel memory cost.

### Part A conclusion

Vanilla Minecraft-owned streaming is **insufficient for the project's 16-speaker stress target** without changing the allocation policy. The experiment succeeded because it located the actual runtime boundary cleanly.

Do not respond to this result by immediately building an independent raw-OpenAL source manager.

## Re-evaluation after Part A

Source research and exact runtime evidence support a narrower candidate before the earlier A/B/C backend fork: **rebalance Minecraft's own static/streaming source reservation while keeping Minecraft ownership and the existing total source budget**.

Relevant verified/rechecked points:

- Minecraft 1.21.1 `Library` owns distinct static and streaming channel counters/pools and selects between them on channel acquisition.
- The observed runtime debug string reached `... + 8/8` exactly when allocation stopped.
- Existing source-limit implementations for the 1.21 family modify the two Minecraft channel-pool sizes and expose the streaming reservation separately; this demonstrates feasibility of the mechanism, but HighAudio will not copy their broader total-source-limit behavior.
- HighAudio already proved `SoundInstance + AudioStream + SoundManager` playback and lifecycle in EXP-002.
- SPR 1.21.1 hooks Minecraft `Library`/`SoundEngine`/`Channel`, including `Channel.play()` and the Minecraft-owned source id. Keeping Minecraft-owned channels therefore preserves the integration surface SPR already expects better than independent raw OpenAL ownership would.

### Important correction to earlier interpretation

Do **not** assume `247 + 8` means a fixed 255-source hardware pool in every environment. Minecraft derives policy limits from the device-reported mono-source count and applies its own clamps. The observed runtime has `247` static slots plus `8` streaming slots, but the Part A2 prototype preserves the runtime's existing combined reservation rather than hard-coding a universal 239/16 split.

## Part A2 — streaming reservation rebalance

### Goal

Test whether HighAudio can meet the 16-stream stress target by changing only Minecraft's own reservation policy while preserving:

- Minecraft `Library`/`Channel` ownership;
- `SoundInstance + AudioStream + SoundManager` playback;
- ordinary category/position/pause/reload semantics;
- the runtime's existing combined static+streaming reservation;
- no independent `alGenSources` ownership in HighAudio.

### Prototype rule

The prototype contains one narrow client-side Minecraft-audio Mixin localized under the EXP-003 package. It must **not**:

- raise the total source budget;
- probe/generate additional raw OpenAL sources;
- create a HighAudio-owned source manager;
- alter CC:T speaker internals;
- add codecs/media/session/network behavior.

The policy is conceptually:

```text
original static + original streaming = preserved combined reservation
if vanilla originalStreaming == 8:
    new streaming = min(16, combined reservation - safe static floor)
    new static    = combined reservation - new streaming
else:
    keep the vanilla split unchanged
```

This conservative eligibility rule matters: HighAudio does **not** force 16 streaming slots on a lower-capacity device for which vanilla itself derives fewer than eight. Such a device keeps its vanilla split instead of sacrificing a disproportionate amount of ordinary static/SFX capacity.

On the measured 255-channel runtime the eligible path transforms `247 + 8` into `239 + 16`, but the implementation derives the original reservation from the runtime rather than assuming those constants globally. It also fails loudly if the actual Minecraft constructor arguments do not match the exact vanilla 1.21.1 reservation shape it derived, rather than silently composing with an unknown transform.

### Superseded first automatic Part A2 candidate

The first automatic rebalance candidate was fully green, but a post-pass re-evaluation found that it was too aggressive on hypothetical lower-capacity devices because it could attempt to raise a vanilla reservation below 8 to 16. It was therefore superseded **before any user manual run**.

```text
code/CI commit:    fc4c63efc5377700d71a78683cd123dc60b7d635
CI run:            34102697796
JAR SHA-256:
6a9e1eeb548b7f2b3b985f3357355510d5e8dfcbbb4319d1fbce6c11c0dabe96
```

### Frozen conservative automatic Part A2 candidate — PASS

```text
code/CI commit:    82c195637de3987463c864c8f8493e9194410094
CI run:            34103604455
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256 on both matrix legs:
f1c06daa595bf3a081d4cae36bdc7cadc0bd5cec3bd717bf937d734ee8e74da7
```

Both matrix artifacts are byte-identical. The exact development-client sound-engine initialization smoke passed on both target NeoForge builds and logged:

```text
reportedChannelCount=255
originalStatic=247
originalStreaming=8
newStatic=239
newStreaming=16
combinedPreserved=true
rebalanceApplied=true
targetStreaming=16
```

The CI audio device is OpenAL Soft `No Output`; this proves the exact client Mixin/application/calculation boundary, not audible 16-channel playback on the user's hardware.

Additional automatic checks passed:

- exact Java/MC/CC:T/NeoForge build matrix;
- packaged Mixin config and only the intended `LibraryStreamingReservationMixin`;
- old `SpeakerPeripheralMixin` remains absent;
- M1 GenericSource regression classes and real development-server self-check remain intact;
- M2 and M3 playback/capacity classes/resources remain packaged;
- direct bytecode/symbol inspection found no HighAudio `alGenSources`, `alDeleteSources`, AL10 raw-source ownership, or independent source manager in the reservation patch;
- packaged-JAR dedicated-server startup passed on `.247` and `.248`, confirming the client-only transform does not break server loading.

Independent artifact inspection reconfirmed the two matrix JARs are byte-identical and match the SHA-256 above. No HighAudio/Mixin/OpenAL allocation error was found in the candidate diagnostics.

### Manual Part A2 SPR-off acceptance evidence — PASS

The conservative candidate was run on a real NeoForge 21.1.247 Windows client with SPR absent. OpenAL initialized on:

```text
OpenAL Soft on Speakers (4- USB Audio Device)
```

The runtime logged:

```text
reportedChannelCount=255
originalStatic=247
originalStreaming=8
newStatic=239
newStreaming=16
combinedPreserved=true
rebalanceApplied=true
targetStreaming=16
```

The user exercised:

```text
4, 8, 12, 16, 16, 16, 16, 16
```

Measured result:

| Run | Requested | Unique captures | Early active sounds | Streaming debug | End state |
|---:|---:|---:|---:|---|---|
| 1 | 4 | 4 | 4 | `4/16` | natural final; 4 closed |
| 2 | 8 | 8 | 8 | `8/16` | natural final; 8 closed |
| 3 | 12 | 12 | 12 | `12/16` | natural final; 12 closed |
| 4 | 16 | 16 | 16 | `16/16` | natural final; 16 closed |
| 5 | 16 | 16 | 16 | `16/16` | natural final; 16 closed |
| 6 | 16 | 16 | 16 | `16/16` | explicit stop; 16 closed |
| 7 | 16 | 16 | 16 | `16/16` | explicit stop; 16 closed |
| 8 | 16 | 16 | 16 | `16/16` | natural final; 16 closed |

The 16-channel target therefore succeeded **five separate times**. Every capture reported `eventThread=Sound engine`.

Natural 16-channel runs retained `captures=16`, `activeSounds=16`, `closedStreams=0`, and `soundDebug=... + 16/16` through t+5/t+20/t+40 snapshots. Run 6 also retained all 16 through t+40 before explicit stop. Run 7 was intentionally stopped earlier and therefore showed inactive/closed state by its t+40 snapshot.

All completed runs reached `phase=final` with `inactiveTicks=10` and every allocated stream closed. The two explicit stop cycles cleaned all 16 streams. Attempts to begin a new run before finalization were refused, and no stale-run capture was observed.

Minecraft static-side activity was observed simultaneously with a saturated HighAudio streaming pool, including states such as:

```text
Sounds: 1/239 + 16/16
Sounds: 2/239 + 16/16
```

Across the supplied `latest.log` and `debug.log`, there were no `ERROR` or `FATAL` entries and no HighAudio/OpenAL/Mixin allocation failure. The observed YACL/offline/assets/goat-horn/shader warnings are unrelated to the Part A2 path.

Canonical evidence:

`docs/test-batches/evidence/TEST-BATCH-003-PARTA2-NEOFORGE-21.1.247.md`

### Part A2 SPR-off conclusion

The tested real runtime now supports the project's **16 simultaneous HighAudio streaming-channel stress target** through Minecraft-owned channels while preserving the combined source reservation.

This closes the SPR-off capacity side of Part A2. It does **not** yet prove exact SPR coexistence or reload reapplication on the real device, so ADR-0009 remains Proposed.

### Exact SPR coexistence/reload acceptance evidence — NEXT

Use exact SPR `1.21.1-1.5.1` with the same frozen HighAudio candidate.

In one launch:

```text
/highaudio_exp3 capacity 16
wait for phase=final
play/confirm at least one ordinary Minecraft sound
F3+T
wait for the reload to complete
/highaudio_exp3 capacity 16
wait for phase=final
```

Required success evidence:

- no Mixin application conflict;
- the reservation rebalance applies with SPR present;
- 16 Minecraft-owned streaming channels allocate before reload;
- normal Minecraft static sound remains available;
- F3+T rebuild/reload re-applies the reservation;
- 16 channels allocate again after reload;
- no HighAudio/SPR/OpenAL error;
- HighAudio channels continue through Minecraft `Channel.play()`.

The SPR-on comparison is a capacity/coexistence proof, not the final MILESTONE-010 SPR correctness gate.

If this exact SPR coexistence/reload comparison passes, ADR-0009 may be accepted as the M3 capacity policy and EXP-003 can move to Part B synchronization measurement.

## Part B — synchronization boundary

Part B remains unresolved. Do not choose a synchronization architecture until the exact SPR coexistence/reload comparison closes Part A2.

### Option A — pure Minecraft/high-level scheduling

Pros:

- cleanest lifecycle and compatibility boundary;
- no private OpenAL/source-id coupling.

Cons:

- separate `SoundManager.play(...)` operations may have measurable first-sample skew;
- may not expose precise enough renderer position for drift measurement.

### Option B — narrow accessor/control of Minecraft-owned OpenAL sources

Pros:

- Minecraft still owns allocation/lifecycle/category/spatial setup;
- allows precise source-offset measurement and potentially atomic `alSourcePlayv` on already-owned sources.

Cons:

- localized version-sensitive private/OpenAL coupling;
- must prove prepare/arm does not leak audible samples or fight Minecraft/SPR state.

### Option C — independent raw OpenAL ownership

Pros:

- maximum source/queue/timing control.

Cons:

- duplicates Minecraft source allocation/lifecycle/category/reload/world cleanup responsibilities;
- bypasses the normal Minecraft-owned `Channel.play()` integration surface SPR already hooks;
- highest source-pool coexistence and compatibility risk.

This remains a fallback only if Minecraft-owned approaches fail experimentally.

## GATE-003

Pass only when:

- real vanilla source/channel capacity is preserved as measured evidence;
- the 16-source product target is either met through a proven Minecraft-owned policy or reduced explicitly based on evidence;
- one synchronization mechanism is selected from measured results rather than preference;
- start skew is measured for the supported source counts;
- required private/OpenAL access, if any, is precisely scoped;
- preparation leak, pause/resume, and renderer-offset behavior are documented;
- an alternative is documented if atomic vector start is not reliable through Minecraft ownership.
