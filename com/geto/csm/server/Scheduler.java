package com.geto.csm.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class Scheduler {
   private static final List<Scheduler.Task> TASKS = new ArrayList<>();
   private static final List<Scheduler.Task> PENDING = new ArrayList<>();
   private static long now;

   public static void after(int ticks, Runnable r) {
      PENDING.add(new Scheduler.Task(now + (long)Math.max(1, ticks), r));
   }

   public static void tick() {
      now++;
      TASKS.addAll(PENDING);
      PENDING.clear();
      Iterator<Scheduler.Task> it = TASKS.iterator();
      List<Scheduler.Task> due = new ArrayList<>();

      while (it.hasNext()) {
         Scheduler.Task t = it.next();
         if (t.at <= now) {
            due.add(t);
            it.remove();
         }
      }

      for (Scheduler.Task t : due) {
         try {
            t.run.run();
         } catch (Exception var5) {
         }
      }
   }

   public static void clear() {
      TASKS.clear();
      PENDING.clear();
   }

   private Scheduler() {
   }

   private static record Task(long at, Runnable run) {
   }
}
