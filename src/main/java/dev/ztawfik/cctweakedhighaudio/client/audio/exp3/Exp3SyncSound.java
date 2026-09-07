package dev.ztawfik.cctweakedhighaudio.client.audio.exp3;

import dev.ztawfik.cctweakedhighaudio.client.audio.exp2.GeneratedPcmSound;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.concurrent.CompletableFuture;

/** One Minecraft-owned stream in an EXP-003 synchronization trial. */
public final class Exp3SyncSound extends AbstractSoundInstance {
    private final long runToken;
    private final int index;
    private final Exp3SyncStream stream;

    public Exp3SyncSound(long runToken, int index, Vec3 position, Exp3SyncStream stream) {
        super(GeneratedPcmSound.LOCATION, SoundSource.RECORDS, SoundInstance.createUnseededRandom());
        this.runToken = runToken;
        this.index = index;
        this.stream = stream;
        x = position.x;
        y = position.y;
        z = position.z;
        volume = 0.03f;
        pitch = 1.0f;
        looping = false;
        attenuation = Attenuation.LINEAR;
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(stream);
    }

    public long runToken() {
        return runToken;
    }

    public int index() {
        return index;
    }

    public Exp3SyncStream stream() {
        return stream;
    }
}
