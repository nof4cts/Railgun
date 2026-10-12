package com.firestar.orbital.server;

import com.firestar.orbital.OrbitalMod;
import com.firestar.orbital.config.OConfig;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = OrbitalMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Strikes.tick(event.getServer());
            for (net.minecraft.server.level.ServerLevel level : event.getServer().getAllLevels()) {
                Blight b = Blight.get(level);
                if (b.size() > 0) {
                    b.tick(level, OConfig.SERVER.blocksPerTick.get());
                    if (level.getGameTime() % 40 == 0) {
                        Blight.spawn(level);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Strikes.sync(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Strikes.sync(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Strikes.forget(p);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        Strikes.clearAll();
    }

    /** /orbital_arsenal fire helios|antipode : fire at the crosshair as the running player (operators; no cooldown). */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        var root = Commands.literal("orbital_arsenal").requires(s -> s.hasPermission(2));
        var fire = Commands.literal("fire");
        for (com.firestar.orbital.common.Cannon c : com.firestar.orbital.common.Cannon.values()) {
            fire.then(Commands.literal(c.name().toLowerCase()).executes(ctx -> {
                ServerPlayer p = ctx.getSource().getPlayerOrException();
                Vec3 at = Strikes.aim(p);
                if (at == null) {
                    return 0;
                }
                Strikes.begin(p, c, at);
                return 1;
            }));
        }
        var blight = Commands.literal("blight")
                .then(Commands.literal("stop").executes(ctx -> {
                    Blight.get(ctx.getSource().getLevel()).clear();
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("The blight stops spreading (what it took stays)."), true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    int n = Blight.get(ctx.getSource().getLevel()).size();
                    ctx.getSource().sendSuccess(() -> net.minecraft.network.chat.Component.literal("Blight front: " + n + " columns spreading."), false);
                    return n;
                }));
        event.getDispatcher().register(root.then(fire).then(blight));
    }
}
