package com.firestar.worldbreaker.server;

import com.firestar.worldbreaker.WorldbreakerMod;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = WorldbreakerMod.MODID)
public final class ServerEvents {
    private ServerEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            Marches.tick(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Marches.sync(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Marches.sync(p);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            Marches.forget(p);
        }
    }

    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        Marches.clearAll();
    }

    /** /worldbreaker march [yaw] : begin a march as the running player (operators; ignores the cooldown). */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("worldbreaker").requires(s -> s.hasPermission(2))
                .then(Commands.literal("march")
                        .executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            Marches.begin(p, p.getLookAngle());
                            return 1;
                        })
                        .then(Commands.argument("yaw", FloatArgumentType.floatArg(-360, 360)).executes(ctx -> {
                            ServerPlayer p = ctx.getSource().getPlayerOrException();
                            float yaw = FloatArgumentType.getFloat(ctx, "yaw");
                            Marches.begin(p, Vec3.directionFromRotation(0, yaw));
                            return 1;
                        }))));
    }
}
