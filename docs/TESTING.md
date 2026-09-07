# HighAudio testing policy

**Status:** canonical test cadence policy  
**Target stack:** Minecraft 1.21.1 / Java 21 / CC:Tweaked 1.120.0 / NeoForge 21.1.247–21.1.248  
**Last reviewed:** 2026-09-07  
**Search tags:** `TEST-CADENCE`, `MANUAL-TEST`, `CI-TEST`, `BATCHED-RUNTIME-TESTING`

## Goal

Keep manual runtime testing efficient. HighAudio should not ask for a new Minecraft test after every small code edit.

## Default cadence

### Automatic checks — run frequently

These do **not** consume a manual test cycle and should run whenever practical:

- Gradle compilation/build;
- CI against the supported NeoForge compile matrix;
- unit tests where they are cheap and deterministic;
- static validation of resources/configuration;
- source/bytecode/method-supplier inspection when it can answer the question without a user launch;
- dedicated-server startup smoke tests where CI can run them automatically.

### Manual Minecraft/runtime checks — batch them

Manual tests should normally be accumulated into one focused test session after several related edits or at a milestone gate.

Default rule:

1. Make several related implementation changes.
2. Keep automatic build/CI checks green while iterating.
3. Accumulate runtime questions into one checklist.
4. Ask for one manual test session that covers all of them together.
5. Record the result in `PROTOTYPES.md` or the relevant milestone/ADR.

Do **not** request a manual launch for documentation edits, refactors with no behavior change, formatting, or every tiny implementation patch.

## When an immediate manual test is justified

Break the batching rule only when the next implementation decision depends on runtime evidence that cannot reasonably be obtained another way, for example:

- a speaker augmentation mechanism may register but not appear through the real Lua peripheral path;
- a client sound hook may not fire;
- an OpenAL lifecycle behavior is architecture-blocking;
- continuing without the result would likely create throwaway work.

Even then, combine all currently useful observations into the same test build.

## Milestone gate policy

A milestone may contain many code patches but should normally have **one consolidated manual gate session** unless a blocker forces an earlier probe.

For example, `MILESTONE-001` should prefer one probe build that checks method visibility, native speaker regression, direct/wired attachment, lifecycle behavior, turtle/pocket visibility where practical, and both target NeoForge versions rather than separate user tests for each small edit.

## Result recording

Manual sessions should be given stable labels such as:

```text
TEST-BATCH-001
TEST-BATCH-002
```

Each result should record:

- exact JAR/commit;
- exact mod versions;
- scenarios covered;
- pass/fail observations;
- relevant logs;
- what architecture decision the result changes.

## Rule for future development

**Prefer fewer, broader, better-instrumented runtime tests over many narrow manual tests.**

The user provides the subjective/runtime verdict; CI, logs, instrumentation, and source analysis should carry as much technical verification as possible between those sessions.
