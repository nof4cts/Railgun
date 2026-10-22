package com.woh.server;

import com.woh.W;
import com.woh.net.WNet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 4th Wall Breaker, the server side. Every player's screen is slashed open; a short moment later the world is saved
 * and the server is brought down cleanly (on a single-player world the player is dropped back to the title). Only
 * ever reached from the move, which is gated to operators. Skipped in the self-test so CI doesn't kill itself.
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class ServerShutdown {
    private static final boolean SELFTEST = Boolean.getBoolean("woh.selftest");
    private static MinecraftServer server;
    private static long dueTick;
    private static boolean pending;

    private ServerShutdown() {}

    public static void begin(MinecraftServer srv, String by, int ticks) {
        if (srv == null || pending) return;
        // the whole server sees it, wherever they are
        WNet.all(new WNet.Wall(by, ticks));
        srv.getPlayerList().broadcastSystemMessage(
                Component.literal("§c§lThe 4th wall has been broken by " + by + "."), false);
        W.LOG.warn("[woh] 4th Wall Breaker by {}: the server will go down in {} ticks", by, ticks);
        if (SELFTEST) {
            W.LOG.info("[woh] (self-test: the shutdown is not carried out)");
            return;
        }
        server = srv;
        dueTick = srv.getTickCount() + ticks + 10;
        pending = true;
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (!pending || e.phase != TickEvent.Phase.END || server == null) return;
        if (server.getTickCount() < dueTick) return;
        pending = false;
        try {
            W.LOG.warn("[woh] 4th Wall Breaker: saving the world and stopping the server");
            server.saveEverything(false, true, true);   // save all worlds and player data first - nothing is lost
        } catch (Throwable t) {
            W.LOG.error("[woh] couldn't save before shutdown", t);
        }
        // on a single-player world this returns the player to the title screen; on a dedicated server it stops it
        server.halt(false);
    }
}
