# HighAudio finite-media configuration

HighAudio uses one NeoForge common configuration file, `config/cctweakedhighaudio-common.toml`. All settings require a game restart so server, network, and client resource owners use one coherent snapshot.

## Settings and defaults

| Setting | Default | Purpose |
| --- | ---: | --- |
| `server.maxFiniteFileMiB` | 64 MiB | Largest finite upload and WAV container. |
| `server.uploadChunkKiB` | 64 KiB | Largest byte string accepted by one upload write. |
| `server.uploadsPerComputer` | 4 | Incomplete uploads per computer/speaker pair. |
| `server.concurrentUploads` | 64 | Server-wide incomplete-upload count. |
| `server.uploadTimeoutSeconds` | 60 s | Idle time before an incomplete upload expires. |
| `server.inFlightUploadMemoryMiB` | 256 MiB | Aggregate declared bytes reserved by incomplete uploads. |
| `server.contentStoreMiB` | 512 MiB | Transient server content LRU capacity. |
| `server.sessionCap` | 256 | Simultaneous authoritative speaker sessions. |
| `server.playbackRadiusBlocks` | 64 blocks | Play/stop audience and content-request radius. |
| `network.transferChunkKiB` | 32 KiB | Server-to-client content payload chunk. |
| `client.contentCacheMiB` | 256 MiB | Transient client compressed/container LRU capacity. |
| `client.transferMemoryMiB` | 128 MiB | Aggregate client content-assembly reservation. |
| `client.decodedPlaybackMemoryMiB` | 128 MiB | Aggregate decoded PCM retained by active playback. |
| `client.concurrentTransfers` | 8 | Simultaneous client content assemblies. |
| `client.concurrentPlaybacks` | 8 | Simultaneous HighAudio client playbacks. |
| `client.sessionCap` | 256 | Server-announced sessions tracked by one client. |

Count limits and byte budgets are independent. For example, the server may admit up to 64 upload records, but it reserves each upload's complete declared length before allocating and rejects a begin when the 256 MiB aggregate budget has insufficient space.

## Validation and hard ceilings

NeoForge range-validates scalar values and reports corrections; HighAudio logs the complete effective limit snapshot during configuration load. Relationship validation is fail-visible and names both settings and effective values. The maximum file must fit one in-flight upload reservation, the server content store, one client transfer, the client content cache, and the decoded playback budget.

Non-configurable safety ceilings are 256 MiB per file, 1 MiB per Lua upload write, 256 server uploads, 64 uploads per computer/speaker, a 3600-second timeout, 1 GiB for each server aggregate/store pool, 512 MiB for each client cache/transfer/playback pool, 1023 KiB per network chunk, 64 client transfers, 16 client playbacks, 4096 server or client sessions, and a 512-block radius. The playback ceiling matches the proven total-preserving reservation of 16 Minecraft streaming sources. The network ceiling leaves 1 KiB of metadata headroom below Minecraft 1.21.1's 1 MiB clientbound custom-payload limit.

All M4 uploads, content, sessions, transfer assemblies, caches, and decoded playback state remain memory-only. This revision introduces no persisted media state and therefore needs no media migration.
