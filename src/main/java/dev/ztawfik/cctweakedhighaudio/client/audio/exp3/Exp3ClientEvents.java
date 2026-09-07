package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import com.mojang.brigadier.arguments.IntegerArgumentType;
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

/** Main client event-bus hooks for EXP-003 Part A. */
@EventBusSubscriber(modid = HighAudio.MOD_ID, value = Dist.CLIENT)
public final class Exp3ClientEvents {
    private Exp3ClientEvents() {
    }

    @SubscribeEvent
    public static void onPlayStreaming(PlayStreamingSourceEvent event) {
        Exp3CapacityController.onPlayStreaming(event);
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Exp3CapacityController.tick();
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("highaudio_exp3")
                .executes(context -> respond(Exp3CapacityController.status()))
                .then(Commands.literal("capacity")
                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 16))
                        .executes(context -> respond(Exp3CapacityController.runCapacity(
                            IntegerArgumentType.getInteger(context, "count")
                        )))))
                .then(Commands.literal("stop").executes(context -> respond(Exp3CapacityController.stop())))
                .then(Commands.literal("status").executes(context -> respond(Exp3CapacityController.status())))
        );
    }

    private static int respond(String message) {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal(message), false);
        return 1;
    }
}
