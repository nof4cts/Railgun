package com.firestar.santoryu.client;

import com.firestar.santoryu.client.anim.PlayerAnims;
import com.firestar.santoryu.client.cinematic.Director;
import com.firestar.santoryu.client.cinematic.Films;
import com.firestar.santoryu.client.fx.Ribbon;
import com.firestar.santoryu.client.fx.ScreenFx;
import com.firestar.santoryu.config.ZConfig;
import com.firestar.santoryu.network.S2CCast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Every technique currently playing on this client. */
public final class Casts {
    private static final List<ClientCast> ACTIVE = new ArrayList<>();

    private Casts() {}

    public static void start(S2CCast msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ACTIVE.removeIf(c -> c.casterId == msg.casterId);
        ClientCast cast = new ClientCast(msg);
        ACTIVE.add(cast);
        Entity caster = cast.caster();
        if (caster instanceof AbstractClientPlayer p) {
            PlayerAnims.play(p, cast.move);
        }
        if (cast.mine && cast.move.cinematic && ZConfig.CLIENT.cutscenes.get()) {
            Director.play(Films.of(cast));
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            ACTIVE.clear();
            return;
        }
        ACTIVE.removeIf(ClientCast::over);
        for (ClientCast c : ACTIVE) {
            int now = (int) Math.floor(c.time(0.0F));
            // fire every tick we passed, once, even if a tick was skipped on a lag spike
            for (int t = Math.max(c.lastTick + 1, now - 4); t <= now; t++) {
                if (t >= 0 && t <= c.move.length) {
                    Choreo.tick(c, t);
                }
            }
            c.lastTick = Math.max(c.lastTick, now);
        }
    }

    public static void ribbons(List<Ribbon> out, float partial) {
        for (ClientCast c : ACTIVE) {
            float t = c.time(partial);
            if (t >= 0) {
                Choreo.ribbons(c, t, out);
            }
        }
    }

    public static void screen(ScreenFx fx, float partial) {
        for (ClientCast c : ACTIVE) {
            float t = c.time(partial);
            // other players' techniques only touch your screen when they are close
            if (!c.mine) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player == null || mc.player.position().distanceTo(c.point) > 48) {
                    continue;
                }
            }
            if (t >= 0) {
                Choreo.screen(c, t, fx);
            }
        }
    }

    public static ClientCast of(Player p) {
        for (ClientCast c : ACTIVE) {
            if (c.isCaster(p)) {
                return c;
            }
        }
        return null;
    }

    public static boolean any() {
        return !ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
