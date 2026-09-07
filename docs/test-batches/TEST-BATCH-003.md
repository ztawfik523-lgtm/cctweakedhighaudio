# TEST-BATCH-003 — EXP-003 capacity and synchronization proof

**Status:** PART A RE-AUDITED — strengthened sequencing/refinement patch pending final CI freeze  
**Milestone:** MILESTONE-003  
**Experiment:** EXP-003  
**Branch:** `milestone-003-exp-003-capacity-sync`

This milestone remains proof-first. Do not add production sessions, media upload, codecs, or a broad raw-OpenAL manager while source capacity and synchronization are still unmeasured.

## Previous Part A candidate — superseded before manual test

The earlier automatic candidate was intentionally **not** sent through the manual gate after re-audit:

```text
code/CI commit: abe6063f46077fd74c0a83d05660cf07e0f3a33c
CI run:         34092379023
JAR SHA-256:
3e4b487221a39b13cfe5fc2382c6317c2f4ec60e38b8342fe0fffa3f38991b15
```

It compiled/packaged/booted correctly on both target NeoForge versions, but the measurement procedure had two avoidable weaknesses: it could silently stop a still-running previous probe immediately before the next count, and it only permitted the four baseline counts. The strengthened candidate removes those ambiguities before any user runtime evidence is collected.

## Part A — Minecraft-owned streaming capacity

Use the final strengthened candidate with SPR absent for the initial baseline.

Baseline commands:

```text
/highaudio_exp3 capacity 1
/highaudio_exp3 capacity 4
/highaudio_exp3 capacity 8
/highaudio_exp3 capacity 16
/highaudio_exp3 status
/highaudio_exp3 stop
```

The command accepts **any integer from 1 through 16**. The canonical first pass remains 1, 4, 8, and 16; intermediate values exist only to refine the threshold in the same session if 16 does not fully allocate.

Each capacity run:

- creates the requested number of ordinary Minecraft-owned positional streaming `SoundInstance`s;
- keeps them close to the listener and at low non-zero volume so Minecraft can allocate real channels;
- captures each real `Channel` only through `PlayStreamingSourceEvent`;
- logs requested/captured/active/stopped/closed-stream counts;
- records `SoundManager.getDebugString()` so Minecraft's own source-pool view is preserved;
- records an approximate whole-probe JVM heap delta;
- does not access raw OpenAL source ids.

NeoForge posts `PlayStreamingSourceEvent` only after the Minecraft-owned channel has had the stream attached and `channel.play()` invoked. Therefore a unique capture is evidence of a real allocated/started streaming channel, not merely a `SoundManager.play()` request.

### Important interpretation

This experiment establishes a **conservative HighAudio streaming limit up to the project's 16-source stress target**. It does not claim to discover the device's absolute maximum if 16 succeeds.

- If 16/16 captures and remains active normally, the tested runtime supports **at least 16** concurrent HighAudio streaming channels under those conditions. That is enough to satisfy the current product stress target, but it is not proof that 16 is Minecraft/OpenAL's maximum.
- If 16 does not fully allocate, use intermediate counts in the same session to locate the highest reliable count at or below 16.
- Do not infer the supported source budget from raw OpenAL hardware maximum alone.
- `heapDeltaBytes` includes the generated PCM arrays and ordinary JVM/GC noise; it is an end-to-end observation, **not** per-channel memory cost.

### Sequencing rule

The strengthened probe refuses a new capacity run until the previous one has produced `capacity snapshot phase=final`. This prevents the next measurement from racing sound-thread channel release.

After each command, wait for the final snapshot before starting the next count. `/highaudio_exp3 status` reports `finalized=true` for a completed run.

### Consolidated client procedure

One Minecraft launch is enough.

1. Enter a world with SPR absent and normal master/Records volume above zero.
2. Run `capacity 1`; wait for `phase=final`.
3. Run `capacity 4`; wait for `phase=final`.
4. Run `capacity 8`; wait for `phase=final`.
5. Run `capacity 16`; wait for `phase=final`.
6. If 16 is short of 16 captures, refine with intermediate counts (for example 12, then narrow further) while staying in the same launch.
7. During one high-count run, trigger an ordinary Minecraft/CC:T sound if convenient to observe coexistence. Do not block the baseline if this is awkward.
8. Run `/highaudio_exp3 status` after the final run.
9. Preserve `latest.log` and `debug.log`.

No need to manually count log lines. The instrumentation records snapshots at roughly 5, 20, and 40 client ticks and the final state.

### Part A evidence to preserve

For each count:

- `capacity requested=...`;
- all `capacity channel captured ... captures=X/N` lines;
- `capacity snapshot phase=t+5ticks`;
- `capacity snapshot phase=t+20ticks`;
- `capacity snapshot phase=t+40ticks`;
- `capacity snapshot phase=final`;
- `soundDebug=...`;
- any Minecraft/OpenAL acquisition warning or voice stealing.

A short SPR-on capacity comparison belongs after the clean no-SPR baseline and does not replace the later MILESTONE-010 correctness work.

## Part B — synchronization boundary

Do not implement a broad low-level backend before Part A evidence is understood.

The meaningful candidates remain:

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
- highest risk for SPR and source-pool coexistence.

This remains a fallback only if A and B fail experimentally.

## GATE-003

Pass only when:

- real source/channel capacity is measured well enough to set a conservative provisional limit for the intended product target;
- one synchronization mechanism is selected from measured results rather than preference;
- start skew is measured for 2/4/8/16 sources;
- required private/OpenAL access, if any, is precisely scoped;
- preparation leak, pause/resume, and renderer-offset behavior are documented;
- an alternative is documented if atomic vector start is not reliable through Minecraft ownership.
