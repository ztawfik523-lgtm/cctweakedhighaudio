package dev.ztawfik.cctweakedhighaudio.client.audio.exp2;

import com.mojang.blaze3d.audio.Channel;
import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

/** Client-only state for the deliberately tiny EXP-002 playback probe. */
public final class Exp2AudioController {
    private static GeneratedPcmSound activeSound;
    private static GeneratedPcmStream activeStream;
    private static Channel capturedChannel;

    private static int soundEngineGeneration;
    private static int streamingCaptures;
    private static String lastOutcome = "not-run";

    private Exp2AudioController() {
    }

    public static String play() {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return "EXP-002: no client player/world is active";

        stopExisting(false);

        activeStream = new GeneratedPcmStream();
        activeSound = new GeneratedPcmSound(player.position(), activeStream);
        capturedChannel = null;
        lastOutcome = "play-requested";

        minecraft.getSoundManager().play(activeSound);
        HighAudio.LOGGER.info(
            "[EXP-002] play requested position=({}, {}, {}) format={}Hz/{}bit/mono frames={} engineGeneration={}",
            player.getX(), player.getY(), player.getZ(), GeneratedPcmStream.SAMPLE_RATE,
            GeneratedPcmStream.BITS_PER_SAMPLE, GeneratedPcmStream.DURATION_FRAMES, soundEngineGeneration
        );

        return "EXP-002 chirp requested at your position";
    }

    public static String stop() {
        if (activeSound == null) return "EXP-002: no active probe sound";
        stopExisting(true);
        return "EXP-002 stop requested";
    }

    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        if (!(event.getSound() instanceof GeneratedPcmSound sound)) return;

        streamingCaptures++;
        capturedChannel = event.getChannel();
        if (activeSound == null) activeSound = sound;
        if (activeStream == null) activeStream = sound.stream();
        lastOutcome = "channel-captured";

        HighAudio.LOGGER.info(
            "[EXP-002] PlayStreamingSourceEvent PASS soundClass={} channelClass={} channelIdentity=0x{} engineIdentity=0x{} captures={}",
            sound.getClass().getName(), event.getChannel().getClass().getName(),
            Integer.toHexString(System.identityHashCode(event.getChannel())),
            Integer.toHexString(System.identityHashCode(event.getEngine())), streamingCaptures
        );
    }

    public static void onSoundEngineLoad(SoundEngineLoadEvent event) {
        soundEngineGeneration++;
        var hadActiveSound = activeSound != null;
        var priorBytesRead = activeStream == null ? -1 : activeStream.bytesRead();
        var priorStreamClosed = activeStream != null && activeStream.isClosed();

        activeSound = null;
        activeStream = null;
        capturedChannel = null;
        lastOutcome = "sound-engine-load";

        HighAudio.LOGGER.info(
            "[EXP-002] SoundEngineLoadEvent observed generation={} engineIdentity=0x{} hadActiveSound={} priorBytesRead={} priorStreamClosed={}",
            soundEngineGeneration, Integer.toHexString(System.identityHashCode(event.getEngine())),
            hadActiveSound, priorBytesRead, priorStreamClosed
        );
    }

    public static void tick() {
        if (activeSound == null || capturedChannel == null) return;

        var soundManager = Minecraft.getInstance().getSoundManager();
        if (soundManager.isActive(activeSound)) return;

        var channelStopped = capturedChannel.stopped();
        var bytesRead = activeStream == null ? -1 : activeStream.bytesRead();
        var totalBytes = activeStream == null ? -1 : activeStream.totalBytes();
        var streamClosed = activeStream != null && activeStream.isClosed();
        lastOutcome = "finished:channelStopped=" + channelStopped + ",streamClosed=" + streamClosed
            + ",bytesRead=" + bytesRead + "/" + totalBytes;

        HighAudio.LOGGER.info(
            "[EXP-002] playback inactive channelStopped={} streamClosed={} bytesRead={}/{} engineGeneration={}",
            channelStopped, streamClosed, bytesRead, totalBytes, soundEngineGeneration
        );

        activeSound = null;
        activeStream = null;
        capturedChannel = null;
    }

    public static String status() {
        var soundActive = activeSound != null && Minecraft.getInstance().getSoundManager().isActive(activeSound);
        var channelState = capturedChannel == null ? "none" : Boolean.toString(capturedChannel.stopped());
        var streamState = activeStream == null
            ? "none"
            : activeStream.bytesRead() + "/" + activeStream.totalBytes() + " bytes, closed=" + activeStream.isClosed();

        return "EXP-002 status: active=" + soundActive
            + ", channelStopped=" + channelState
            + ", stream=" + streamState
            + ", captures=" + streamingCaptures
            + ", engineGeneration=" + soundEngineGeneration
            + ", last=" + lastOutcome;
    }

    private static void stopExisting(boolean log) {
        if (activeSound == null) return;

        Minecraft.getInstance().getSoundManager().stop(activeSound);
        lastOutcome = "stop-requested";
        if (log) {
            HighAudio.LOGGER.info(
                "[EXP-002] stop requested channelCaptured={} bytesRead={} engineGeneration={}",
                capturedChannel != null, activeStream == null ? -1 : activeStream.bytesRead(), soundEngineGeneration
            );
        }
    }
}
