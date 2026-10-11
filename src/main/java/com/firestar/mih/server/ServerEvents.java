package com.firestar.mih.server;

import com.firestar.mih.MihMod;
import com.firestar.mih.config.ZConfig;
import com.firestar.mih.network.Net;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = com.firestar.styles.StylesMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Techniques.tick(event.getServer());
            TimeAccel.tick(event.getServer());
            UniverseReset.tick(event.getServer());
            InfiniteSpeed.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer p) {
            StandUsers.tick(p);
        }
    }

    @SubscribeEvent
    public static void onAttacked(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            if (UniverseReset.holding(p) || (ZConfig.SERVER.invulnerableInCinematic.get() && Techniques.inCinematic(p))) {
                event.setCanceled(true);
            }
        }
    }

    /** Without an arm you can't guard as well: everything hits harder. Blows in Infinite Speed are remembered. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer src && InfiniteSpeed.active(src)) {
            InfiniteSpeed.mark(src, event.getEntity(), event.getAmount());
        }
        if (event.getEntity() instanceof Player p && StandUsers.limb(p) == StandUsers.LIMB_ARM) {
            event.setAmount(event.getAmount() * 1.25F);
        }
    }

    /** Without a leg you can't jump. */
    @SubscribeEvent
    public static void onJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof Player p && StandUsers.limb(p) == StandUsers.LIMB_LEG) {
            Vec3 v = p.getDeltaMovement();
            p.setDeltaMovement(v.x * 0.4, Math.min(v.y, 0.12), v.z * 0.4);
        }
    }

    @SubscribeEvent
    public static void onName(PlayerEvent.NameFormat event) {
        if (StandUsers.alternate(event.getEntity())) {
            event.setDisplayname(event.getDisplayname().copy().append(Component.literal(" (Alternate Universe)")));
        }
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        StandUsers.copy(event.getOriginal(), event.getEntity());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            StandUsers.sync(p);
            Techniques.syncCooldowns(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            StandUsers.state(p).summoned = false;
            StandUsers.sync(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            TimeAccel.cancel(p);
            StandUsers.sync(p);
        }
    }

    /** Someone new sees a Stand user: tell them about the Stand. */
    @SubscribeEvent
    public static void onTrack(PlayerEvent.StartTracking event) {
        if (event.getTarget() instanceof ServerPlayer subject && event.getEntity() instanceof ServerPlayer viewer) {
            Net.toPlayer(viewer, StandUsers.packet(subject));
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            TimeAccel.cancel(p);
            Techniques.forget(p);
            StandUsers.forget(p);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        TimeAccel.clearAll();
        InfiniteSpeed.clearAll();
        UniverseReset.clearAll();
        Techniques.clearAll();
        StandUsers.clearAll();
    }
}
