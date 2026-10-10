package com.hakari.idg.server;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
        if (e.player instanceof ServerPlayer sp) HakariLogic.tick(sp);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        HakariState st = HakariState.peek(sp);
        if (st == null) return;
        if (st.locked()) {
            e.setCanceled(true);
            return;
        }
        Entity attacker = e.getSource().getEntity();
        if (st.counterTicks > 0 && attacker instanceof LivingEntity living && living != sp) {
            e.setCanceled(true);
            Moves.counterTrigger(sp, st, living);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        HakariState st = HakariState.peek(sp);
        if (st != null && st.immortal()) {
            // Automatic reverse cursed technique: refuse to die while on a roll.
            e.setCanceled(true);
            sp.setHealth(sp.getMaxHealth());
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        HakariState st = HakariState.peek(sp);
        if (st != null && st.immortal()) e.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) HakariLogic.onLogout(sp);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        Scheduler.clear();
    }

    private ServerEvents() {}
}
