package com.geto.csm.server;

import com.geto.csm.ModRegistry;
import com.geto.csm.entity.CurseEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.Item;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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
        KuchisakeGame.tick();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != LogicalSide.SERVER) return;
        if (e.player instanceof ServerPlayer sp) CsmLogic.tick(sp);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent e) {
        LivingEntity victim = e.getEntity();
        Entity attacker = e.getSource().getEntity();
        // binding vow: nobody in the question game can hurt or be hurt
        if (KuchisakeGame.bound(victim) || KuchisakeGame.bound(attacker)) {
            e.setCanceled(true);
            return;
        }
        if (victim instanceof ServerPlayer sp) {
            CsmState st = CsmState.peek(sp);
            if (st != null && st.lockTicks > 0) e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || !(victim instanceof Enemy) || victim instanceof CurseEntity) return;
        Entity killer = e.getSource().getEntity();
        boolean byManipulator = false;
        if (killer instanceof CurseEntity c) byManipulator = c.owner() != null;
        else if (killer instanceof ServerPlayer sp) byManipulator = holds(sp, ModRegistry.MANIPULATION.get()) || holds(sp, ModRegistry.UZUMAKI.get());
        if (byManipulator && victim.getRandom().nextFloat() < 0.7f) CsmLogic.dropOrb(victim);
    }

    private static boolean holds(ServerPlayer sp, Item item) {
        return sp.getMainHandItem().is(item) || sp.getOffhandItem().is(item);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            CsmLogic.dismissAll(sp);
            CsmState.remove(sp);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        Scheduler.clear();
        KuchisakeGame.clear();
    }

    private ServerEvents() {}
}
