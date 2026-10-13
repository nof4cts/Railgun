package com.firestar.rose.server;

import com.firestar.rose.RoseConfig;
import com.firestar.rose.net.RoseNet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID)
public final class RoseServerEvents {
    private RoseServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            Reaping.tick(e.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) {
            Reaper.tick(p);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && RoseConfig.SERVER.invulnerableInCinematic.get() && Reaping.inCinematic(p)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && (Reaper.form(p) != Reaper.MORTAL || Reaping.busy(p))) {
            e.setDistance(0);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Reaper.cleanFlight(p);
            Reaper.sync(p);
            Reaping.syncReady(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Reaper.setForm(p, Reaper.MORTAL);
        }
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer subject && e.getEntity() instanceof ServerPlayer viewer) {
            RoseNet.to(viewer, Reaper.packet(subject));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Reaping.forget(p);
            Reaper.forget(p);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        Reaping.clearAll();
        Reaper.clearAll();
    }
}
