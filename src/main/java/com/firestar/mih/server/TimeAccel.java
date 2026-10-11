package com.firestar.mih.server;

import com.firestar.mih.common.Beats;
import com.firestar.mih.config.ZConfig;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Made in Heaven's Time Acceleration. Everything but living things speeds up: the sun races, then the days blur into a
 * strip of light; crops grow, thrown things fly. Those near him are held inside the acceleration with him. When it
 * reaches the end, the universe resets.
 */
public final class TimeAccel {
    private static final List<Run> RUNS = new ArrayList<>();

    private TimeAccel() {}

    public static final class Run {
        public final ServerPlayer user;
        public final ServerLevel level;
        public final Vec3 center;
        public final long start;
        public final int length;
        public final float radius;
        final Set<UUID> trapped = new HashSet<>();
        int savedRandomTick = -1;
        double carry;

        Run(ServerPlayer user, int length, float radius) {
            this.user = user;
            this.level = user.serverLevel();
            this.center = user.position();
            this.start = level.getGameTime();
            this.length = length;
            this.radius = radius;
        }

        public int age() {
            return (int) (level.getGameTime() - start);
        }
    }

    public static Run of(ServerPlayer p) {
        for (Run r : RUNS) {
            if (r.user == p) {
                return r;
            }
        }
        return null;
    }

    public static boolean anyIn(ServerLevel level) {
        for (Run r : RUNS) {
            if (r.level == level) {
                return true;
            }
        }
        return false;
    }

    public static void start(ServerPlayer p) {
        if (of(p) != null) {
            return;
        }
        int length = ZConfig.SERVER.accelSeconds.get() * 20;
        if (Boolean.getBoolean("mih.selftest")) {
            length = Integer.getInteger("mih.selftest.accel", 200);
        }
        Run r = new Run(p, length, ZConfig.SERVER.accelRadius.get());
        for (ServerPlayer other : r.level.players()) {
            if (other != p && other.position().distanceTo(r.center) <= r.radius) {
                r.trapped.add(other.getUUID());
                other.displayClientMessage(Component.literal("Time is accelerating. You can't get away.").withStyle(ChatFormatting.AQUA), true);
            }
        }
        if (ZConfig.SERVER.speedUpWorld.get()) {
            r.savedRandomTick = r.level.getGameRules().getInt(GameRules.RULE_RANDOMTICKING);
        }
        RUNS.add(r);
        StandUsers.sync(p);
    }

    public static void cancel(ServerPlayer p) {
        Run r = of(p);
        if (r != null) {
            end(r, false);
        }
    }

    private static void end(Run r, boolean reset) {
        RUNS.remove(r);
        if (r.savedRandomTick >= 0) {
            r.level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(r.savedRandomTick, r.level.getServer());
        }
        if (!r.user.isRemoved()) {
            r.user.removeEffect(MobEffects.MOVEMENT_SPEED);
            StandUsers.sync(r.user);
        }
        if (reset && ZConfig.SERVER.universeReset.get() && r.user.isAlive() && !r.user.isRemoved()) {
            UniverseReset.begin(r);
        }
    }

    public static void tick(MinecraftServer server) {
        Iterator<Run> it = new ArrayList<>(RUNS).iterator();
        while (it.hasNext()) {
            Run r = it.next();
            ServerPlayer u = r.user;
            if (u.isRemoved() || !u.isAlive() || u.level() != r.level) {
                end(r, false);
                continue;
            }
            int age = r.age();
            if (age >= r.length) {
                end(r, true);
                continue;
            }
            double rate = Beats.accelRate(age / (double) r.length);
            // the day runs on: the extra on top of the tick the level already counts
            boolean daylight = r.level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
            r.carry += rate - (daylight ? 1.0 : 0.0);
            long add = (long) r.carry;
            r.carry -= add;
            r.level.setDayTime(r.level.getDayTime() + add);
            ClientboundSetTimePacket time = new ClientboundSetTimePacket(r.level.getGameTime(), r.level.getDayTime(), daylight);
            for (ServerPlayer pl : r.level.players()) {
                pl.connection.send(time);
            }

            if (r.savedRandomTick >= 0) {
                int boosted = (int) Math.min(160, Math.max(r.savedRandomTick, 3 + rate / 8.0));
                r.level.getGameRules().getRule(GameRules.RULE_RANDOMTICKING).set(boosted, server);
                int extra = (int) Math.min(4, rate / 180.0);
                if (extra > 0) {
                    AABB box = new AABB(r.center, r.center).inflate(r.radius);
                    for (Entity e : r.level.getEntities((Entity) null, box, e -> !(e instanceof LivingEntity) && (e instanceof ItemEntity
                            || (e instanceof Projectile && !(e instanceof KnifeEntity)) || e instanceof FallingBlockEntity || e instanceof PrimedTnt))) {
                        for (int i = 0; i < extra && !e.isRemoved(); i++) {
                            e.tick();
                        }
                    }
                }
            }

            // the ones caught with him stay caught
            for (UUID id : r.trapped) {
                if (!(r.level.getPlayerByUUID(id) instanceof ServerPlayer pl) || !pl.isAlive()) {
                    continue;
                }
                Vec3 off = pl.position().subtract(r.center);
                double d = off.length();
                if (d > r.radius) {
                    Vec3 back = r.center.add(off.scale((r.radius - 0.8) / d));
                    pl.connection.teleport(back.x, Math.max(back.y, pl.getY()), back.z, pl.getYRot(), pl.getXRot());
                    pl.displayClientMessage(Component.literal("Time won't let you leave.").withStyle(ChatFormatting.AQUA), true);
                }
            }

            if (age % 40 == 0) {
                u.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 50, 3, false, false, true));
            }
            Techniques.hasten(u);
            if (age % 20 == 0) {
                Techniques.syncCooldowns(u);
            }
        }
    }

    public static void clearAll() {
        for (Run r : new ArrayList<>(RUNS)) {
            end(r, false);
        }
        RUNS.clear();
    }
}
