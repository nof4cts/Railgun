package com.teengojo;

import com.jjk.mod.Sorcerer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/** The three places the jar calls into Teen Gojo (patched into Balance.used and the selector screen). */
public final class TeenHooks {
    private TeenHooks() {}

    /** A key press: true if Teen Gojo took it (the jar's own handling is then skipped). */
    public static boolean key(ServerPlayer p, int slot) {
        try {
            if (p != null && com.sbr.Sbr.key(p, slot)) {
                return true;
            }
            if (p != null && TeenGojo.is(p)) {
                TeenGojo.key(p, slot);
                return true;
            }
            return false;
        } catch (Throwable t) {
            TeenGojo.LOG.error("[teen gojo] key failed", t);
            return true;
        }
    }

    /** The selector's Stand tab with Johnny and Gyro added after Gold Experience Requiem. */
    public static Sorcerer[] withStands(Sorcerer[] list) {
        List<Sorcerer> out = new ArrayList<>(Arrays.asList(list));
        for (String n : new String[]{"JOHNNY", "GYRO"}) {
            try {
                Sorcerer s = Sorcerer.valueOf(n);
                if (!out.contains(s)) {
                    out.add(s);
                }
            } catch (Throwable ignored) {
            }
        }
        return out.toArray(new Sorcerer[0]);
    }

    /** The selector's list with Teen Gojo placed right after Gojo. */
    public static Sorcerer[] withTeen(Sorcerer[] list) {
        Sorcerer t = TeenGojo.teen();
        if (t == null) {
            return list;
        }
        for (Sorcerer s : list) {
            if (s == t) {
                return list;
            }
        }
        Sorcerer[] out = new Sorcerer[list.length + 1];
        int n = 0;
        boolean placed = false;
        for (Sorcerer s : list) {
            out[n++] = s;
            if (!placed && s.name().equals("GOJO")) {
                out[n++] = t;
                placed = true;
            }
        }
        if (!placed) {
            System.arraycopy(list, 0, out, 1, list.length);
            out[0] = t;
        }
        return out;
    }
}
