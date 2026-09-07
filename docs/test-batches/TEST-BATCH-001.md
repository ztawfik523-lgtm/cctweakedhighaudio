# TEST-BATCH-001 — EXP-001 GenericSource speaker augmentation gate

**Status:** IN PROGRESS — NeoForge 21.1.247 partial runtime evidence captured; remaining lifecycle checks + 21.1.248 manual subset pending  
**Milestone:** MILESTONE-001  
**Experiment:** EXP-001  
**Branch:** `milestone-001-exp-001-genericsource`

This is the single consolidated manual runtime session for the targeted `SpeakerPeripheral` GenericSource approach. Do not split these observations into repeated Minecraft launches unless a failure makes that necessary.

## Automatic evidence required first

The final pre-gate candidate must pass CI on both NeoForge 21.1.247 and 21.1.248. The automatic proof now requires all of the following:

- compile Java 21 / Minecraft 1.21.1 / exact CC:T 1.120.0;
- package `SpeakerGenericSource.class` and `GenericSourceSelfCheck.class`;
- prove the finished HighAudio JAR contains no old HighAudio Mixin declaration, Mixin class, or Mixin JSON;
- use CC:T's exact method supplier to confirm `highAudioProbe` plus native `playNote`, `playSound`, `playAudio`, and `stop` are present for a `SpeakerPeripheral` subtype;
- prove `highAudioProbe` does not leak onto an unrelated `IPeripheral`;
- invoke the generated `highAudioProbe` wrapper automatically and verify `IComputerAccess` injection plus the returned diagnostic map;
- register the GenericSource;
- after CC:T creates its real `ServerContext`, confirm the live peripheral method registry contains `highAudioProbe` plus all four native speaker methods and remains speaker-only;
- launch the NeoGradle development server and reach its normal `Done (` ready message;
- install the exact NeoForge version into a clean dedicated-server directory, copy the finished HighAudio JAR plus exact CC:T 1.120.0 runtime JAR into `mods/`, launch that installed server, and again prove registration, live-registry exposure, and normal ready state;
- record the exact built JAR SHA-256.

Latest frozen candidate evidence:

```text
commit: 93a72cbb13357cd9d9906478998604835e0931b0
CI run: 34082746562
NeoForge 21.1.247: PASS
NeoForge 21.1.248: PASS
JAR SHA-256 on both matrix legs:
0d5478ad27f44b6bf19857372747ae337b0ccf40606ec5f9d3cde71a9014ee64
```

## Test program

Copy `tools/test-batch-001.lua` onto the CC computer/turtle/pocket computer. Run it with no argument to use the first visible speaker, or pass an explicit peripheral name:

```text
<program>
<program> left
<program> speaker_0
```

The script checks method discovery, validates that the probe identifies itself as the GenericSource EXP-001 implementation, calls `highAudioProbe`, exercises native methods, and calls the probe again. Native methods which legitimately return `false` because the speaker is temporarily busy are retried before being considered a failure. Exceptions, missing methods, unexpected return types, or repeated failure remain real failures.

The probe diagnostics include emitter kind, exact runtime class, CC:T's native per-instance source UUID, Java object identity hash, calling computer ID, attachment name, and thread name. These are observations only; none are adopted as the final HighAudio `EmitterId`.

Before running the gate, ensure CC:T's `disabled_generic_methods` setting is not deliberately disabling `cctweakedhighaudio:speaker` or its `highAudioProbe` method.

## NeoForge 21.1.247 — broad pass

1. **Direct speaker / initial** — normal speaker directly beside a computer. Run the script and save output. Confirm the native note/sound/audio checks are audible where applicable.
2. **Direct detach/reattach** — reboot the computer or otherwise detach/reattach without replacing the speaker, rerun, and compare diagnostics while the same block remains.
3. **Wired network** — expose that same speaker through wired modems, run against its remote name, and compare diagnostics with direct access. HighAudio must not create a second speaker peripheral.
4. **Chunk unload/reload** — unload the speaker chunk, return, rerun, and confirm method exposure still works.
5. **Break/re-place** — break and place a new speaker at the same coordinates, rerun, and record identity behavior. This observes lifecycle semantics; it does not define the future HighAudio `EmitterId` policy.
6. **Turtle speaker** — run on a turtle with speaker upgrade; record visibility, emitter kind, runtime class, and native behavior.
7. **Pocket speaker** — if practical in the same session, repeat for a pocket computer with speaker upgrade.

For every run, preserve corresponding server `[EXP-001] highAudioProbe ...` log lines where practical.

### 2026-09-07 runtime evidence captured

Detailed evidence is recorded in:

`docs/test-batches/evidence/TEST-BATCH-001-NEOFORGE-21.1.247.md`

Current 21.1.247 assessment:

- **PASS:** real direct block speaker exposes/calls `highAudioProbe` and completes the native `playNote` / `playSound` / `playAudio` / `stop` sequence.
- **PASS:** real wired remote speaker (`speaker_27`) exposes/calls `highAudioProbe` and completes the native sequence.
- **PASS:** real turtle speaker exposes/calls `highAudioProbe` and completes the native sequence.
- **PASS:** newly-created block/turtle speaker peripheral instances still receive the GenericSource method and complete the native sequence.
- **PENDING:** the supplied logs do not prove the wired remote was the same physical speaker previously tested directly.
- **PENDING:** the move-away/return attempt did not prove a real chunk unload/reconstruction; the original direct speaker retained the exact same Java object identity and CC:T native source UUID.
- **PENDING:** pocket speaker exposure was not observed.
- **AMBIGUOUS:** the supplied logs show peripheral recreation, but do not identify the exact user action which caused each recreation, so reboot/detach and break/re-place labels are not assigned without proof.

## NeoForge 21.1.248 — compatibility subset

Repeat:

1. normal direct speaker;
2. same speaker through wired modem;
3. one detach/reattach or reboot;
4. turtle visibility if available;
5. pocket visibility if already practical.

Automatic CI already covers build, generated-call behavior, speaker-only targeting, registration, the live CC:T method registry, and both development-server and installed packaged-JAR server startup on both exact NeoForge versions. This manual subset focuses on real Lua exposure, native behavior, and peripheral lifecycle behavior.

## GATE-001 pass conditions

- `highAudioProbe` is discoverable/callable on the intended normal speaker;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain present and usable;
- direct and wired access work without a duplicate HighAudio peripheral;
- lifecycle transitions do not lose/corrupt method exposure;
- turtle/pocket exposure is observed rather than guessed;
- both exact NeoForge versions pass the strengthened automatic evidence and their manual coverage above.

After the batch, update `PROTOTYPES.md`, `ROADMAP.md`, and ADR-0008. `VERIFIED-FACTS.md` only receives facts actually established by evidence.
