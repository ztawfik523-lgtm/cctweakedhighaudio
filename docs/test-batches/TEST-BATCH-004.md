# TEST-BATCH-004 — M4 finite-WAV vertical slice

**Status:** READY — automated checks complete; one audible real-client run pending  
**Milestone:** MILESTONE-004 — IMPLEMENTED; GATE-004 PENDING  
**Branch:** `codex/milestone-004-vertical-slice`

This is the one consolidated manual gate for the first product slice. Do not split it into separate upload, decode, positional, and stop sessions.

## Candidate prerequisites

- use the exact commit/JAR reported for this batch after the two-version GitHub Actions matrix is green;
- Minecraft 1.21.1, CC:Tweaked 1.120.0, and NeoForge 21.1.247 for the audible baseline;
- Sound Physics Remastered absent;
- one normal placed `computercraft:speaker` attached to one CC computer;
- one clearly audible mono integer-PCM WAV, at most 2 MiB, 8-bit unsigned or 16-bit little-endian signed, with an 8–48 kHz sample rate;
- copy [`../../tools/test-batch-004.lua`](../../tools/test-batch-004.lua) onto the CC computer as `test-batch-004` and copy the WAV into that computer's filesystem.

## One-run procedure

1. Stand close to the placed speaker and run:

   ```text
   test-batch-004 play <speaker-side-or-network-name> <wav-path>
   ```

2. Confirm the program prints a 64-character `ContentId` and a `SessionId`, and the WAV becomes audible from the placed speaker.
3. Move away from and back toward the speaker while it plays. Confirm volume follows the placed position instead of behaving as non-positional/global audio.
4. Before the file finishes naturally, run:

   ```text
   test-batch-004 stop <speaker-side-or-network-name>
   ```

5. Confirm the program reports `HighAudio session stopped` and sound stops promptly. Check the client/server logs for HighAudio, payload, Mixin, sound-engine, or disconnect errors.

Use a WAV long enough to complete steps 2–4 without rushing. No pause, seek, loop, stereo/downmix, multiple speakers, moving emitters, SPR, or long-media test belongs in this gate.

## Pass criteria

- the CC file is accepted through bounded chunked upload without a Lua/runtime error;
- `ContentId` and `SessionId` are returned;
- the real WAV is audibly decoded and played;
- playback is positional at the placed speaker;
- the authoritative stop command ends the active session promptly;
- no M1–M3 regression, disconnect, Mixin, payload, or sound-engine error appears.

Record the exact commit, artifact SHA-256, target versions, observation, and relevant log excerpts under `docs/test-batches/evidence/` only after the run. GATE-004 remains open until that evidence exists.
