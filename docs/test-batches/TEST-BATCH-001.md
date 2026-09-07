# TEST-BATCH-001 — EXP-001 GenericSource speaker augmentation gate

**Status:** PASS — GATE-001 complete  
**Milestone:** MILESTONE-001  
**Experiment:** EXP-001  
**Branch:** `milestone-001-exp-001-genericsource`

This is the consolidated runtime gate for the targeted `SpeakerPeripheral` GenericSource approach.

## Automatic evidence

The frozen candidate passed CI on both NeoForge 21.1.247 and 21.1.248. The automatic proof covers:

- Java 21 / Minecraft 1.21.1 / exact CC:T 1.120.0 compilation;
- packaged `SpeakerGenericSource.class` and `GenericSourceSelfCheck.class`;
- no old HighAudio Mixin declaration/class/JSON in the finished JAR;
- exact CC:T method-supplier generation of `highAudioProbe` for a `SpeakerPeripheral` subtype;
- native `playNote`, `playSound`, `playAudio`, and `stop` retained in the generated method map;
- no `highAudioProbe` leakage onto an unrelated `IPeripheral`;
- generated `highAudioProbe` wrapper invocation with `IComputerAccess` injection;
- GenericSource registration;
- live CC:T `ServerContext` registry exposure after configuration filtering;
- NeoGradle development-server startup;
- clean installed packaged-JAR dedicated-server startup;
- exact built JAR SHA-256.

Frozen evidence:

```text
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI run: 34082746562
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256 on both matrix legs:
0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

## Test program

`tools/test-batch-001.lua` checks method discovery, validates the GenericSource EXP-001 probe, calls `highAudioProbe`, exercises native speaker methods, and calls the probe again. Temporary native `false`/busy returns are retried before being considered failures.

The probe diagnostics include emitter kind, exact runtime class, CC:T native per-instance source UUID, Java object identity hash, calling computer ID, attachment name, and thread name. These remain diagnostics only and are not the final HighAudio `EmitterId`.

## NeoForge 21.1.247 — broad real-client pass

Detailed evidence is recorded in:

`docs/test-batches/evidence/TEST-BATCH-001-NEOFORGE-21.1.247.md`

Observed PASS coverage:

- real direct block speakers expose/call `highAudioProbe` and complete the native `playNote` / `playSound` / `playAudio` / `stop` sequence;
- real wired remote speakers expose/call `highAudioProbe` and complete the native sequence;
- real turtle speakers expose/call `highAudioProbe` and complete the native sequence, including after peripheral recreation;
- a real `PocketSpeakerPeripheral` (`attachment=back`) exposes/calls `highAudioProbe` and completes multiple full native-method test runs;
- newly-created/reconstructed block-speaker peripheral instances continue to receive the GenericSource method and native methods;
- the deterministic lifecycle follow-up disabled spawn-chunk retention with `/gamerule spawnChunkRadius 0`; the same wired remote name later resolved to a new CC:T source UUID and Java object identity and successfully completed the test again;
- no HighAudio-specific runtime exception was observed in the supplied `latest.log` or `debug.log`.

## NeoForge 21.1.248 — compatibility evidence and manual waiver

A second gameplay session on 21.1.248 is **not claimed to have occurred**.

It is waived as redundant after re-audit because:

1. the exact frozen candidate passed compilation and all strengthened GenericSource/self-check logic on NeoForge 21.1.248;
2. the NeoForge 21.1.248 development server reached ready state with live `ServerContext` GenericSource exposure;
3. the finished packaged HighAudio JAR plus exact CC:T 1.120.0 was installed into a clean NeoForge 21.1.248 dedicated server and reached ready state with the same live-registry checks;
4. NeoForge's official 21.1.248 changelog shows the only change after 21.1.247 is a `SolidBucketItem#getPlaceSound` backport, unrelated to CC:T GenericSource/peripheral dispatch.

This is sufficient compatibility evidence for EXP-001 because the broad real-client behavior was already established on the baseline 21.1.247 runtime and the compatibility build was exercised with the exact candidate in both development and installed packaged-server contexts.

## GATE-001 result

**PASS.**

Established:

- `highAudioProbe` is discoverable/callable on intended CC:T speaker implementations;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain present and usable;
- direct and wired access work without a duplicate HighAudio peripheral;
- speaker peripheral reconstruction does not lose/corrupt method exposure;
- turtle and pocket speaker exposure is observed rather than guessed;
- both exact NeoForge target versions pass the strengthened automatic/runtime compatibility evidence.

`ADR-0008` is accepted and MILESTONE-001 may proceed to MILESTONE-002.
