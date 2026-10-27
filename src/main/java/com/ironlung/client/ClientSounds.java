package com.ironlung.client;

import com.ironlung.block.ControlType;
import com.ironlung.client.sound.LoopSound;
import com.ironlung.config.IronLungConfig;
import com.ironlung.mission.SubInterior;
import com.ironlung.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Ambient loops, proximity alarm and small local UI sounds. */
public final class ClientSounds {
    private ClientSounds() {}

    private static LoopSound drone, engine, buzz;
    private static int pingCooldown;
    private static float silenceFade = 1f;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientMissionState.inSub()) {
            stopAll();
            return;
        }
        boolean silent = ClientMissionState.silenceTicks > 0;
        silenceFade += ((silent ? 0f : 1f) - silenceFade) * (silent ? 0.05f : 0.01f);
        double master = IronLungConfig.AMBIENT_VOLUME.get();

        if (drone == null || drone.isDead()) {
            drone = new LoopSound(ModSounds.AMBIENT_DRONE.get(),
                    () -> 0.8 * master * silenceFade * (ClientMissionState.introTicks > 100 ? 0.3 : 1.0),
                    () -> 0.92 + Math.min(0.12, (ClientMissionState.depth() - 120) / 1000.0));
            mc.getSoundManager().play(drone);
        }
        if (engine == null || engine.isDead()) {
            engine = new LoopSound(ModSounds.ENGINE_HUM.get(),
                    () -> {
                        if (ClientMissionState.cur == null || !ClientMissionState.powered()) return 0;
                        return Math.min(1.0, Math.abs(ClientMissionState.cur.speed) / 1.5) * 0.7 * master * (0.4 + 0.6 * silenceFade);
                    },
                    () -> ClientMissionState.cur == null ? 1.0 : 0.8 + Math.abs(ClientMissionState.cur.speed) * 0.12);
            mc.getSoundManager().play(engine);
        }
        if (buzz == null || buzz.isDead()) {
            buzz = new LoopSound(ModSounds.ELECTRIC_BUZZ.get(),
                    () -> {
                        if (!ClientMissionState.powered()) return 0;
                        double v = 0.18;
                        if (ClientMissionState.glitchTicks > 0) v = 0.45 + Math.random() * 0.4;
                        return v * master * silenceFade;
                    },
                    () -> 1.0);
            mc.getSoundManager().play(buzz);
        }

        // proximity alarm
        if (pingCooldown > 0) pingCooldown--;
        if (ClientMissionState.cur != null && ClientMissionState.powered() && pingCooldown == 0) {
            float min = 99;
            for (float v : ClientMissionState.cur.prox) min = Math.min(min, v);
            if (min < 10) {
                playAt(ControlType.PROXIMITY, ModSounds.PROXIMITY_PING.get(), 0.55f, min < 4 ? 1.35f : 1.0f);
                pingCooldown = (int) (5 + min * 3.2);
            }
        }
    }

    public static void stopAll() {
        if (drone != null) drone.kill();
        if (engine != null) engine.kill();
        if (buzz != null) buzz.kill();
        drone = engine = buzz = null;
    }

    public static void playAt(ControlType control, SoundEvent sound, float vol, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos p = ClientMissionState.base().offset(SubInterior.CONTROL_POS.get(control));
        mc.level.playLocalSound(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, sound, SoundSource.BLOCKS, vol, pitch, false);
    }
}
