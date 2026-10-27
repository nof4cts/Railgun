package com.abomination.horror.finisher;

import com.abomination.horror.AbominationMod;
import com.abomination.horror.entity.AbominationEntity;
import com.abomination.horror.registry.ModDamage;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = AbominationMod.MODID)
public final class FinisherEvents {
    private FinisherEvents() {}

    /** Intercept the killing blow and turn it into the cinematic. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer victim)) return;
        if (event.getSource().is(ModDamage.SPINE_RIP)) return;
        if (victim.isCreative() || victim.isSpectator()) return;
        if (!(event.getSource().getEntity() instanceof AbominationEntity creature)) return;
        if (FinisherManager.tryStart(creature, victim)) {
            event.setCanceled(true);
        }
    }

    /** Victims in the creature's grip can't be hurt by anything else. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FinisherManager.isInFinisher(p) && !event.getSource().is(ModDamage.SPINE_RIP)) {
            event.setCanceled(true);
        }
    }

    private static boolean locked(PlayerInteractEvent event) {
        return event.getEntity() instanceof ServerPlayer p && FinisherManager.isInFinisher(p);
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (locked(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (locked(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (locked(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (locked(event)) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && FinisherManager.isInFinisher(p)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) FinisherManager.tick();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) FinisherManager.onLogout(p);
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        FinisherManager.clear();
    }
}
