package dev.ztawfik.cctweakedhighaudio.network;

import dev.ztawfik.cctweakedhighaudio.client.audio.media.MediaClientController;
import dev.ztawfik.cctweakedhighaudio.server.MediaServerRuntime;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Registers the deliberately small M4 play/content protocol. */
public final class MediaNetworking {
    private static final String PROTOCOL_VERSION = "1";

    private MediaNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToClient(MediaPayloads.Play.TYPE, MediaPayloads.Play.STREAM_CODEC, MediaNetworking::handlePlay);
        registrar.playToClient(MediaPayloads.Stop.TYPE, MediaPayloads.Stop.STREAM_CODEC, MediaNetworking::handleStop);
        registrar.playToServer(
            MediaPayloads.ContentRequest.TYPE,
            MediaPayloads.ContentRequest.STREAM_CODEC,
            MediaServerRuntime::handleContentRequest
        );
        registrar.playToClient(
            MediaPayloads.ContentBegin.TYPE,
            MediaPayloads.ContentBegin.STREAM_CODEC,
            MediaNetworking::handleContentBegin
        );
        registrar.playToClient(
            MediaPayloads.ContentChunk.TYPE,
            MediaPayloads.ContentChunk.STREAM_CODEC,
            MediaNetworking::handleContentChunk
        );
        registrar.playToClient(
            MediaPayloads.ContentEnd.TYPE,
            MediaPayloads.ContentEnd.STREAM_CODEC,
            MediaNetworking::handleContentEnd
        );
        registrar.playToClient(
            MediaPayloads.ContentUnavailable.TYPE,
            MediaPayloads.ContentUnavailable.STREAM_CODEC,
            MediaNetworking::handleContentUnavailable
        );
    }

    private static void handlePlay(MediaPayloads.Play payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        MediaClientController.handlePlay(payload);
    }

    private static void handleStop(MediaPayloads.Stop payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        MediaClientController.handleStop(payload);
    }

    private static void handleContentBegin(MediaPayloads.ContentBegin payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        MediaClientController.handleContentBegin(payload);
    }

    private static void handleContentChunk(MediaPayloads.ContentChunk payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        MediaClientController.handleContentChunk(payload);
    }

    private static void handleContentEnd(MediaPayloads.ContentEnd payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        MediaClientController.handleContentEnd(payload);
    }

    private static void handleContentUnavailable(MediaPayloads.ContentUnavailable payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        MediaClientController.handleContentUnavailable(payload);
    }
}
