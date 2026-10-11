package com.firestar.silver.server;

import com.firestar.silver.SilverMod;
import com.firestar.silver.config.ZConfig;
import com.firestar.silver.network.Net;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Techniques.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer p) {
            Fighters.tick(p);
        }
    }

    /** Cinematic invulnerability, then the instinct: the blow may simply not be there when it lands. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        if (ZConfig.SERVER.invulnerableInCinematic.get() && Techniques.inCinematic(p)) {
            event.setCanceled(true);
            return;
        }
        if (Fighters.dodge(p, event.getSource())) {
            event.setCanceled(true);
        }
    }

    /** An awakened body lands like a feather. */
    @SubscribeEvent
    public static void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.getEntity() instanceof ServerPlayer p && Fighters.form(p) != Fighters.NONE) {
            event.setDistance(0);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Fighters.cleanFlight(p);
            Fighters.sync(p);
            Techniques.syncCooldowns(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Fighters.setForm(p, Fighters.NONE);
        }
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer subject && event.getEntity() instanceof ServerPlayer viewer) {
            Net.toPlayer(viewer, Fighters.packet(subject));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Techniques.forget(p);
            Fighters.forget(p);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        Techniques.clearAll();
        Fighters.clearAll();
    }
}
