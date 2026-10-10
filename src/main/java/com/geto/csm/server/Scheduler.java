package com.geto.csm.server;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Tiny server-tick scheduler for multi-hit choreography. */
public final class Scheduler {
    private record Task(long at, Runnable run) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();
    private static long now;

    public static void after(int ticks, Runnable r) {
        PENDING.add(new Task(now + Math.max(1, ticks), r));
    }

    public static void tick() {
        now++;
        TASKS.addAll(PENDING);
        PENDING.clear();
        Iterator<Task> it = TASKS.iterator();
        List<Task> due = new ArrayList<>();
        while (it.hasNext()) {
            Task t = it.next();
            if (t.at <= now) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) {
            try {
                t.run.run();
            } catch (Exception e) {
                // A target despawned mid-combo; never crash the server over a punch.
            }
        }
    }

    public static void clear() {
        TASKS.clear();
        PENDING.clear();
    }

    private Scheduler() {}
}
