package com.firestar.rose.client;

import com.firestar.rose.RoseConfig;
import com.firestar.rose.client.cam.Lens;
import com.firestar.rose.common.RMove;
import com.firestar.rose.net.RoseNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;

/** Every Requiem Rose technique playing on this client. */
public final class RoseCasts {
    private static final List<RoseCast> ACTIVE = new ArrayList<>();

    private RoseCasts() {}

    public static void start(RoseNet.Play m) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ACTIVE.removeIf(c -> c.casterId == m.caster());
        RoseCast c = new RoseCast(m);
        ACTIVE.add(c);
        if (c.caster() instanceof AbstractClientPlayer p) {
            RoseAnims.play(p, c.move == RMove.REAP ? "reap_" + (c.seed & 3) : c.move.key);
        }
        RoseChoreo.begin(c);
        if (c.mine && c.move.cinematic && RoseConfig.CLIENT.cutscenes.get()) {
            Lens.play(c, Reels.of(c));
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        ACTIVE.removeIf(RoseCast::over);
        for (RoseCast c : new ArrayList<>(ACTIVE)) {
            int now = (int) Math.floor(c.time(0));
            for (int t = Math.max(c.lastTick + 1, now - 4); t <= now; t++) {
                if (t >= 0 && t <= c.move.length) {
                    RoseChoreo.tick(c, t);
                }
            }
            c.lastTick = Math.max(c.lastTick, now);
        }
    }

    public static RoseCast of(Player p) {
        for (RoseCast c : ACTIVE) {
            if (c.isCaster(p)) {
                return c;
            }
        }
        return null;
    }

    public static List<RoseCast> all() {
        return ACTIVE;
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
