# TEST-BATCH-001 — EXP-001 GenericSource speaker augmentation gate

**Status:** PREPARED / NOT RUN  
**Milestone:** MILESTONE-001  
**Experiment:** EXP-001  
**Branch:** `milestone-001-exp-001-genericsource`

This is the single consolidated manual runtime session for the targeted `SpeakerPeripheral` GenericSource approach. Do not split these observations into repeated Minecraft launches unless a failure makes that necessary.

## Automatic evidence required first

For both NeoForge 21.1.247 and 21.1.248, CI must already have:

- compiled Java 21 / Minecraft 1.21.1 / exact CC:T 1.120.0;
- packaged `SpeakerGenericSource.class` and `GenericSourceSelfCheck.class`;
- used CC:T's exact method supplier to confirm `highAudioProbe` plus native `playNote`, `playSound`, `playAudio`, and `stop` are all generated/present for a `SpeakerPeripheral` subtype;
- registered the GenericSource;
- launched a dedicated development server;
- observed `[EXP-001] GenericSource method-supplier self-check PASS`;
- observed the server reach its normal `Done (` ready message;
- recorded the exact built JAR SHA-256.

Use the matching CI artifact for the manual batch.

## Test program

Copy `tools/test-batch-001.lua` onto the CC computer/turtle/pocket computer. Run it with no argument to use the first visible speaker, or pass an explicit peripheral name:

```text
<program>
<program> left
<program> speaker_0
```

The script checks method discovery, calls `highAudioProbe`, exercises native methods, and calls the probe again. Native methods which legitimately return `false` because the speaker is temporarily busy are retried before being considered a failure.

The probe diagnostics include emitter kind, exact runtime class, CC:T's native per-instance source UUID, Java object identity hash, calling computer ID, attachment name, and thread name. These are observations only; none are adopted as the final HighAudio `EmitterId`.

Before running the gate, ensure CC:T's `disabled_generic_methods` setting is not deliberately disabling `cctweakedhighaudio:speaker` or its `highAudioProbe` method.

## NeoForge 21.1.247 — broad pass

1. **Direct speaker / initial** — normal speaker directly beside a computer. Run the script and save output. Confirm native sounds are audible where applicable.
2. **Direct detach/reattach** — reboot/change adjacency, rerun, and compare diagnostic identity while the same block remains.
3. **Wired network** — expose that same speaker through wired modems, run against its remote name, and compare diagnostics with direct access. HighAudio must not create a second speaker peripheral.
4. **Chunk unload/reload** — unload the speaker chunk, return, rerun, and confirm method exposure still works.
5. **Break/re-place** — break and place a new speaker at the same coordinates, rerun, and record identity behavior.
6. **Turtle speaker** — run on a turtle with speaker upgrade; record visibility, emitter kind, runtime class, and native behavior.
7. **Pocket speaker** — if practical in the same session, repeat for pocket speaker.

For every run, preserve corresponding server `[EXP-001] highAudioProbe ...` log lines.

## NeoForge 21.1.248 — compatibility subset

Repeat:

1. normal direct speaker;
2. same speaker through wired modem;
3. one detach/reattach or reboot;
4. turtle visibility if available;
5. pocket visibility if already practical.

Automatic CI already covers build, registration, method-supplier generation, and dedicated-server startup on both exact NeoForge versions. This manual subset focuses on real Lua exposure and peripheral lifecycle behavior.

## GATE-001 pass conditions

- `highAudioProbe` is discoverable/callable on the intended normal speaker;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain present and usable;
- direct and wired access work without a duplicate HighAudio peripheral;
- lifecycle transitions do not lose/corrupt method exposure;
- turtle/pocket exposure is observed rather than guessed;
- both exact NeoForge versions pass startup evidence.

After the batch, update `PROTOTYPES.md`, `ROADMAP.md`, and ADR-0008. `VERIFIED-FACTS.md` only receives facts actually established by evidence.
