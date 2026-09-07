package dev.ztawfik.cctweakedhighaudio.integration.cct;

import dan200.computercraft.api.lua.GenericSource;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.integration.cct.mixin.SpeakerPeripheralAccess;
import dev.ztawfik.cctweakedhighaudio.media.ContentId;
import dev.ztawfik.cctweakedhighaudio.media.MediaLimits;
import dev.ztawfik.cctweakedhighaudio.media.UploadException;
import dev.ztawfik.cctweakedhighaudio.media.UploadManager;
import dev.ztawfik.cctweakedhighaudio.server.MediaServerRuntime;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * EXP-001: add a diagnostic method through CC:T's GenericSource method system.
 *
 * <p>The registration API is public, while the target type is intentionally the exact CC:T 1.120.0
 * implementation class. Keeping this class under integration/cct localizes that version-sensitive coupling.</p>
 */
public final class SpeakerGenericSource implements GenericSource {
    @Override
    public String id() {
        return "cctweakedhighaudio:speaker";
    }

    @LuaFunction
    public static Map<String, Object> highAudioProbe(SpeakerPeripheral speaker, IComputerAccess computer) {
        var runtimeClass = speaker.getClass().getName();
        var emitterKind = "speaker";
        if (runtimeClass.equals("dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral")) {
            emitterKind = "block";
        } else if (runtimeClass.equals("dan200.computercraft.shared.turtle.upgrades.TurtleSpeaker$Peripheral")) {
            emitterKind = "turtle";
        } else if (runtimeClass.equals("dan200.computercraft.shared.pocket.peripherals.PocketSpeakerPeripheral")) {
            emitterKind = "pocket";
        }

        var nativeSource = speaker.getSource().toString();
        var identityHash = "0x" + Integer.toHexString(System.identityHashCode(speaker));
        var attachmentName = computer.getAttachmentName();
        var computerId = computer.getID();
        var threadName = Thread.currentThread().getName();

        var result = new LinkedHashMap<String, Object>();
        result.put("experiment", "EXP-001");
        result.put("probeVersion", 1);
        result.put("integration", "generic_source");
        result.put("emitterKind", emitterKind);
        result.put("runtimeClass", runtimeClass);
        result.put("nativeSource", nativeSource);
        result.put("identityHash", identityHash);
        result.put("computerId", computerId);
        result.put("attachment", attachmentName);
        result.put("thread", threadName);

        HighAudio.LOGGER.info(
            "[EXP-001] highAudioProbe computerId={} attachment={} emitterKind={} runtimeClass={} nativeSource={} identityHash={} thread={}",
            computerId, attachmentName, emitterKind, runtimeClass, nativeSource, identityHash, threadName
        );

        return result;
    }

    @LuaFunction
    public static String highAudioUploadBegin(
        SpeakerPeripheral speaker,
        IComputerAccess computer,
        int expectedBytes
    ) throws LuaException {
        ensurePlacedSpeaker(speaker);
        try {
            return MediaServerRuntime.beginUpload(owner(speaker, computer), expectedBytes).toString();
        } catch (UploadException error) {
            throw new LuaException(error.getMessage());
        }
    }

    @LuaFunction
    public static int highAudioUploadWrite(
        SpeakerPeripheral speaker,
        IComputerAccess computer,
        IArguments arguments
    ) throws LuaException {
        ensurePlacedSpeaker(speaker);
        var uploadId = parseUuid(arguments.getString(0), "upload id");
        var owner = owner(speaker, computer);
        ByteBuffer bytes = arguments.getBytes(1);
        if (bytes.remaining() > MediaLimits.MAX_UPLOAD_CHUNK_BYTES) {
            try {
                MediaServerRuntime.abortUpload(owner, uploadId);
            } catch (UploadException ignored) {
                // The size error is the useful failure for this call.
            }
            throw new LuaException("Upload chunk exceeds the 16 KiB limit; the upload was aborted");
        }

        // Never retain CC:T's argument-backed ByteBuffer beyond this invocation.
        var copiedChunk = new byte[bytes.remaining()];
        bytes.get(copiedChunk);
        try {
            return MediaServerRuntime.writeUpload(owner, uploadId, copiedChunk);
        } catch (UploadException error) {
            throw new LuaException(error.getMessage());
        }
    }

    @LuaFunction
    public static String highAudioUploadFinish(
        SpeakerPeripheral speaker,
        IComputerAccess computer,
        String uploadId
    ) throws LuaException {
        ensurePlacedSpeaker(speaker);
        try {
            return MediaServerRuntime.finishUpload(owner(speaker, computer), parseUuid(uploadId, "upload id")).value();
        } catch (UploadException error) {
            throw new LuaException(error.getMessage());
        }
    }

    @LuaFunction
    public static boolean highAudioUploadAbort(
        SpeakerPeripheral speaker,
        IComputerAccess computer,
        String uploadId
    ) throws LuaException {
        ensurePlacedSpeaker(speaker);
        try {
            return MediaServerRuntime.abortUpload(owner(speaker, computer), parseUuid(uploadId, "upload id"));
        } catch (UploadException error) {
            throw new LuaException(error.getMessage());
        }
    }

    @LuaFunction(mainThread = true)
    public static String highAudioPlay(
        SpeakerPeripheral speaker,
        IComputerAccess computer,
        String contentId
    ) throws LuaException {
        ensurePlacedSpeaker(speaker);
        var access = (SpeakerPeripheralAccess) (Object) speaker;
        try {
            return MediaServerRuntime.play(
                owner(speaker, computer),
                access.highaudio$getLevel(),
                access.highaudio$getPosition().position(),
                new ContentId(contentId)
            ).toString();
        } catch (IllegalArgumentException | UploadException error) {
            throw new LuaException(error.getMessage());
        }
    }

    @LuaFunction(mainThread = true)
    public static boolean highAudioStop(SpeakerPeripheral speaker, IComputerAccess computer) throws LuaException {
        ensurePlacedSpeaker(speaker);
        return MediaServerRuntime.stop(speaker.getSource());
    }

    private static UploadManager.Owner owner(SpeakerPeripheral speaker, IComputerAccess computer) {
        return new UploadManager.Owner(speaker.getSource(), computer.getID());
    }

    private static UUID parseUuid(String value, String label) throws LuaException {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException error) {
            throw new LuaException("Malformed " + label);
        }
    }

    private static void ensurePlacedSpeaker(SpeakerPeripheral speaker) throws LuaException {
        if (!speaker.getClass().getName().equals("dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral")) {
            throw new LuaException("MILESTONE-004 HighAudio playback supports placed block speakers only");
        }
    }
}
