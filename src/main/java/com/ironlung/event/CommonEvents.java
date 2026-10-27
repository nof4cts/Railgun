package com.ironlung.event;

import com.ironlung.IronLungMod;
import com.ironlung.mission.HorrorDirector;
import com.ironlung.mission.HorrorType;
import com.ironlung.mission.Mission;
import com.ironlung.mission.MissionManager;
import com.ironlung.network.Packets;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

@Mod.EventBusSubscriber(modid = IronLungMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) MissionManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) MissionManager.onLogin(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) MissionManager.onRespawn(sp);
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) MissionManager.onDeath(sp);
    }

    /** Nobody takes the boat apart from the inside. */
    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() != null && event.getPlayer().level().dimension().equals(IronLungMod.DIMENSION)
                && !event.getPlayer().hasPermissions(2)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp && sp.level().dimension().equals(IronLungMod.DIMENSION) && !sp.hasPermissions(2)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ironlung")
                .then(Commands.literal("start")
                        .executes(ctx -> start(ctx.getSource(), ctx.getSource().getPlayerOrException(), ctx.getSource().getLevel().getRandom().nextLong()))
                        .then(Commands.argument("seed", LongArgumentType.longArg())
                                .executes(ctx -> start(ctx.getSource(), ctx.getSource().getPlayerOrException(), LongArgumentType.getLong(ctx, "seed"))))
                        .then(Commands.argument("player", EntityArgument.player()).requires(s -> s.hasPermission(2))
                                .executes(ctx -> start(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), ctx.getSource().getLevel().getRandom().nextLong()))))
                .then(Commands.literal("abort").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Mission m = MissionManager.get(p);
                    if (m == null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.not_diving"));
                        return 0;
                    }
                    MissionManager.endMission(p, m, Packets.MissionEnd.ABORTED, true);
                    return 1;
                }))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Mission m = MissionManager.get(p);
                    if (m == null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.not_diving"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "seed %d | X %.1f Y %.1f | depth %.1f m | hull %d%% | power %s | sites %d/%d | tension %.2f",
                            m.seed, m.x, m.z, m.depth, Math.round(m.hull * 100), m.power, m.objectivesComplete(), m.objectiveDone.length, m.tension)), false);
                    return 1;
                }))
                .then(Commands.literal("event").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((ctx, b) -> {
                                    for (HorrorType t : HorrorType.values()) b.suggest(t.name().toLowerCase(Locale.ROOT));
                                    return b.buildFuture();
                                })
                                .executes(ctx -> {
                                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                                    Mission m = MissionManager.get(p);
                                    if (m == null) {
                                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.not_diving"));
                                        return 0;
                                    }
                                    HorrorType t;
                                    try {
                                        t = HorrorType.valueOf(StringArgumentType.getString(ctx, "type").toUpperCase(Locale.ROOT));
                                    } catch (IllegalArgumentException e) {
                                        ctx.getSource().sendFailure(Component.literal("Unknown event"));
                                        return 0;
                                    }
                                    if (t == HorrorType.SILHOUETTE || t == HorrorType.EYES || t == HorrorType.LEVIATHAN) m.cameraActiveUntil = m.ticks + 40;
                                    HorrorDirector.trigger(m, p, (ServerLevel) p.level(), t);
                                    return 1;
                                }))));
    }

    private static int start(CommandSourceStack src, ServerPlayer p, long seed) {
        if (MissionManager.hasMission(p)) {
            src.sendFailure(Component.translatable("ironlung.msg.already_diving"));
            return 0;
        }
        return MissionManager.startMission(p, seed) ? 1 : 0;
    }
}
