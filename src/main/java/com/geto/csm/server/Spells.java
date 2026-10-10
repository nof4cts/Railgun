package com.geto.csm.server;

import com.geto.csm.entity.CurseEntity;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.CutType;
import com.geto.csm.network.FxType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Three cataclysm spells. Each locks the caster into a cutscene, then really reshapes the
 * terrain (respecting the mobGriefing game rule; chests and other block entities are never touched).
 */
public final class Spells {
    public enum Spell { FALLING_STAR, EVENT_HORIZON, SKYFALL_LANCE }

    public static final int STAR_IMPACT = 82;    // 4.1 s
    public static final int HOLE_COLLAPSE = 84;  // 4.2 s
    public static final int LANCE_START = 40;    // 2.0 s
    private static final RandomSource RNG = RandomSource.create();

    public static void cast(ServerPlayer sp, Item item, Spell spell) {
        CsmState st = CsmState.get(sp);
        if (st.lockTicks > 0 || sp.getCooldowns().isOnCooldown(item)) return;
        switch (spell) {
            case FALLING_STAR -> fallingStar(sp, st);
            case EVENT_HORIZON -> eventHorizon(sp, st);
            case SKYFALL_LANCE -> skyfallLance(sp, st);
        }
        sp.getCooldowns().addCooldown(item, 20 * 40);
    }

    // ─────────────── helpers ───────────────

    private static Vec3 groundTarget(ServerPlayer sp, double range) {
        HitResult hit = sp.pick(range, 1f, false);
        Vec3 p = hit.getType() == HitResult.Type.MISS ? sp.getEyePosition().add(sp.getLookAngle().scale(range * 0.6)) : hit.getLocation();
        ServerLevel lvl = sp.serverLevel();
        int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
        if (hit.getType() == HitResult.Type.BLOCK) y = Math.min(y, Mth.floor(p.y) + 1);
        return new Vec3(p.x, y, p.z);
    }

    private static void lock(ServerPlayer sp, CsmState st, int ticks) {
        st.lockTicks = ticks;
        Combat.stun(sp, ticks - 6);
    }

    private static boolean griefing(ServerLevel lvl) {
        return lvl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    private static boolean breakable(ServerLevel lvl, BlockPos p, BlockState s) {
        if (s.isAir() || s.hasBlockEntity()) return false;
        float h = s.getDestroySpeed(lvl, p);
        return h >= 0 && h < 50;
    }

    /** Carve an ellipsoid of terrain, with a ragged edge. Only y-slices [yFrom, yTo] relative to centre. */
    private static void carve(ServerLevel lvl, Vec3 c, double rx, double ry, double rz, int yFrom, int yTo, boolean fireRim) {
        if (!griefing(lvl)) return;
        int cx = Mth.floor(c.x), cy = Mth.floor(c.y), cz = Mth.floor(c.z);
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int dy = yFrom; dy <= yTo; dy++) {
            for (int dx = -(int) rx - 1; dx <= rx + 1; dx++) {
                for (int dz = -(int) rz - 1; dz <= rz + 1; dz++) {
                    double ex = dx / rx, ey = dy / ry, ez = dz / rz;
                    double d = ex * ex + ey * ey + ez * ez;
                    double jitter = 1 + (RNG.nextDouble() - 0.5) * 0.18;
                    mp.set(cx + dx, cy + dy, cz + dz);
                    if (d <= jitter) {
                        BlockState s = lvl.getBlockState(mp);
                        if (breakable(lvl, mp, s)) lvl.setBlock(mp, Blocks.AIR.defaultBlockState(), 3);
                    } else if (fireRim && d <= jitter + 0.35 && RNG.nextFloat() < 0.07 && lvl.getBlockState(mp).isAir()
                            && lvl.getBlockState(mp.below()).isFaceSturdy(lvl, mp.below(), net.minecraft.core.Direction.UP)) {
                        lvl.setBlock(mp, BaseFireBlock.getState(lvl, mp), 3);
                    }
                }
            }
        }
    }

