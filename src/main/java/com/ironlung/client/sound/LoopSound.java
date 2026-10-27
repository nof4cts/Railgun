package com.ironlung.client.sound;

import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.function.DoubleSupplier;

/** A non-positional loop whose volume and pitch ease toward targets supplied every tick. */
public class LoopSound extends AbstractTickableSoundInstance {
    private final DoubleSupplier targetVolume;
    private final DoubleSupplier targetPitch;
    private float cur;
    private boolean dead;

    public LoopSound(SoundEvent event, DoubleSupplier targetVolume, DoubleSupplier targetPitch) {
        super(event, SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.targetVolume = targetVolume;
        this.targetPitch = targetPitch;
        this.looping = true;
        this.delay = 0;
        this.relative = true;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0001f;
        this.pitch = 1.0f;
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    @Override
    public void tick() {
        if (dead) {
            stop();
            return;
        }
        float t = (float) targetVolume.getAsDouble();
        cur += (t - cur) * (t < cur ? 0.08f : 0.03f);
        volume = Math.max(0.0001f, cur);
        pitch = (float) Math.max(0.5, Math.min(2.0, targetPitch.getAsDouble()));
    }

    public void kill() {
        dead = true;
    }

    public boolean isDead() {
        return dead || isStopped();
    }
}
