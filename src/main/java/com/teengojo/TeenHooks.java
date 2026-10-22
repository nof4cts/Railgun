package com.teengojo;

import com.jjk.mod.Sorcerer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;

/**
 * The places the jar calls into the added sorcerers (patched into Balance.used and the selector screen).
 * Recompiled here with Modulo Yuji added: his keys, and his card in the JJK tab right after Todo.
 */
public final class TeenHooks {
    private TeenHooks() {}

    /** A key press: true if an added sorcerer took it (the jar's own handling is then skipped). */
    public static boolean key(ServerPlayer p, int slot) {
        try {
            if (p != null && com.woh.server.Woh.key(p, slot)) {
                return true;
            }
        } catch (Throwable t) {
            com.woh.W.LOG.error("[woh] key failed", t);
            return true;
        }
        try {
            if (p != null && com.yuji.server.Yuji.key(p, slot)) {
                return true;
            }
        } catch (Throwable t) {
            com.yuji.Y.LOG.error("[yuji] key failed", t);
            return true;
        }
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
        for (String n : new String[]{"JOHNNY", "GYRO", "WORLD_OVER_HEAVEN"}) {
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

    /** The selector's list with Teen Gojo placed right after Gojo, and Modulo Yuji right after Todo. */
    public static Sorcerer[] withTeen(Sorcerer[] list) {
        return withYuji(withTeenOnly(list));
    }

    private static Sorcerer[] withYuji(Sorcerer[] list) {
        List<Sorcerer> out = new ArrayList<>(Arrays.asList(list));
        if (out.contains(Sorcerer.YUJI)) {
            return list;
        }
        int at = -1;
        for (int i = 0; i < out.size(); i++) {
            if (out.get(i) == Sorcerer.TODO) at = i + 1;
        }
        if (at < 0) {
            at = Math.max(0, out.indexOf(Sorcerer.NONE));
            if (at == 0 && !out.contains(Sorcerer.NONE)) at = out.size();
        }
        out.add(at, Sorcerer.YUJI);
        return out.toArray(new Sorcerer[0]);
    }

    private static Sorcerer[] withTeenOnly(Sorcerer[] list) {
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
