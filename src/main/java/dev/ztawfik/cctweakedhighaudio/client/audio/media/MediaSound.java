package dev.ztawfik.cctweakedhighaudio.client.audio.media;

import dev.ztawfik.cctweakedhighaudio.HighAudio;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/** Positional production sound backed by HighAudio-decoded PCM and Minecraft's normal sound lifecycle. */
public final class MediaSound extends AbstractSoundInstance {
    public static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(HighAudio.MOD_ID, "media");

    private final MediaPcmStream stream;

    public MediaSound(Vec3 position, MediaPcmStream stream) {
        super(LOCATION, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.stream = stream;
        x = position.x;
        y = position.y;
        z = position.z;
        volume = 1.0f;
        pitch = 1.0f;
        looping = false;
        attenuation = Attenuation.LINEAR;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(stream);
    }
}
