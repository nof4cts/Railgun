package com.firestar.mih.client;

import com.firestar.mih.client.anim.PlayerAnims;
import com.firestar.mih.client.cinematic.Director;
import com.firestar.mih.client.cinematic.Films;
import com.firestar.mih.client.fx.Ribbon;
import com.firestar.mih.client.fx.ScreenFx;
import com.firestar.mih.common.Move;
import com.firestar.mih.config.ZConfig;
import com.firestar.mih.network.S2CCast;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/** Every move currently playing on this client. */
public final class Casts {
    private static final List<ClientCast> ACTIVE = new ArrayList<>();

    private Casts() {}

    public static void start(S2CCast msg) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ACTIVE.removeIf(c -> c.casterId == msg.casterId && (c.move != Move.UNIVERSE_RESET || msg.move == Move.UNIVERSE_RESET));
        ClientCast cast = new ClientCast(msg);
        ACTIVE.add(cast);
        Entity caster = cast.caster();
        if (caster instanceof AbstractClientPlayer p) {
            PlayerAnims.play(p, cast.move);
        }
        Choreo.begin(cast);
        if (cast.watching && cast.move.cinematic && ((ZConfig.CLIENT.cutscenes.get() && com.firestar.styles.client.ClientStyles.cutscenes()) || cast.move == Move.UNIVERSE_RESET)) {
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
        for (ClientCast c : new ArrayList<>(ACTIVE)) {
            int now = (int) Math.floor(c.time(0.0F));
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
                Choreo.ribbons(c, t, partial, out);
            }
        }
        Choreo.ambientRibbons(out, partial);
    }

    public static void screen(ScreenFx fx, float partial) {
        Minecraft mc = Minecraft.getInstance();
        for (ClientCast c : ACTIVE) {
            float t = c.time(partial);
            if (!c.watching && (mc.player == null || mc.player.position().distanceTo(c.point) > 40)) {
                continue;
            }
            if (t >= 0) {
                Choreo.screen(c, t, fx);
            }
        }
        Choreo.ambientScreen(fx, partial);
    }

    public static ClientCast of(Player p) {
        for (ClientCast c : ACTIVE) {
            if (c.isCaster(p) && c.move != Move.UNIVERSE_RESET) {
                return c;
            }
        }
        return null;
    }

    /** The universe reset this client is watching, if any. */
    public static ClientCast reset() {
        for (ClientCast c : ACTIVE) {
            if (c.move == Move.UNIVERSE_RESET) {
                return c;
            }
        }
        return null;
    }

    public static List<ClientCast> all() {
        return ACTIVE;
    }

    public static boolean any() {
        return !ACTIVE.isEmpty();
    }

    public static void clear() {
        ACTIVE.clear();
    }
}
