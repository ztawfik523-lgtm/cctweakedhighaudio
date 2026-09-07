# TEST-BATCH-003 — EXP-003 capacity and synchronization proof

**Status:** IN PREPARATION — Part A capacity instrumentation implemented; compile/runtime proof pending  
**Milestone:** MILESTONE-003  
**Experiment:** EXP-003  
**Branch:** `milestone-003-exp-003-capacity-sync`

This milestone must remain proof-first. Do not add production sessions, media upload, codecs, or a broad raw-OpenAL manager while source capacity and synchronization are still unmeasured.

## Part A — Minecraft-owned streaming capacity

Current diagnostic command:

```text
/highaudio_exp3 capacity 1
/highaudio_exp3 capacity 4
/highaudio_exp3 capacity 8
/highaudio_exp3 capacity 16
/highaudio_exp3 status
/highaudio_exp3 stop
```

Each capacity run:

- creates the requested number of ordinary Minecraft-owned positional streaming `SoundInstance`s;
- keeps them close to the listener and at low non-zero volume so Minecraft still allocates real channels;
- captures each real `Channel` only through `PlayStreamingSourceEvent`;
- logs requested/captured/active/stopped/closed-stream counts;
- records `SoundManager.getDebugString()` so Minecraft's own source-pool view is preserved;
- records an approximate JVM heap delta;
- does not access raw OpenAL source ids.

The allowed counts are deliberately 1, 4, 8, and 16. Do not infer the supported source budget from OpenAL hardware maximum alone.

### Part A pass evidence to collect

For each count, preserve:

- `capacity requested=...`;
- all `capacity channel captured ... captures=X/N` lines;
- `capacity snapshot phase=t+5ticks`;
- `capacity snapshot phase=t+20ticks`;
- `capacity snapshot phase=t+40ticks`;
- final snapshot after sounds stop;
- any Minecraft/OpenAL acquisition warning or voice stealing;
- one ordinary Minecraft/CC:T sound competing during the 16-source case if convenient.

SPR is not needed for the first baseline. A short SPR-on capacity comparison belongs after the clean no-SPR baseline and does not replace the later MILESTONE-010 correctness work.

## Part B — synchronization boundary

Do not implement a broad low-level backend before Part A is green.

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

- real source/channel capacity is measured well enough to set a conservative provisional limit;
- one synchronization mechanism is selected from measured results rather than preference;
- start skew is measured for 2/4/8/16 sources;
- required private/OpenAL access, if any, is precisely scoped;
- preparation leak, pause/resume, and renderer-offset behavior are documented;
- an alternative is documented if atomic vector start is not reliable through Minecraft ownership.
