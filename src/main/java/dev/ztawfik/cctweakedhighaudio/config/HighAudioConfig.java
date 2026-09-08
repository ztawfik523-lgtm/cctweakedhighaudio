package dev.ztawfik.cctweakedhighaudio.config;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.media.MediaLimits;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** One restart-bound common config for the complete finite-media resource policy. */
public final class HighAudioConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.IntValue MAX_FILE_MIB;
    private static final ModConfigSpec.IntValue UPLOAD_CHUNK_KIB;
    private static final ModConfigSpec.IntValue UPLOADS_PER_COMPUTER;
    private static final ModConfigSpec.IntValue CONCURRENT_SERVER_UPLOADS;
    private static final ModConfigSpec.IntValue UPLOAD_TIMEOUT_SECONDS;
    private static final ModConfigSpec.IntValue IN_FLIGHT_UPLOAD_MEMORY_MIB;
    private static final ModConfigSpec.IntValue SERVER_CONTENT_STORE_MIB;
    private static final ModConfigSpec.IntValue NETWORK_TRANSFER_CHUNK_KIB;
    private static final ModConfigSpec.IntValue CLIENT_CONTENT_CACHE_MIB;
    private static final ModConfigSpec.IntValue CLIENT_TRANSFER_MEMORY_MIB;
    private static final ModConfigSpec.IntValue CLIENT_PLAYBACK_MEMORY_MIB;
    private static final ModConfigSpec.IntValue CONCURRENT_CLIENT_TRANSFERS;
    private static final ModConfigSpec.IntValue CONCURRENT_CLIENT_PLAYBACKS;
    private static final ModConfigSpec.IntValue SERVER_SESSION_CAP;
    private static final ModConfigSpec.IntValue PLAYBACK_RADIUS_BLOCKS;
    private static final ModConfigSpec.IntValue CLIENT_SESSION_CAP;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment("HighAudio finite-media limits. Changes require a game restart.");

        builder.push("server");
        MAX_FILE_MIB = integer(builder, "maxFiniteFileMiB", 64, 1, 256,
            "Largest accepted finite upload and WAV container, in MiB.");
        UPLOAD_CHUNK_KIB = integer(builder, "uploadChunkKiB", 64, 1, 1024,
            "Largest byte string accepted by one highAudioUploadWrite call, in KiB.");
        UPLOADS_PER_COMPUTER = integer(builder, "uploadsPerComputer", 4, 1, 64,
            "Maximum incomplete uploads owned by one computer/speaker pair.");
        CONCURRENT_SERVER_UPLOADS = integer(builder, "concurrentUploads", 64, 1, 256,
            "Maximum incomplete uploads across the server; the memory budget is enforced independently.");
        UPLOAD_TIMEOUT_SECONDS = integer(builder, "uploadTimeoutSeconds", 60, 1, 3600,
            "Seconds without a write before an incomplete upload expires.");
        IN_FLIGHT_UPLOAD_MEMORY_MIB = integer(builder, "inFlightUploadMemoryMiB", 256, 1, 1024,
            "Aggregate memory reserved by all incomplete uploads, in MiB.");
        SERVER_CONTENT_STORE_MIB = integer(builder, "contentStoreMiB", 512, 1, 1024,
            "Capacity of the transient server content LRU, in MiB.");
        SERVER_SESSION_CAP = integer(builder, "sessionCap", 256, 1, 4096,
            "Maximum simultaneous authoritative HighAudio speaker sessions.");
        PLAYBACK_RADIUS_BLOCKS = integer(builder, "playbackRadiusBlocks", 64, 1, 512,
            "Radius in blocks for play/stop announcements and content-request authorization.");
        builder.pop();

        builder.push("network");
        NETWORK_TRANSFER_CHUNK_KIB = integer(builder, "transferChunkKiB", 32, 1, 1023,
            "Bytes per server-to-client content packet, in KiB. The ceiling leaves protocol overhead below Minecraft's 1 MiB clientbound custom-payload limit.");
        builder.pop();

        builder.push("client");
        CLIENT_CONTENT_CACHE_MIB = integer(builder, "contentCacheMiB", 256, 1, 512,
            "Capacity of the transient compressed/container content LRU, in MiB.");
        CLIENT_TRANSFER_MEMORY_MIB = integer(builder, "transferMemoryMiB", 128, 1, 512,
            "Aggregate memory reserved while assembling content transfers, in MiB.");
        CLIENT_PLAYBACK_MEMORY_MIB = integer(builder, "decodedPlaybackMemoryMiB", 128, 1, 512,
            "Aggregate decoded PCM retained by active HighAudio playback streams, in MiB.");
        CONCURRENT_CLIENT_TRANSFERS = integer(builder, "concurrentTransfers", 8, 1, 64,
            "Maximum simultaneous content assemblies on one client.");
        CONCURRENT_CLIENT_PLAYBACKS = integer(builder, "concurrentPlaybacks", 8, 1, 16,
            "Maximum simultaneous HighAudio playbacks. The hard ceiling matches the proven reserved streaming-source capacity.");
        CLIENT_SESSION_CAP = integer(builder, "sessionCap", 256, 1, 4096,
            "Maximum server-announced HighAudio sessions tracked by one client.");
        builder.pop();

        SPEC = builder.build();
    }

    private HighAudioConfig() {
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != SPEC) return;
        var values = values();
        MediaLimits.install(values);
        HighAudio.LOGGER.info("Loaded effective HighAudio limits: {}", values);
    }

    private static MediaLimits.Values values() {
        return new MediaLimits.Values(
            mib(MAX_FILE_MIB),
            kib(UPLOAD_CHUNK_KIB),
            UPLOADS_PER_COMPUTER.getAsInt(),
            CONCURRENT_SERVER_UPLOADS.getAsInt(),
            UPLOAD_TIMEOUT_SECONDS.getAsInt(),
            mib(IN_FLIGHT_UPLOAD_MEMORY_MIB),
            mib(SERVER_CONTENT_STORE_MIB),
            kib(NETWORK_TRANSFER_CHUNK_KIB),
            mib(CLIENT_CONTENT_CACHE_MIB),
            mib(CLIENT_TRANSFER_MEMORY_MIB),
            mib(CLIENT_PLAYBACK_MEMORY_MIB),
            CONCURRENT_CLIENT_TRANSFERS.getAsInt(),
            CONCURRENT_CLIENT_PLAYBACKS.getAsInt(),
            SERVER_SESSION_CAP.getAsInt(),
            PLAYBACK_RADIUS_BLOCKS.getAsInt(),
            CLIENT_SESSION_CAP.getAsInt()
        );
    }

    private static ModConfigSpec.IntValue integer(
        ModConfigSpec.Builder builder,
        String name,
        int defaultValue,
        int minimum,
        int maximum,
        String comment
    ) {
        return builder.comment(comment, "Supported range: " + minimum + ".." + maximum + ". Out-of-range values are corrected visibly by NeoForge.")
            .gameRestart()
            .defineInRange(name, defaultValue, minimum, maximum);
    }

    private static int mib(ModConfigSpec.IntValue value) {
        return Math.multiplyExact(value.getAsInt(), MediaLimits.MIB);
    }

    private static int kib(ModConfigSpec.IntValue value) {
        return Math.multiplyExact(value.getAsInt(), MediaLimits.KIB);
    }
}
