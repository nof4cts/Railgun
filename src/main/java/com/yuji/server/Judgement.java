package com.yuji.server;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.yuji.Y;
import com.yuji.net.YNet;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Heavenly Judgement. Whoever types /kill at Modulo Yuji while "As God As My Witness" holds gets an answer: their
 * own screen goes into a cutscene that reads HEAVENLY JUDGEMENT, and when it ends they die - creative or not.
 */
@Mod.EventBusSubscriber(modid = "jjk")
public final class Judgement {
    /** How long the judgement plays on their screen before it lands (ticks). */
    public static final int LENGTH = 70;
    private static final List<Object[]> PENDING = new ArrayList<>();   // {victim UUID, server level, due tick}

    private Judgement() {}

    @SubscribeEvent
    public static void onCommand(CommandEvent e) {
        try {
            ParseResults<CommandSourceStack> parse = e.getParseResults();
            CommandContextBuilder<CommandSourceStack> ctx = parse.getContext();
            List<ParsedCommandNode<CommandSourceStack>> nodes = ctx.getNodes();
            if (nodes.isEmpty() || !"kill".equals(nodes.get(0).getNode().getName())) return;
            CommandSourceStack src = ctx.getSource();
            if (!(src.getEntity() instanceof ServerPlayer sender)) return;   // the console and command blocks can't be judged
            // who the /kill is aimed at: its target selector, or (bare /kill) the sender
            Collection<? extends Entity> targets;
            ParsedArgument<CommandSourceStack, ?> arg = findArg(ctx, "targets");
            if (arg != null && arg.getResult() instanceof EntitySelector sel) {
                targets = sel.findEntities(src);
            } else {
                targets = List.of(sender);
            }
            for (Entity t : targets) {
                if (t instanceof ServerPlayer yuji && yuji != sender && Yuji.is(yuji) && Yuji.god(yuji)) {
                    judge(sender, yuji);
                    return;
                }
            }
        } catch (Throwable t) {
            Y.LOG.debug("[yuji] couldn't read a /kill", t);
        }
    }

    private static ParsedArgument<CommandSourceStack, ?> findArg(CommandContextBuilder<CommandSourceStack> ctx, String name) {
        for (CommandContextBuilder<CommandSourceStack> c = ctx; c != null; c = c.getChild()) {
            ParsedArgument<CommandSourceStack, ?> a = c.getArguments().get(name);
            if (a != null) return a;
        }
        return null;
    }

    static void judge(ServerPlayer sender, ServerPlayer yuji) {
        for (Object[] p : PENDING) if (p[0].equals(sender.getUUID())) return;
        ServerLevel level = sender.serverLevel();
        PENDING.add(new Object[]{sender.getUUID(), level, level.getGameTime() + LENGTH});
        YNet.to(sender, new YNet.Judge(yuji.getGameProfile().getName(), LENGTH));
        Y.LOG.info("[yuji] Heavenly Judgement: {} raised /kill against {}", sender.getGameProfile().getName(), yuji.getGameProfile().getName());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        Iterator<Object[]> it = PENDING.iterator();
        while (it.hasNext()) {
            Object[] p = it.next();
            ServerLevel level = (ServerLevel) p[1];
            if (level.getGameTime() < (Long) p[2]) continue;
            it.remove();
            ServerPlayer v = e.getServer().getPlayerList().getPlayer((UUID) p[0]);
            if (v == null || !v.isAlive()) continue;
            // a bolt out of a clear sky, and they are gone - creative mode, totems and invulnerability don't matter
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(v.serverLevel());
            if (bolt != null) {
                bolt.moveTo(v.getX(), v.getY(), v.getZ());
                bolt.setVisualOnly(true);
                v.serverLevel().addFreshEntity(bolt);
            }
            v.invulnerableTime = 0;
            v.hurt(v.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            if (v.isAlive()) v.kill();
            Y.LOG.info("[yuji] Heavenly Judgement landed on {} (dead={})", v.getGameProfile().getName(), !v.isAlive());
        }
    }
}
