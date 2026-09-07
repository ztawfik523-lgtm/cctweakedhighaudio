# TEST-BATCH-003 — EXP-003 capacity and synchronization proof

**Status:** PART A BASELINE PASS — vanilla streaming cap measured at 8; PART A2 REBALANCE PROTOTYPE NEXT  
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

Source research and exact runtime evidence now support a narrower fourth candidate before the earlier A/B/C backend fork: **rebalance Minecraft's own static/streaming source reservation while keeping Minecraft ownership and the existing total source budget**.

Relevant verified/rechecked points:

- Minecraft 1.21.1 `Library` owns distinct static and streaming channel counters/pools and selects between them on channel acquisition.
- The observed runtime debug string reached `... + 8/8` exactly when allocation stopped.
- Existing source-limit implementations for the 1.21 family modify the two Minecraft channel-pool sizes and expose the streaming reservation separately; this demonstrates feasibility of the mechanism, but HighAudio will not copy their broader total-source-limit behavior.
- HighAudio already proved `SoundInstance + AudioStream + SoundManager` playback and lifecycle in EXP-002.
- SPR 1.21.1 hooks Minecraft `Library`/`SoundEngine`/`Channel`, including `Channel.play()` and the Minecraft-owned source id. Keeping Minecraft-owned channels therefore preserves the integration surface SPR already expects better than independent raw OpenAL ownership would.

### Important correction to earlier interpretation

Do **not** assume `247 + 8` means a fixed 255-source hardware pool in every environment. Minecraft derives policy limits from the device-reported mono-source count and applies its own clamps. The observed runtime has `247` static slots plus `8` streaming slots, but the Part A2 prototype must preserve the runtime's existing combined reservation rather than hard-code a universal 239/16 split.

## Part A2 — streaming reservation rebalance

### Goal

Test whether HighAudio can meet the 16-stream stress target by changing only Minecraft's own reservation policy while preserving:

- Minecraft `Library`/`Channel` ownership;
- `SoundInstance + AudioStream + SoundManager` playback;
- ordinary category/position/pause/reload semantics;
- the runtime's existing combined static+streaming reservation;
- no independent `alGenSources` ownership in HighAudio.

### Prototype rule

The prototype may contain one narrow client-side Minecraft-audio Mixin/access patch localized under an EXP-003 package. It must **not**:

- raise the total source budget;
- probe/generate additional raw OpenAL sources;
- create a HighAudio-owned source manager;
- alter CC:T speaker internals;
- add codecs/media/session/network behavior.

The intended policy is conceptually:

```text
original static + original streaming = preserved combined reservation
new streaming = min(16, combined reservation - safe static floor)
new static    = combined reservation - new streaming
```

On the measured runtime this is expected to transform `247 + 8` into `239 + 16`, but the implementation must derive the values from the runtime rather than assuming those constants globally.

### Automatic gate before another manual launch

The Part A2 candidate must prove in CI/code inspection that:

- only the intended Minecraft audio initialization boundary is transformed;
- the Mixin applies against NeoForge 21.1.247 and 21.1.248 development runtime;
- packaged-JAR dedicated-server startup remains clean/client-only;
- M1 GenericSource regression checks remain intact;
- M2/M3 playback classes remain packaged;
- no raw `alGenSources`/`alDeleteSources` ownership is introduced;
- diagnostic logging records original and rebalanced static/streaming limits on every sound-engine load/reload.

### Manual Part A2 acceptance evidence

With SPR absent first:

```text
/highaudio_exp3 capacity 8
wait for final
/highaudio_exp3 capacity 16
wait for final
```

Expected success evidence:

```text
rebalance originalStatic=... originalStreaming=8
rebalance newStatic=... newStreaming=16
capacity requested=16
captures=16
activeSounds=16
soundDebug=... + 16/16
```

Then perform one short SPR-on repeat only after the clean baseline proves 16 allocation. Required SPR-on checks:

- no Mixin application conflict;
- 16 Minecraft-owned streaming channels still allocate;
- HighAudio channels continue through Minecraft `Channel.play()`;
- no HighAudio/SPR/OpenAL error;
- normal Minecraft sounds still play;
- F3+T/device sound-engine rebuild re-applies the reservation and returns to a clean state.

The SPR-on comparison is a capacity/coexistence proof, not the final MILESTONE-010 SPR correctness gate.

## Part B — synchronization boundary

Part B remains unresolved. Do not choose a synchronization architecture until Part A2 either succeeds or fails.

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
