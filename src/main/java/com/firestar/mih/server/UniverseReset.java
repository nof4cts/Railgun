package com.firestar.mih.server;

import com.firestar.mih.common.Beats;
import com.firestar.mih.common.Move;
import com.firestar.mih.network.Net;
import com.firestar.mih.network.S2CCast;
import com.firestar.mih.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.Vec3;

/**
 * The end of one universe and the start of the next. Everyone caught in the acceleration watches it together: held
 * still while everything is torn into a galaxy and swallowed, then set down at dawn in a new world, marked as from
 * an Alternate Universe. Survivors of the old world come out missing a limb; Pucci comes out healed.
 */
public final class UniverseReset {
    private static final List<Seq> SEQS = new ArrayList<>();

    private UniverseReset() {}

    static final class Seq {
        final ServerPlayer user;
        final ServerLevel level;
        final Vec3 center;
        final long start;
        final List<ServerPlayer> players = new ArrayList<>();
        final Map<UUID, Vec3> anchors = new HashMap<>();
        boolean applied;

        Seq(ServerPlayer user, Vec3 center) {
            this.user = user;
            this.level = user.serverLevel();
            this.center = center;
            this.start = level.getGameTime();
        }

        int age() {
            return (int) (level.getGameTime() - start);
        }
    }

    static void begin(TimeAccel.Run run) {
        ServerPlayer u = run.user;
        Seq s = new Seq(u, u.position());
        s.players.add(u);
        for (ServerPlayer pl : run.level.players()) {
            if (pl != u && pl.isAlive() && !pl.isSpectator() && (run.trapped.contains(pl.getUUID()) || pl.position().distanceTo(s.center) <= run.radius * 1.3)) {
                s.players.add(pl);
            }
        }
        int seed = u.getRandom().nextInt();
        for (ServerPlayer pl : s.players) {
            s.anchors.put(pl.getUUID(), pl.position());
            Net.toPlayer(pl, new S2CCast(Move.UNIVERSE_RESET, u.getId(), u.getEyePosition(), u.getLookAngle(), s.center, s.center, s.start, seed, -1));
        }
        s.level.playSound(null, s.center.x, s.center.y, s.center.z, ModSounds.RESET_RISE.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        SEQS.add(s);
    }

    public static boolean holding(ServerPlayer p) {
        for (Seq s : SEQS) {
            if (s.players.contains(p)) {
                return true;
            }
        }
        return false;
    }

    public static void tick(MinecraftServer server) {
        Iterator<Seq> it = SEQS.iterator();
        while (it.hasNext()) {
            Seq s = it.next();
            int age = s.age();
            if (age > Move.UNIVERSE_RESET.length) {
                it.remove();
                continue;
            }
            for (ServerPlayer pl : s.players) {
                Vec3 a = s.anchors.get(pl.getUUID());
                if (a == null || pl.isRemoved() || pl.level() != s.level) {
                    continue;
                }
                if (age < Beats.RESET_NEW + 30) {
                    if (pl.position().distanceToSqr(a) > 0.0025) {
                        pl.connection.teleport(a.x, a.y, a.z, pl.getYRot(), pl.getXRot());
                    }
                    pl.setDeltaMovement(Vec3.ZERO);
                    pl.fallDistance = 0.0F;
                }
            }
            if (age < Beats.RESET_LIFT) {
                boolean daylight = s.level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
                s.level.setDayTime(s.level.getDayTime() + (long) Beats.MAX_RATE);
                ClientboundSetTimePacket time = new ClientboundSetTimePacket(s.level.getGameTime(), s.level.getDayTime(), daylight);
                for (ServerPlayer pl : s.level.players()) {
                    pl.connection.send(time);
                }
            }
            if (age == Beats.RESET_SINGULARITY) {
                s.level.playSound(null, s.center.x, s.center.y, s.center.z, ModSounds.SINGULARITY.get(), SoundSource.PLAYERS, 2.5F, 1.0F);
            }
            if (age == Beats.RESET_NEW && !s.applied) {
                s.applied = true;
                apply(s);
            }
        }
    }

    private static void apply(Seq s) {
        ServerLevel level = s.level;
        // a new universe, a new dawn
        long day = level.getDayTime() / 24000L;
        level.setDayTime((day + 1) * 24000L);
        level.setWeatherParameters(12000, 0, false, false);
        boolean daylight = level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT);
        ClientboundSetTimePacket time = new ClientboundSetTimePacket(level.getGameTime(), level.getDayTime(), daylight);
        for (ServerPlayer pl : level.players()) {
            pl.connection.send(time);
        }
        level.playSound(null, s.center.x, s.center.y, s.center.z, ModSounds.NEW_UNIVERSE.get(), SoundSource.PLAYERS, 2.0F, 1.0F);
        long until = level.getGameTime() + 6000;
        for (ServerPlayer pl : s.players) {
            if (pl.isRemoved() || !pl.isAlive()) {
                continue;
            }
            StandUsers.State st = StandUsers.state(pl);
            st.auUntil = until;
            if (pl == s.user) {
                st.limb = StandUsers.LIMB_NONE;
                pl.heal(8.0F);
                pl.displayClientMessage(Component.literal("「一巡後の世界」 The universe has reset.").withStyle(ChatFormatting.GOLD), false);
            } else {
                st.limb = pl.getRandom().nextBoolean() ? StandUsers.LIMB_ARM : StandUsers.LIMB_LEG;
                pl.invulnerableTime = 0;
                pl.hurt(level.damageSources().magic(), 4.0F);
                pl.displayClientMessage(Component.literal("You wake in a new universe... missing your "
                        + (st.limb == StandUsers.LIMB_ARM ? "arm" : "leg") + ".").withStyle(ChatFormatting.RED), false);
            }
            pl.refreshDisplayName();
            StandUsers.sync(pl);
        }
    }

    public static void clearAll() {
        SEQS.clear();
    }
}
