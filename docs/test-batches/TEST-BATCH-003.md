# TEST-BATCH-003 — EXP-003 capacity and synchronization proof

**Status:** PART A READY — exact automatic candidate PASS; real client capacity measurements pending  
**Milestone:** MILESTONE-003  
**Experiment:** EXP-003  
**Branch:** `milestone-003-exp-003-capacity-sync`

This milestone remains proof-first. Do not add production sessions, media upload, codecs, or a broad raw-OpenAL manager while source capacity and synchronization are still unmeasured.

## Frozen Part A candidate

```text
code/CI commit: abe6063f46077fd74c0a83d05660cf07e0f3a33c
CI run:         34092379023
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256 on both matrix legs:
3e4b487221a39b13cfe5fc2382c6317c2f4ec60e38b8342fe0fffa3f38991b15
```

Automatic evidence on both target NeoForge versions passed:

- Java 21 / Minecraft 1.21.1 / exact CC:T 1.120.0 compilation;
- packaged MILESTONE-003 Part A classes;
- accepted MILESTONE-002 playback classes/resource retained as regression fixtures;
- accepted EXP-001 GenericSource regression checks;
- development-server startup;
- finished packaged-JAR clean dedicated-server startup;
- no old HighAudio Mixin declaration/config/class reintroduced.

## Part A — Minecraft-owned streaming capacity

Use the exact frozen candidate above with SPR absent for the initial baseline.

Commands:

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
- keeps them close to the listener and at low non-zero volume so Minecraft can allocate real channels;
- captures each real `Channel` only through `PlayStreamingSourceEvent`;
- logs requested/captured/active/stopped/closed-stream counts;
- records `SoundManager.getDebugString()` so Minecraft's own source-pool view is preserved;
- records an approximate JVM heap delta;
- does not access raw OpenAL source ids.

The allowed counts are deliberately 1, 4, 8, and 16. Do not infer the supported source budget from OpenAL hardware maximum alone.

### Consolidated client procedure

One Minecraft launch is enough.

1. Enter a world with SPR absent and normal master/Records volume above zero.
2. Run `capacity 1`; let it finish.
3. Run `capacity 4`; let it finish.
4. Run `capacity 8`; let it finish.
5. Run `capacity 16`; let it finish.
6. During one 16-source run, trigger an ordinary Minecraft/CC:T sound if convenient to observe coexistence. Do not block the baseline if this is awkward.
7. Run `/highaudio_exp3 status` after the final run.
8. Preserve `latest.log` and `debug.log`.

No need to manually count log lines. The instrumentation records snapshots at roughly 5, 20, and 40 client ticks and the final state.

### Part A evidence to preserve

For each count:

- `capacity requested=...`;
- all `capacity channel captured ... captures=X/N` lines;
- `capacity snapshot phase=t+5ticks`;
- `capacity snapshot phase=t+20ticks`;
- `capacity snapshot phase=t+40ticks`;
- final snapshot after sounds stop;
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

- real source/channel capacity is measured well enough to set a conservative provisional limit;
- one synchronization mechanism is selected from measured results rather than preference;
- start skew is measured for 2/4/8/16 sources;
- required private/OpenAL access, if any, is precisely scoped;
- preparation leak, pause/resume, and renderer-offset behavior are documented;
- an alternative is documented if atomic vector start is not reliable through Minecraft ownership.
