package com.hakari.idg.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvent;

public final class Sfx {
   private static final List<Sfx.Job> JOBS = new ArrayList<>();

   public static void play(SoundEvent s, float pitch, float volume) {
      Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(s, pitch, volume));
   }

   public static void later(long ms, Runnable r) {
      JOBS.add(new Sfx.Job(Vfx.now() + ms, r));
   }

   public static void later(long ms, SoundEvent s, float pitch, float volume) {
      later(ms, () -> play(s, pitch, volume));
   }

   public static void run() {
      if (!JOBS.isEmpty()) {
         long t = Vfx.now();
         List<Sfx.Job> due = new ArrayList<>();
         JOBS.removeIf(jx -> {
            if (jx.at <= t) {
               due.add(jx);
               return true;
            } else {
               return false;
            }
         });

         for (Sfx.Job j : due) {
            j.run.run();
         }
      }
   }

   public static void clear() {
      JOBS.clear();
   }

   private Sfx() {
   }

   private static record Job(long at, Runnable run) {
   }
}
