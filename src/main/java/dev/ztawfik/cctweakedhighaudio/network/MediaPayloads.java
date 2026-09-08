package dev.ztawfik.cctweakedhighaudio.network;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import dev.ztawfik.cctweakedhighaudio.media.ContentId;
import dev.ztawfik.cctweakedhighaudio.media.MediaLimits;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** Wire messages for M4 content-on-demand and authoritative play/stop control. */
public final class MediaPayloads {
    private MediaPayloads() {
    }

    public record Play(
        UUID sourceId,
        UUID sessionId,
        ContentId contentId,
        int contentLength,
        double x,
        double y,
        double z
    ) implements CustomPacketPayload {
        public static final Type<Play> TYPE = MediaPayloads.type("play");
        public static final StreamCodec<RegistryFriendlyByteBuf, Play> STREAM_CODEC = StreamCodec.ofMember(
            Play::encode,
            Play::decode
        );

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(sourceId);
            buffer.writeUUID(sessionId);
            writeContentId(buffer, contentId);
            buffer.writeVarInt(contentLength);
            buffer.writeDouble(x);
            buffer.writeDouble(y);
            buffer.writeDouble(z);
        }

        private static Play decode(RegistryFriendlyByteBuf buffer) {
            return new Play(
                buffer.readUUID(),
                buffer.readUUID(),
                readContentId(buffer),
                buffer.readVarInt(),
                buffer.readDouble(),
                buffer.readDouble(),
                buffer.readDouble()
            );
        }

        @Override
        public Type<Play> type() {
            return TYPE;
        }
    }

    public record Stop(UUID sourceId, UUID sessionId) implements CustomPacketPayload {
        public static final Type<Stop> TYPE = MediaPayloads.type("stop");
        public static final StreamCodec<RegistryFriendlyByteBuf, Stop> STREAM_CODEC = StreamCodec.ofMember(
            Stop::encode,
            buffer -> new Stop(buffer.readUUID(), buffer.readUUID())
        );

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(sourceId);
            buffer.writeUUID(sessionId);
        }

        @Override
        public Type<Stop> type() {
            return TYPE;
        }
    }

    public record ContentRequest(UUID sourceId, UUID sessionId, ContentId contentId) implements CustomPacketPayload {
        public static final Type<ContentRequest> TYPE = MediaPayloads.type("content_request");
        public static final StreamCodec<RegistryFriendlyByteBuf, ContentRequest> STREAM_CODEC = StreamCodec.ofMember(
            ContentRequest::encode,
            buffer -> new ContentRequest(buffer.readUUID(), buffer.readUUID(), readContentId(buffer))
        );

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(sourceId);
            buffer.writeUUID(sessionId);
            writeContentId(buffer, contentId);
        }

        @Override
        public Type<ContentRequest> type() {
            return TYPE;
        }
    }

    public record ContentBegin(UUID sessionId, ContentId contentId, int contentLength) implements CustomPacketPayload {
        public static final Type<ContentBegin> TYPE = MediaPayloads.type("content_begin");
        public static final StreamCodec<RegistryFriendlyByteBuf, ContentBegin> STREAM_CODEC = StreamCodec.ofMember(
            ContentBegin::encode,
            buffer -> new ContentBegin(buffer.readUUID(), readContentId(buffer), buffer.readVarInt())
        );

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(sessionId);
            writeContentId(buffer, contentId);
            buffer.writeVarInt(contentLength);
        }

        @Override
        public Type<ContentBegin> type() {
            return TYPE;
        }
    }

    public record ContentChunk(UUID sessionId, int offset, byte[] bytes) implements CustomPacketPayload {
        public static final Type<ContentChunk> TYPE = MediaPayloads.type("content_chunk");
        public static final StreamCodec<RegistryFriendlyByteBuf, ContentChunk> STREAM_CODEC = StreamCodec.ofMember(
            ContentChunk::encode,
            buffer -> new ContentChunk(
                buffer.readUUID(),
                buffer.readVarInt(),
                buffer.readByteArray(MediaLimits.HARD_MAX_NETWORK_CHUNK_BYTES)
            )
        );

        public ContentChunk {
            bytes = bytes.clone();
        }

        private void encode(RegistryFriendlyByteBuf buffer) {
            if (bytes.length == 0 || bytes.length > MediaLimits.HARD_MAX_NETWORK_CHUNK_BYTES) {
                throw new IllegalArgumentException("Invalid content transfer chunk length");
            }
            buffer.writeUUID(sessionId);
            buffer.writeVarInt(offset);
            buffer.writeByteArray(bytes);
        }

        @Override
        public Type<ContentChunk> type() {
            return TYPE;
        }
    }

    public record ContentEnd(UUID sessionId, ContentId contentId) implements CustomPacketPayload {
        public static final Type<ContentEnd> TYPE = MediaPayloads.type("content_end");
        public static final StreamCodec<RegistryFriendlyByteBuf, ContentEnd> STREAM_CODEC = StreamCodec.ofMember(
            ContentEnd::encode,
            buffer -> new ContentEnd(buffer.readUUID(), readContentId(buffer))
        );

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(sessionId);
            writeContentId(buffer, contentId);
        }

        @Override
        public Type<ContentEnd> type() {
            return TYPE;
        }
    }

    public record ContentUnavailable(UUID sessionId, String reason) implements CustomPacketPayload {
        public static final Type<ContentUnavailable> TYPE = MediaPayloads.type("content_unavailable");
        public static final StreamCodec<RegistryFriendlyByteBuf, ContentUnavailable> STREAM_CODEC = StreamCodec.ofMember(
            ContentUnavailable::encode,
            buffer -> new ContentUnavailable(buffer.readUUID(), buffer.readUtf(256))
        );

        private void encode(RegistryFriendlyByteBuf buffer) {
            buffer.writeUUID(sessionId);
            buffer.writeUtf(reason, 256);
        }

        @Override
        public Type<ContentUnavailable> type() {
            return TYPE;
        }
    }

    private static void writeContentId(RegistryFriendlyByteBuf buffer, ContentId id) {
        buffer.writeUtf(id.value(), 64);
    }

    private static ContentId readContentId(RegistryFriendlyByteBuf buffer) {
        return new ContentId(buffer.readUtf(64));
    }

    private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> type(String path) {
        return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(HighAudio.MOD_ID, path));
    }
}
