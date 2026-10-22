package com.woh.server;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.context.CommandContextBuilder;
import com.mojang.brigadier.context.ParsedArgument;
import com.mojang.brigadier.context.ParsedCommandNode;
import com.woh.W;
import com.woh.net.WNet;
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
 * Heavenly Judgement. Whoever types /kill at Modulo Woh while "As God As My Witness" holds gets an answer: their
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
                if (t instanceof ServerPlayer woh && woh != sender && Woh.is(woh) && Woh.god(woh)) {
                    judge(sender, woh);
                    return;
                }
            }
        } catch (Throwable t) {
            W.LOG.debug("[woh] couldn't read a /kill", t);
        }
    }

    private static ParsedArgument<CommandSourceStack, ?> findArg(CommandContextBuilder<CommandSourceStack> ctx, String name) {
        for (CommandContextBuilder<CommandSourceStack> c = ctx; c != null; c = c.getChild()) {
            ParsedArgument<CommandSourceStack, ?> a = c.getArguments().get(name);
            if (a != null) return a;
        }
        return null;
    }

    /** Heavenly Judgement on whoever struck him three times: a player gets the full cutscene and death, a mob is struck down. */
    public static void judgeEntity(net.minecraft.world.entity.Entity attacker, ServerPlayer woh) {
        if (attacker instanceof ServerPlayer sp) {
            judge(sp, woh);
        } else if (attacker instanceof net.minecraft.world.entity.LivingEntity le && le.isAlive()) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(le.level());
            if (bolt != null) {
                bolt.moveTo(le.getX(), le.getY(), le.getZ());
                le.level().addFreshEntity(bolt);
            }
            le.invulnerableTime = 0;
            le.hurt(le.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
            if (le.isAlive()) le.kill();
            W.LOG.info("[woh] Heavenly Judgement struck down {} for striking a god thrice", le.getName().getString());
        }
    }

    static void judge(ServerPlayer sender, ServerPlayer woh) {
        for (Object[] p : PENDING) if (p[0].equals(sender.getUUID())) return;
        ServerLevel level = sender.serverLevel();
        PENDING.add(new Object[]{sender.getUUID(), level, level.getGameTime() + LENGTH});
        WNet.to(sender, new WNet.Judge(woh.getGameProfile().getName(), LENGTH));
        W.LOG.info("[woh] Heavenly Judgement: {} raised /kill against {}", sender.getGameProfile().getName(), woh.getGameProfile().getName());
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
            W.LOG.info("[woh] Heavenly Judgement landed on {} (dead={})", v.getGameProfile().getName(), !v.isAlive());
        }
    }
}
