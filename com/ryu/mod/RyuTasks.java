package com.ryu.mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk"
)
public final class RyuTasks {
   private static final List<RyuTasks.Task> TASKS = new ArrayList<>();
   private static long now;

   private RyuTasks() {
   }

   public static void after(int ticks, Runnable r) {
      TASKS.add(new RyuTasks.Task(now + (long)Math.max(1, ticks), r));
   }

   @SubscribeEvent
   public static void onTick(ServerTickEvent e) {
      if (e.phase == Phase.END) {
         now++;
         List<RyuTasks.Task> due = new ArrayList<>();
         Iterator<RyuTasks.Task> it = TASKS.iterator();

         while (it.hasNext()) {
            RyuTasks.Task t = it.next();
            if (t.at <= now) {
               due.add(t);
               it.remove();
            }
         }

         for (RyuTasks.Task t : due) {
            try {
               t.run.run();
            } catch (RuntimeException var5) {
            }
         }
      }
   }

   private static record Task(long at, Runnable run) {
   }
}
