package com.sixpaths.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

import java.util.ArrayList;
import java.util.List;

/** Client-side sound cues and a millisecond scheduler for cutscene choreography. */
public final class Sfx {
    private record Job(long at, Runnable run) {}

    private static final List<Job> JOBS = new ArrayList<>();

    public static void play(SoundEvent s, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
    }

    public static void later(long ms, Runnable r) {
        JOBS.add(new Job(Vfx.now() + ms, r));
    }

    public static void later(long ms, SoundEvent s, float pitch, float volume) {
        later(ms, () -> play(s, pitch, volume));
    }

    public static void run() {
        if (JOBS.isEmpty()) return;
        long t = Vfx.now();
        List<Job> due = new ArrayList<>();
        JOBS.removeIf(j -> {
            if (j.at <= t) {
                due.add(j);
                return true;
            }
            return false;
        });
        for (Job j : due) j.run.run();
    }

    public static void clear() {
        JOBS.clear();
    }

    private Sfx() {}
}
