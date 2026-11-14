package com.aegis.command.client;

import com.aegis.command.AegisConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Sound design layer. Every mod sound is a layered, pitched composition of vanilla sound events
 * (so there is nothing to license), scaled by the terminal's volume / mute settings, and can be
 * scheduled in ticks for precise sync with animations.
 */
public final class Sfx {
    private static final RandomSource RNG = RandomSource.create();
    private static final List<Pending> QUEUE = new ArrayList<>();

    private record Pending(int due, Runnable action) {}

    private static int clock;

    private Sfx() {}

    public static float master() {
        if (AegisConfig.CLIENT.muted.get()) return 0f;
        return AegisConfig.CLIENT.volume.get() / 100f;
    }

    public static void ui(SoundEvent ev, float pitch, float vol) {
        if (!AegisConfig.CLIENT.uiSounds.get()) return;
        raw(ev, pitch, vol);
    }

    public static void raw(SoundEvent ev, float pitch, float vol) {
        float v = vol * master();
        if (v <= 0.001f) return;
        Minecraft.getInstance().getSoundManager().play(
                new SimpleSoundInstance(ev.getLocation(), SoundSource.MASTER, v, pitch, RNG, false, 0,
                        SoundInstance.Attenuation.NONE, 0, 0, 0, true));
    }

    public static void at(SoundEvent ev, double x, double y, double z, float pitch, float vol) {
        float v = vol * master();
        if (v <= 0.001f) return;
        Minecraft.getInstance().getSoundManager().play(
                new SimpleSoundInstance(ev, SoundSource.MASTER, v, pitch, RNG, x, y, z));
    }

    public static void later(int ticks, Runnable r) {
        QUEUE.add(new Pending(clock + Math.max(0, ticks), r));
    }

    public static void tick() {
        clock++;
        if (QUEUE.isEmpty()) return;
        List<Runnable> run = new ArrayList<>();
        Iterator<Pending> it = QUEUE.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.due <= clock) { run.add(p.action); it.remove(); }
        }
        run.forEach(Runnable::run);
    }

    // ------------------------------------------------------------ named cues

    public static void click() { ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.9f, 0.25f); ui(SoundEvents.NOTE_BLOCK_HAT.value(), 2.0f, 0.15f); }
    public static void hover() { ui(SoundEvents.NOTE_BLOCK_HAT.value(), 2.0f, 0.06f); }
    public static void tab() { ui(SoundEvents.NOTE_BLOCK_BIT.value(), 1.6f, 0.18f); ui(SoundEvents.UI_BUTTON_CLICK.value(), 1.4f, 0.15f); }
    public static void deny() { ui(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.5f); later(3, () -> ui(SoundEvents.NOTE_BLOCK_BASS.value(), 0.5f, 0.5f)); }
    public static void confirm() { ui(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.3f); later(2, () -> ui(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 0.3f)); }
    public static void ping() { ui(SoundEvents.NOTE_BLOCK_BIT.value(), 2.0f, 0.08f); }

    public static void startup() {
        raw(SoundEvents.BEACON_ACTIVATE, 1.7f, 0.35f);
        later(4, () -> raw(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.25f));
        later(8, () -> raw(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.6f, 0.25f));
        later(12, () -> raw(SoundEvents.NOTE_BLOCK_CHIME.value(), 2.0f, 0.25f));
    }

    public static void scan() {
        for (int i = 0; i < 6; i++) {
            final float p = 0.8f + i * 0.2f;
            later(i * 2, () -> ui(SoundEvents.NOTE_BLOCK_BIT.value(), p, 0.12f));
        }
    }

    /** Two-tone alert siren, repeated. */
    public static void alarm(int cycles) {
        for (int i = 0; i < cycles; i++) {
            later(i * 10, () -> raw(SoundEvents.NOTE_BLOCK_BIT.value(), 1.2f, 0.5f));
            later(i * 10 + 5, () -> raw(SoundEvents.NOTE_BLOCK_BIT.value(), 0.9f, 0.5f));
        }
        raw(SoundEvents.BEACON_POWER_SELECT, 0.6f, 0.6f);
    }

    /** Fictional radio chatter: squelch + chirp. Subtitles carry the words. */
    public static void radio() {
        raw(SoundEvents.NOTE_BLOCK_BIT.value(), 1.9f, 0.3f);
        later(2, () -> raw(SoundEvents.NOTE_BLOCK_BIT.value(), 1.5f, 0.25f));
        raw(SoundEvents.SAND_BREAK, 2.0f, 0.15f);
    }

    public static void missionComplete() {
        raw(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.5f);
        later(6, () -> raw(SoundEvents.PLAYER_LEVELUP, 1.4f, 0.4f));
    }

    public static void impact(double x, double y, double z, float power, double distance) {
        // sound travels ~17 blocks per tick: distant impacts are heard after the flash
        int delay = (int) (distance / 17.0);
        float s = Math.min(1.5f, power / 4f);
        later(delay, () -> {
            at(SoundEvents.GENERIC_EXPLODE, x, y, z, 0.55f, 6f * s);
            at(SoundEvents.GENERIC_EXPLODE, x, y, z, 0.8f, 4f * s);
            at(SoundEvents.WARDEN_SONIC_BOOM, x, y, z, 0.5f, 3f * s);
            at(SoundEvents.DRAGON_FIREBALL_EXPLODE, x, y, z, 0.6f, 3f * s);
        });
        later(delay + 6, () -> at(SoundEvents.LIGHTNING_BOLT_THUNDER, x, y, z, 0.6f, 2.5f * s));
        later(delay + 14, () -> at(SoundEvents.GRAVEL_BREAK, x, y, z, 0.5f, 3f * s));
    }
}
