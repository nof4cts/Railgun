package com.sixpaths.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;

public final class ServerEvents {

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Scheduler.tick();
        Combat.tick();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        if (e.player instanceof ServerPlayer sp) PathsLogic.tick(sp);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PathsState st = PathsState.peek(sp);
        if (st == null) return;
        if (st.lockTicks > 0) {
            e.setCanceled(true);
            return;
        }
        if (PathsLogic.pretaAbsorb(sp, e.getSource(), e.getAmount())) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        PathsState st = PathsState.peek(sp);
        if (st != null && (st.ascended || sp.level().getGameTime() < st.noFallUntil)) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) PathsLogic.endAll(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            PathsState st = PathsState.peek(sp);
            if (st != null) {
                st.ascended = false;
                st.ascendUntil = 0;
                st.pretaUntil = 0;
                st.lockTicks = 0;
            }
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        Scheduler.clear();
    }

    private ServerEvents() {}
}