    private static List<LivingEntity> victims(ServerPlayer sp, Vec3 c, double r) {
        return sp.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                e -> e != sp && e.isAlive() && !e.isSpectator() && !(e instanceof CurseEntity ce && ce.ownerId() == sp.getId())
                        && e.position().distanceTo(c) <= r);
    }

    // ─────────────── 1. FALLING STAR ───────────────

    private static void fallingStar(ServerPlayer sp, CsmState st) {
        Vec3 target = groundTarget(sp, 64);
        lock(sp, st, 150);
        CsmNetwork.cutscene(sp, CutType.STAR, sp.getId(), Mth.floor(target.x * 10), Mth.floor(target.y * 10), Mth.floor(target.z * 10));
        CsmNetwork.fx(sp, FxType.STAR_FALL, sp, null, target, 0, 0);
        Combat.sound(sp, SoundEvents.BEACON_ACTIVATE, 2f, 0.4f);
        Scheduler.after(30, () -> Combat.sound(sp.serverLevel(), target, SoundEvents.ELYTRA_FLYING, 4f, 0.5f));
        Scheduler.after(STAR_IMPACT, () -> {
            ServerLevel lvl = sp.serverLevel();
            CsmNetwork.fx(lvl, FxType.STAR_IMPACT, sp.getId(), -1, target, 0, 0);
            Combat.sound(lvl, target, SoundEvents.GENERIC_EXPLODE, 8f, 0.4f);
            Combat.sound(lvl, target, SoundEvents.LIGHTNING_BOLT_THUNDER, 8f, 0.5f);
            for (LivingEntity e : victims(sp, target, 16)) {
                double d = e.position().distanceTo(target);
                float dmg = (float) (70 * (1 - d / 18));
                Combat.damage(sp, e, Math.max(8, dmg), true);
                e.setSecondsOnFire(8);
                Vec3 away = e.position().subtract(target).normalize();
                CurseEntity.launch(e, away.scale(2.8 * (1 - d / 20)).add(0, 1.4, 0));
            }
            carve(lvl, target, 9.5, 6.5, 9.5, -6, -2, false);
            Scheduler.after(1, () -> carve(lvl, target, 9.5, 6.5, 9.5, -1, 2, true));
            Scheduler.after(2, () -> carve(lvl, target, 9.5, 6.5, 9.5, 3, 6, false));
        });
    }

    // ─────────────── 2. EVENT HORIZON ───────────────

    private static void eventHorizon(ServerPlayer sp, CsmState st) {
        Vec3 center = groundTarget(sp, 36).add(0, 3.5, 0);
        lock(sp, st, 140);
        CsmNetwork.cutscene(sp, CutType.HOLE, sp.getId(), Mth.floor(center.x * 10), Mth.floor(center.y * 10), Mth.floor(center.z * 10));
        CsmNetwork.fx(sp, FxType.HOLE_FORM, sp, null, center, 0, 0);
        Combat.sound(sp, SoundEvents.WARDEN_SONIC_CHARGE, 3f, 0.3f);
        List<FallingBlockEntity> ripped = new ArrayList<>();
        ServerLevel lvl = sp.serverLevel();
        for (int t = 16; t < HOLE_COLLAPSE; t++) {
            final int tick = t;
            Scheduler.after(t, () -> {
                float k = (tick - 16) / (float) (HOLE_COLLAPSE - 16);
                double reach = 8 + 16 * k;
                for (LivingEntity e : victims(sp, center, reach)) {
                    Vec3 to = center.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                    double d = to.length();
                    if (d < 1.0e-3) continue;
                    Vec3 v = e.getDeltaMovement().scale(0.8).add(to.scale((0.1 + 0.25 * k) / d));
                    e.setDeltaMovement(v);
                    e.hurtMarked = true;
                    e.fallDistance = 0;
                    if (tick % 10 == 0) Combat.damage(sp, e, 3f + 4f * k, true);
                }
                if (tick % 2 == 0 && griefing(lvl) && ripped.size() < 70) rip(lvl, center, 4 + 6 * k, ripped);
                for (FallingBlockEntity fb : ripped) {
                    if (!fb.isAlive()) continue;
                    Vec3 to = center.subtract(fb.position());
                    double d = Math.max(0.8, to.length());
                    Vec3 tan = to.cross(new Vec3(0, 1, 0)).normalize().scale(0.25);
                    fb.setDeltaMovement(fb.getDeltaMovement().scale(0.85).add(to.scale(0.08 / d * 6)).add(tan));
                    fb.time = 1;
                }
                if (tick % 20 == 0) Combat.sound(lvl, center, SoundEvents.WARDEN_HEARTBEAT, 4f, 0.4f);
            });
        }
        Scheduler.after(HOLE_COLLAPSE, () -> {
            for (FallingBlockEntity fb : ripped) fb.discard();
            CsmNetwork.fx(lvl, FxType.HOLE_COLLAPSE, sp.getId(), -1, center, 0, 0);
            Combat.sound(lvl, center, SoundEvents.WARDEN_SONIC_BOOM, 8f, 0.4f);
            Combat.sound(lvl, center, SoundEvents.GENERIC_EXPLODE, 8f, 0.5f);
            for (LivingEntity e : victims(sp, center, 13)) {
                Combat.damage(sp, e, 50f, true);
                CurseEntity.launch(e, e.position().subtract(center).normalize().scale(3.0).add(0, 1.0, 0));
            }
            carve(lvl, center, 8.5, 8.5, 8.5, -9, 0, false);
            Scheduler.after(1, () -> carve(lvl, center, 8.5, 8.5, 8.5, 1, 9, false));
        });
    }

    private static void rip(ServerLevel lvl, Vec3 center, double radius, List<FallingBlockEntity> out) {
        for (int tries = 0; tries < 6; tries++) {
            double a = RNG.nextDouble() * Math.PI * 2, r = RNG.nextDouble() * radius;
            int x = Mth.floor(center.x + Math.cos(a) * r), z = Mth.floor(center.z + Math.sin(a) * r);
            int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = lvl.getBlockState(p);
            if (!breakable(lvl, p, s) || !s.getFluidState().isEmpty()) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(lvl, p, s);
            fb.dropItem = false;
            fb.setNoGravity(true);
            fb.setDeltaMovement(0, 0.6, 0);
            out.add(fb);
            CsmNetwork.fx(lvl, FxType.RIP_BLOCK, fb.getId(), -1, Vec3.atCenterOf(p), 0, 0);
            return;
        }
    }

    // ─────────────── 3. SKYFALL LANCE ───────────────

    private static void skyfallLance(ServerPlayer sp, CsmState st) {
        Vec3 dir = Combat.flatLook(sp);
        Vec3 start = sp.position().add(dir.scale(5));
        double length = 36;
        lock(sp, st, 130);
        float yaw = sp.getYRot();
        CsmNetwork.cutscene(sp, CutType.LANCE, sp.getId(), Mth.floor(start.x * 10), Mth.floor(start.y * 10), Mth.floor(start.z * 10), Mth.floor(yaw * 10));
        CsmNetwork.fx(sp, FxType.LANCE_SIGIL, sp, null, start, yaw, (float) length);
        Combat.sound(sp, SoundEvents.BEACON_POWER_SELECT, 3f, 0.5f);
        Combat.sound(sp, SoundEvents.BELL_RESONATE, 3f, 0.6f);
        ServerLevel lvl = sp.serverLevel();
        Scheduler.after(LANCE_START, () -> CsmNetwork.fx(lvl, FxType.LANCE_STRIKE, sp.getId(), -1, start, yaw, (float) length));
        int steps = 30;
        for (int k = 0; k <= steps; k++) {
            final int step = k;
            Scheduler.after(LANCE_START + 2 + k, () -> {
                Vec3 p = start.add(dir.scale(length * step / steps));
                int gy = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
                Vec3 g = new Vec3(p.x, gy, p.z);
                carve(lvl, g, 2.8, 4.5, 2.8, -5, 3, step % 3 == 0);
                for (LivingEntity e : victims(sp, g, 4.5)) {
                    Combat.damage(sp, e, 38f, true);
                    e.setSecondsOnFire(6);
                    CurseEntity.launch(e, e.position().subtract(g).normalize().scale(1.6).add(0, 1.2, 0));
                }
                if (step % 5 == 0) Combat.sound(lvl, g, SoundEvents.GENERIC_EXPLODE, 4f, 0.6f);
            });
        }
        sp.displayClientMessage(Component.literal("§eSkyfall Lance — target locked."), true);
    }

    private Spells() {}
}
