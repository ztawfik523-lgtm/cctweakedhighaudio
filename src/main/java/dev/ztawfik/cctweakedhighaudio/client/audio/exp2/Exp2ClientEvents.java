package dev.ztawfik.cctweakedhighaudio.client.audio.exp2;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;

/** Main client event-bus hooks for EXP-002. */
@EventBusSubscriber(modid = HighAudio.MOD_ID, value = Dist.CLIENT)
public final class Exp2ClientEvents {
    private Exp2ClientEvents() {
    }

    @SubscribeEvent
    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        Exp2AudioController.onPlayStreaming(event);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Exp2AudioController.tick();
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("highaudio_exp2")
                .executes(context -> respond(Exp2AudioController.status()))
                .then(Commands.literal("play").executes(context -> respond(Exp2AudioController.play())))
                .then(Commands.literal("stop").executes(context -> respond(Exp2AudioController.stop())))
                .then(Commands.literal("status").executes(context -> respond(Exp2AudioController.status())))
        );
    }

    private static int respond(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(message), false);
        return 1;
    }
}
