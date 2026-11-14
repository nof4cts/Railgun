package com.aegis.command.client.cinematic;

import com.aegis.command.client.Sfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/** Engine roar that follows the flight plan, with distance falloff and a Doppler-style pitch shift. */
public class JetSound extends AbstractTickableSoundInstance {
    private final float basePitch;
    private final float baseVolume;
    private final float range;
    private double lastDist = -1;
    private int age;

    public JetSound(SoundEvent event, float pitch, float volume, float range) {
        super(event, SoundSource.MASTER, RandomSource.create());
        this.looping = true;
        this.delay = 0;
        this.volume = 0.001f;
        this.pitch = pitch;
        this.basePitch = pitch;
        this.baseVolume = volume;
        this.range = range;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.relative = false;
    }

    @Override
    public void tick() {
        age++;
        Vec3 jet = CinematicDirector.jetPos(1f);
        if (jet == null || age > 900) {
            stop();
            return;
        }
        x = jet.x;
        y = jet.y;
        z = jet.z;
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double d = cam.distanceTo(jet);
        float fall = (float) Math.max(0, 1 - d / range);
        volume = Math.max(0.001f, baseVolume * fall * fall * Sfx.master());
        if (lastDist >= 0) {
            double approach = lastDist - d; // blocks per tick toward the listener
            pitch = (float) Math.max(0.3, Math.min(2.0, basePitch * (1 + approach * 0.12)));
        }
        lastDist = d;
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
