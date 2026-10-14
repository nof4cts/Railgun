package com.firestar.sage.server;

import com.firestar.sage.SageConfig;
import com.firestar.sage.net.SageNet;
import com.firestar.styles.StylesMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = StylesMod.MODID)
public final class SageServerEvents {
    private SageServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase == TickEvent.Phase.END) {
            Rites.tick(e.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase == TickEvent.Phase.END && e.player instanceof ServerPlayer p) {
            Sage.tick(p);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttacked(LivingAttackEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && SageConfig.SERVER.invulnerableInCinematic.get() && Rites.inCinematic(p)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && (Sage.form(p) != Sage.MORTAL || Rites.busy(p))) {
            e.setDistance(0);
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Sage.cleanFlight(p);
            Sage.sync(p);
            Rites.syncReady(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && Sage.form(p) != Sage.MORTAL) {
            Sage.setForm(p, Sage.MORTAL);
        }
    }

    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking e) {
        if (e.getTarget() instanceof ServerPlayer subject && e.getEntity() instanceof ServerPlayer viewer) {
            SageNet.to(viewer, Sage.packet(subject));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            Rites.forget(p);
            Sage.forget(p);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent e) {
        Rites.clearAll();
        Sage.clearAll();
    }
}
