# TEST-BATCH-001 — EXP-001 speaker augmentation gate

**Status:** PREPARED / NOT RUN  
**Milestone:** MILESTONE-001  
**Experiment:** EXP-001  
**Branch:** `milestone-001-exp-001`

This is the single consolidated manual runtime session for the additive `SpeakerPeripheral` Mixin. Do not split these observations into separate Minecraft launches unless a failure makes that necessary.

## Automatic evidence required first

For both NeoForge 21.1.247 and 21.1.248, CI must already have:

- compiled the mod against Java 21 / Minecraft 1.21.1 / CC:T 1.120.0;
- verified the packaged Mixin config and `SpeakerPeripheralMixin.class`;
- verified the compiled `highAudioProbe` method retains `@LuaFunction` bytecode metadata;
- launched a dedicated development server;
- observed `[EXP-001] SpeakerPeripheral mixin self-check PASS`;
- observed the server reach its normal `Done (` ready message.

Download the JAR from the matching CI artifact and record the exact commit/JAR SHA-256 before starting the manual batch.

## Test program

Copy `tools/test-batch-001.lua` onto the CC computer/turtle/pocket computer under any convenient filename. Run it with no argument to use the first visible speaker, or pass an explicit peripheral name:

```text
<program>
<program> left
<program> speaker_0
```

Each run checks method discovery, calls `highAudioProbe`, exercises native `playNote`, `playSound`, `playAudio`, and `stop`, then calls the probe again and reports whether the same peripheral instance/source identity remained stable within that run.

The probe returns/logs:

- exact experiment/probe version;
- emitter kind inferred from the exact CC:T runtime subclass;
- runtime class name;
- CC:T's native per-instance speaker source UUID;
- Java object identity hash;
- calling computer ID;
- CC:T attachment name (direct side or remote wired name);
- computer-thread name.

These are diagnostics only. The native source UUID and Java identity hash are **not** being adopted as HighAudio `EmitterId`.

## NeoForge 21.1.247 — broad pass

1. **Direct speaker / initial** — place a normal speaker directly beside a computer and run the program. Save the full output and confirm the three native sounds are audibly produced where applicable.
2. **Direct detach/reattach** — detach/re-attach by changing adjacency or rebooting the computer, then rerun. Record whether `nativeSource` and `identityHash` stay the same while the same speaker block remains loaded.
3. **Wired network** — expose that same speaker through wired modems, run against the remote name, and compare `nativeSource`, `identityHash`, `runtimeClass`, and `emitterKind` with the direct result. There must not be a second/duplicate speaker peripheral identity created by HighAudio.
4. **Chunk unload/reload** — unload the speaker chunk, return, rerun, and record whether CC:T reconstructed the peripheral. Method exposure must still be correct either way.
5. **Break/re-place** — break the speaker and place a new speaker at the same coordinates, rerun, and record the resulting identity behavior. This step observes lifecycle semantics; do not assume coordinate equality means physical identity.
6. **Turtle speaker** — on a turtle with the speaker upgrade, run the program and record whether `highAudioProbe` is exposed and what `emitterKind`/`runtimeClass` report. Native methods must remain usable.
7. **Pocket speaker** — if practical in the same session, run the program on a pocket computer with speaker upgrade and record the same fields.

For every run, save the corresponding server-log `[EXP-001] highAudioProbe ...` line(s).

## NeoForge 21.1.248 — compatibility subset

Repeat the critical subset with the 21.1.248 CI artifact:

1. normal direct speaker;
2. same speaker through wired modem;
3. one detach/reattach or computer reboot;
4. turtle speaker visibility if available;
5. pocket speaker visibility if already practical in the test world.

The automatic CI server smoke already covers dedicated-server Mixin application/startup on both NeoForge versions; this manual subset focuses on Lua exposure, native behavior, and attachment identity.

## Pass/fail recording

Record a result table with at least:

```text
scenario | NF version | target name | emitterKind | runtimeClass | nativeSource | identityHash | probe visible/callable | native methods present | native behavior | notes
```

GATE-001 passes only if:

- `highAudioProbe` is discoverable/callable on the intended normal speaker;
- native `playNote`, `playSound`, `playAudio`, and `stop` remain present and behaviorally usable;
- direct and wired attachment work without a duplicate HighAudio peripheral;
- lifecycle transitions do not lose/corrupt method exposure;
- turtle/pocket exposure is observed rather than guessed;
- both exact NeoForge versions pass their startup/application evidence.

After the batch, update `PROTOTYPES.md`, `ROADMAP.md`, and ADR-0003 from the evidence. Update `VERIFIED-FACTS.md` only for facts actually established by this batch.
