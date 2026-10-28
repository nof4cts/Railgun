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
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = IronLungMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {}

    /** invitee -> (mission id, expiry millis) */
    private static final Map<UUID, Object[]> INVITES = new HashMap<>();
    private static final long INVITE_MS = 120_000;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) MissionManager.tick(event.getServer());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) MissionManager.onLogin(sp);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) MissionManager.onLogout(sp);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) MissionManager.onRespawn(sp);
    }

    /** Deaths to the deep or to the thing in it don't scatter your gear into the void; it comes back on respawn. */
    @SubscribeEvent
    public static void onDrops(net.minecraftforge.event.entity.living.LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (!event.getSource().is(MissionManager.TAKEN) && !event.getSource().is(MissionManager.CRUSHED)) return;
        java.util.List<net.minecraft.world.item.ItemStack> held = MissionManager.HELD_ITEMS.computeIfAbsent(sp.getUUID(), k -> new java.util.ArrayList<>());
        for (net.minecraft.world.entity.item.ItemEntity it : event.getDrops()) held.add(it.getItem().copy());
        event.setCanceled(true);
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
                    if (MissionManager.isLeader(p, m)) MissionManager.finish(p.server, m, Packets.MissionEnd.ABORTED, true);
                    else MissionManager.leaveMission(p, m, true);
                    return 1;
                }))
                .then(Commands.literal("complete").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Mission m = MissionManager.get(p);
                    if (m == null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.not_diving"));
                        return 0;
                    }
                    MissionManager.completeAll(m);
                    return 1;
                }))
                .then(Commands.literal("finale").requires(s -> s.hasPermission(2)).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Mission m = MissionManager.get(p);
                    if (m == null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.not_diving"));
                        return 0;
                    }
                    MissionManager.completeAll(m);
                    MissionManager.onSurface(p, false);
                    return 1;
                }))
                .then(Commands.literal("leave").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Mission m = MissionManager.get(p);
                    if (m == null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.not_diving"));
                        return 0;
                    }
                    MissionManager.leaveMission(p, m, true);
                    return 1;
                }))
                .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                    Mission m = MissionManager.get(p);
                    if (m == null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.not_diving"));
                        return 0;
                    }
                    if (MissionManager.get(target) != null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.target_diving"));
                        return 0;
                    }
                    INVITES.put(target.getUUID(), new Object[]{m.id, System.currentTimeMillis() + INVITE_MS});
                    String name = p.getGameProfile().getName();
                    MutableComponent link = Component.translatable("ironlung.cmd.invite_link").withStyle(st -> st
                            .withColor(ChatFormatting.DARK_RED).withUnderlined(true)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ironlung join " + name))
                            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("/ironlung join " + name))));
                    target.sendSystemMessage(Component.translatable("ironlung.cmd.invited", name).withStyle(ChatFormatting.GRAY).append(" ").append(link));
                    ctx.getSource().sendSuccess(() -> Component.translatable("ironlung.cmd.invite_sent", target.getGameProfile().getName()), false);
                    return 1;
                })))
                .then(Commands.literal("join").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    ServerPlayer host = EntityArgument.getPlayer(ctx, "player");
                    if (MissionManager.get(p) != null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.msg.already_diving"));
                        return 0;
                    }
                    Mission m = MissionManager.get(host);
                    if (m == null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.host_not_diving"));
                        return 0;
                    }
                    Object[] inv = INVITES.get(p.getUUID());
                    boolean invited = inv != null && m.id.equals(inv[0]) && System.currentTimeMillis() < (Long) inv[1];
                    if (!invited && !com.ironlung.config.IronLungConfig.OPEN_JOIN.get() && !p.hasPermissions(2)) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.no_invite"));
                        return 0;
                    }
                    INVITES.remove(p.getUUID());
                    return MissionManager.joinMission(p, m) ? 1 : 0;
                })))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Mission m = MissionManager.get(p);
                    if (m == null) {
                        ctx.getSource().sendFailure(Component.translatable("ironlung.cmd.not_diving"));
                        return 0;
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                            "seed %d | crew %d | X %.1f Y %.1f | depth %.1f m | hull %d%% | power %s | sites %d/%d | tension %.2f",
                            m.seed, m.crew.size(), m.x, m.z, m.depth, Math.round(m.hull * 100), m.power, m.objectivesComplete(), m.objectiveDone.length, m.tension)), false);
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
                                    HorrorDirector.trigger(m, (ServerLevel) p.level(), t);
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
