# HighAudio testing policy

**Status:** canonical test cadence policy  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `TEST-CADENCE`, `MANUAL-TEST`, `CI-TEST`, `BATCHED-RUNTIME-TESTING`, `MINIMUM-MANUAL-TESTING`

## Goal

Keep manual runtime testing efficient. HighAudio should not ask for a new Minecraft test after every small code edit.

**Manual Minecraft launches are the expensive last resort, not the default verification tool.** Source inspection, bytecode inspection, automated client/server startup, CI instrumentation, and combined diagnostic probes should answer as much as possible first.

## Default cadence

### Automatic checks — run frequently

These do **not** consume a manual test cycle and should run whenever practical:

- Gradle compilation/build;
- CI against the supported NeoForge compile matrix;
- unit tests where they are cheap and deterministic;
- static validation of resources/configuration;
- source/bytecode/method-supplier inspection when it can answer the question without a user launch;
- automated development-client startup under CI when the question is client initialization, Mixin application, sound-engine construction, or other machine-observable state;
- dedicated-server startup smoke tests where CI can run them automatically.

### Manual Minecraft/runtime checks — minimum means minimum

Manual tests should normally be accumulated into **one focused session at the end of a meaningful experiment or milestone slice**, not one launch per uncertainty.

Default rule:

1. Research the exact source/API behavior first.
2. Implement all reasonable competing diagnostic modes in one build when practical.
3. Keep the full automatic matrix green while iterating.
4. Make the diagnostic self-measuring and self-logging so the user does not need to count, time, or inspect internal state manually.
5. Ask for the smallest possible user action, ideally one command in one launch.
6. Reuse the resulting logs to answer all currently relevant questions.
7. Record the result only after it materially changes an architecture decision or closes a gate.

Do **not** request another manual launch merely to:

- confirm a conclusion already established strongly enough for the current milestone;
- test a later-milestone compatibility concern early;
- repeat a count/refinement that the existing diagnostic can derive automatically;
- verify documentation edits or behavior-neutral refactors;
- check each candidate architecture in a separate build when they can coexist in one comparison probe;
- obtain a subjective observation when objective instrumentation can answer the question.

## When an immediate manual test is justified

Break the batching rule only when **all** of the following are true:

1. the result is architecture-blocking **now**;
2. source/bytecode/CI/client-smoke evidence cannot answer it reliably;
3. continuing without it would likely create substantial throwaway implementation;
4. the build already combines every useful observation that can reasonably be collected in that launch.

Examples include:

- a speaker augmentation mechanism may register but not appear through the real Lua peripheral path;
- an audible/render-timing property cannot be established from headless/virtual audio;
- an exact compatibility transform succeeds independently but may conflict only when both real mods are loaded;
- an OpenAL device-specific behavior is directly blocking the current architecture choice.

A merely useful or reassuring manual check is **not** enough reason to interrupt development.

## Milestone gate policy

A milestone may contain many code patches but should normally have **one consolidated manual gate session**, with an additional session only if the first session exposes a genuinely architecture-blocking unknown.

A later milestone's concern should not block the current milestone unless the current design would be reckless without resolving it. For example, broad SPR correctness belongs to its dedicated compatibility milestone; earlier work should rely on source/automatic coexistence evidence unless exact SPR runtime behavior becomes architecture-blocking.

For synchronization experiments, prefer one self-running comparison command that exercises all candidate modes and supported source counts, measures skew/offsets itself, and emits a final machine-readable summary. The user should not need separate launches for high-level scheduling vs narrow Minecraft-owned OpenAL control.

For MILESTONE-004, deterministic tests cover content hashing, upload/store/cache limits and cleanup, and WAV parsing. Automated client/server and packaged-JAR checks cover classloading, Mixins, payload registration, and retained M1–M3 regressions. Audible WAV output, positional perception, and authoritative stop are combined into the single [`test-batches/TEST-BATCH-004.md`](test-batches/TEST-BATCH-004.md) real-client gate.

## Result recording

Manual sessions should be given stable labels such as:

```text
TEST-BATCH-001
TEST-BATCH-002
TEST-BATCH-003
```

Each result should record:

- exact JAR/commit;
- exact mod versions;
- scenarios covered;
- pass/fail observations;
- relevant logs;
- what architecture decision the result changes.

Do not create canonical documentation churn for every intermediate implementation attempt. Preserve significant evidence and final/superseded candidates; keep throwaway iteration history in Git commits/CI rather than repeatedly rewriting the architecture ledger.

## Rule for future development

**Prefer the fewest possible, broadest useful, best-instrumented runtime tests.**

The user should ideally only install one already-green candidate, run one command, and send logs. CI, exact source research, automated clients, logs, instrumentation, and bytecode inspection should carry the technical burden between those sessions.
