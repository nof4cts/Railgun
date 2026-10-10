package com.cataclysm.spells.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
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
        SpellState st = SpellState.peek(e.player);
        if (st != null && st.lockTicks > 0) st.lockTicks--;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            SpellState st = SpellState.peek(sp);
            if (st != null && st.lockTicks > 0) e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        SpellState.remove(e.getEntity());
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        Scheduler.clear();
    }

    private ServerEvents() {}
}
