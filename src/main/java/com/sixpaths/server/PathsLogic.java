package com.sixpaths.server;

import com.sixpaths.Ability;
import com.sixpaths.ModRegistry;
import com.sixpaths.entity.BeastEntity;
import com.sixpaths.entity.MissileEntity;
import com.sixpaths.entity.RodEntity;
import com.sixpaths.network.CutType;
import com.sixpaths.network.FxType;
import com.sixpaths.network.PathsNetwork;
import com.sixpaths.network.StatePacket;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Every technique of the Six Paths. Server-authoritative: damage, movement and terrain happen here;
 * the client is told what to draw through FX / cutscene packets. Terrain edits follow mobGriefing and
 * never touch block entities (chests, furnaces…).
 */
public final class PathsLogic {
    private static final RandomSource RNG = RandomSource.create();

    // ══════════════════════════ selection / cooldowns / sync ══════════════════════════

    public static void select(ServerPlayer sp, int id) {
        PathsState st = PathsState.get(sp);
        st.selected = Ability.byId(id).ordinal();
        sync(sp, st);
    }

    public static void cast(ServerPlayer sp) {
        PathsState st = PathsState.get(sp);
        if (st.lockTicks > 0) return;
        Ability ab = Ability.byId(st.selected);
        long now = sp.level().getGameTime();
        long ready = st.readyAt[ab.ordinal()];
        if (ready > now && !sp.isCreative()) {
            sp.displayClientMessage(Component.literal("§7" + ab.title + " §8— §c" + String.format("%.1f", (ready - now) / 20f) + "s"), true);
            return;
        }
        boolean used = switch (ab) {
            case ALMIGHTY_PUSH -> push(sp, st);
            case UNIVERSAL_PULL -> pull(sp, st);
            case CHAKRA_RODS -> rods(sp, st);
            case PLANETARY_DEVASTATION -> planetary(sp, st);
            case HEAVENLY_DESCENT -> descent(sp, st);
            case ASURA_BARRAGE -> barrage(sp, st);
            case ASURA_CANNON -> cannon(sp, st);
            case SOUL_EXTRACTION -> soul(sp, st);
            case SUMMON_RHINO -> summon(sp, st, 0);
            case SUMMON_CENTIPEDE -> summon(sp, st, 1);
            case SUMMON_HOUND -> summon(sp, st, 2);
            case SUMMON_BIRD -> summon(sp, st, 3);
            case PRETA_ABSORPTION -> preta(sp, st);
            case KING_OF_HELL -> kingOfHell(sp, st);
            case SAMSARA -> samsara(sp, st);
            case ASCENSION -> ascend(sp, st);
            case PATH_SIGHT -> sight(sp, st);
        };
        if (used) {
            int cd = ab.cooldown;
            if (st.ascending(now) && ab != Ability.ASCENSION) cd /= 2;
            st.readyAt[ab.ordinal()] = now + cd;
            sync(sp, st);
        }
    }

    public static void sync(ServerPlayer sp, PathsState st) {
        long now = sp.level().getGameTime();
        int[] cds = new int[st.readyAt.length];
        for (int i = 0; i < cds.length; i++) cds[i] = (int) Math.max(0, st.readyAt[i] - now);
        PathsNetwork.toPlayer(sp, new StatePacket(st.selected,
                (int) Math.max(0, st.pretaUntil - now), (int) Math.max(0, st.ascendUntil - now), cds));
    }

    public static void tick(ServerPlayer sp) {
        PathsState st = PathsState.peek(sp);
        if (st == null) return;
        long now = sp.level().getGameTime();
        if (st.lockTicks > 0) st.lockTicks--;
        if (now < st.noFallUntil) sp.fallDistance = 0;

        if (st.preta(now)) {
            // the Preta seal drinks anything thrown at you
            for (Projectile p : sp.level().getEntitiesOfClass(Projectile.class, sp.getBoundingBox().inflate(3.5),
                    p -> p.getOwner() != sp && !(p instanceof RodEntity) && !(p instanceof MissileEntity))) {
                PathsNetwork.fx(sp, FxType.PRETA_DRINK, sp, null, p.position(), 1, 0);
                Combat.sound(sp, SoundEvents.BEACON_POWER_SELECT, 0.8f, 1.8f);
                sp.heal(2f);
                p.discard();
            }
        }

        if (st.ascended) {
            sp.fallDistance = 0;
            if (!st.ascending(now)) {
                st.ascended = false;
                if (!sp.isCreative() && !sp.isSpectator()) {
                    sp.getAbilities().mayfly = false;
                    sp.getAbilities().flying = false;
                    sp.onUpdateAbilities();
                }
                sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, false, false));
                sp.displayClientMessage(Component.literal("§7The ascension fades."), true);
            } else if (!sp.getAbilities().mayfly) {
                sp.getAbilities().mayfly = true;
                sp.onUpdateAbilities();
            }
        }

        if (--st.syncIn <= 0) {
            st.syncIn = 10;
            sync(sp, st);
        }
    }

    public static void endAll(ServerPlayer sp) {
        PathsState st = PathsState.peek(sp);
        if (st != null && st.ascended && !sp.isCreative() && !sp.isSpectator()) {
            sp.getAbilities().mayfly = false;
            sp.getAbilities().flying = false;
            sp.onUpdateAbilities();
        }
        PathsState.remove(sp);
    }

    // ══════════════════════════ helpers ══════════════════════════

    static boolean foe(ServerPlayer sp, Entity e) {
        if (!(e instanceof LivingEntity le) || e == sp || !le.isAlive() || e.isSpectator()) return false;
        if (e instanceof BeastEntity b && b.ownerId() == sp.getId()) return false;
        return !(e.getVehicle() instanceof BeastEntity b2 && b2.ownerId() == sp.getId());
    }

    static List<LivingEntity> foes(ServerPlayer sp, Vec3 c, double r) {
        return sp.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r),
                        e -> foe(sp, e) && e.getBoundingBox().getCenter().distanceTo(c) <= r + e.getBbWidth() / 2)
                .stream().sorted(Comparator.comparingDouble(e -> e.distanceToSqr(c))).toList();
    }

    static LivingEntity aimed(ServerPlayer sp, double range, double minDot) {
        for (LivingEntity e : Combat.cone(sp, range, minDot)) if (foe(sp, e) && sp.hasLineOfSight(e)) return e;
        return null;
    }

    static void lock(ServerPlayer sp, PathsState st, int ticks) {
        st.lockTicks = ticks;
        Combat.stun(sp, ticks - 4);
    }

    static boolean griefing(ServerLevel lvl) {
        return lvl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    static boolean breakable(ServerLevel lvl, BlockPos p, BlockState s) {
        if (s.isAir() || s.hasBlockEntity()) return false;
        float h = s.getDestroySpeed(lvl, p);
        return h >= 0 && h < 50;
    }

    static Vec3 groundTarget(ServerPlayer sp, double range) {
        HitResult hit = sp.pick(range, 1f, false);
        Vec3 p = hit.getType() == HitResult.Type.MISS ? sp.getEyePosition().add(sp.getLookAngle().scale(range * 0.6)) : hit.getLocation();
        ServerLevel lvl = sp.serverLevel();
        int y = lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(p.x), Mth.floor(p.z));
        if (hit.getType() == HitResult.Type.BLOCK) y = Math.min(y, Mth.floor(p.y) + 1);
        return new Vec3(p.x, y, p.z);
    }

    static int ground(ServerLevel lvl, double x, double z) {
        return lvl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
    }

    static int[] enc(Vec3 v) {
        return new int[]{Mth.floor(v.x * 10), Mth.floor(v.y * 10), Mth.floor(v.z * 10)};
    }

    static int[] cat(int[] a, int... b) {
        int[] r = new int[a.length + b.length];
        System.arraycopy(a, 0, r, 0, a.length);
        System.arraycopy(b, 0, r, a.length, b.length);
        return r;
    }

    // ══════════════════════════ DEVA PATH ══════════════════════════

    /** 神羅天征 — repel everything around you. */
    static boolean push(ServerPlayer sp, PathsState st) {
        ServerLevel lvl = sp.serverLevel();
        boolean asc = st.ascending(lvl.getGameTime());
        double r = asc ? 14 : 9;
        Vec3 c = sp.position().add(0, 1, 0);
        PathsNetwork.fx(sp, FxType.PUSH, sp, null, c, (float) r, asc ? 1 : 0);
        Combat.sound(sp, SoundEvents.WARDEN_SONIC_BOOM, 2.5f, 1.25f);
        Combat.sound(sp, SoundEvents.ENDER_DRAGON_FLAP, 3f, 0.5f);
        Combat.sound(sp, SoundEvents.GENERIC_EXPLODE, 1.5f, 0.6f);
        for (LivingEntity e : foes(sp, c, r)) {
            Vec3 d = e.getBoundingBox().getCenter().subtract(c);
            double dist = Math.max(0.5, d.length());
            double k = 1 - dist / (r + 2);
            Combat.damage(sp, e, (float) ((asc ? 16 : 10) * (0.5 + 0.5 * k)), true);
            Combat.launch(e, d.normalize().scale(0.8 + 2.6 * k).add(0, 0.5 + 0.5 * k, 0));
        }
        for (Entity e : lvl.getEntities(sp, new AABB(c, c).inflate(r), x -> !(x instanceof LivingEntity) && !(x instanceof RodEntity) && !(x instanceof MissileEntity))) {
            Vec3 d = e.position().subtract(c);
            if (d.length() > r) continue;
            if (e instanceof Projectile p) {
                Vec3 v = p.getDeltaMovement();
                p.setOwner(sp);
                p.setDeltaMovement(d.normalize().scale(Math.max(1.2, v.length() * 1.4)));
            } else {
                e.setDeltaMovement(d.normalize().scale(1.6).add(0, 0.5, 0));
            }
            e.hurtMarked = true;
        }
        // soft terrain around you is blown flat (grass, leaves, flowers, snow, glass panes...)
        if (griefing(lvl)) {
            int rr = (int) (r * 0.6);
            BlockPos base = sp.blockPosition();
            for (BlockPos p : BlockPos.betweenClosed(base.offset(-rr, 0, -rr), base.offset(rr, rr, rr))) {
                if (p.distSqr(base) > rr * rr) continue;
                BlockState s = lvl.getBlockState(p);
                if (breakable(lvl, p, s) && s.getDestroySpeed(lvl, p) < 0.8f && s.getFluidState().isEmpty()) lvl.destroyBlock(p, false);
            }
        }
        return true;
    }

    /** 万象天引 — drag one target to you and impale it on a receiver. */
    static boolean pull(ServerPlayer sp, PathsState st) {
        LivingEntity t = aimed(sp, 32, 0.92);
        if (t == null) {
            sp.displayClientMessage(Component.literal("§7No target in sight."), true);
            return false;
        }
        PathsNetwork.fx(sp, FxType.PULL, sp, t, t.position(), 0, 0);
        Combat.sound(sp, SoundEvents.WARDEN_SONIC_CHARGE, 1.5f, 1.6f);
        Combat.sound(t, SoundEvents.ENDER_DRAGON_FLAP, 2f, 1.4f);
        for (int i = 1; i <= 9; i++) {
            final int k = i;
            Scheduler.after(i, () -> {
                if (!t.isAlive()) return;
                Vec3 hold = sp.getEyePosition().add(Combat.flatLook(sp).scale(1.8)).subtract(0, t.getBbHeight() * 0.6, 0);
                Vec3 to = hold.subtract(t.position());
                Combat.launch(t, to.scale(k < 9 ? 0.45 : 0.1));
                t.fallDistance = 0;
            });
        }
        Scheduler.after(10, () -> {
            if (!t.isAlive()) return;
            Combat.damage(sp, t, 9f, true);
            Combat.stun(t, 40);
            t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 1));
            PathsNetwork.fx(sp, FxType.ROD_HIT, sp, t, t.getBoundingBox().getCenter(), 2, 0);
            Combat.sound(t, SoundEvents.TRIDENT_HIT, 2f, 0.5f);
            Combat.sound(t, SoundEvents.PLAYER_ATTACK_CRIT, 2f, 0.6f);
        });
        return true;
    }

    /** Black receivers: three rods in a fan. */
    static boolean rods(ServerPlayer sp, PathsState st) {
        for (int i = -1; i <= 1; i++) {
            RodEntity rod = new RodEntity(sp.level(), sp);
            rod.shootFromRotation(sp, sp.getXRot(), sp.getYRot() + i * 6f, 0f, 2.8f, 0.2f);
            sp.level().addFreshEntity(rod);
        }
        PathsNetwork.fx(sp, FxType.ROD_THROW, sp, null, sp.getEyePosition(), sp.getYRot(), sp.getXRot());
        Combat.sound(sp, SoundEvents.TRIDENT_THROW, 1.5f, 0.6f);
        return true;
    }

    /** 地爆天星 — a gravity core that tears the ground into the sky and seals everything inside a moon. */
    static boolean planetary(ServerPlayer sp, PathsState st) {
        ServerLevel lvl = sp.serverLevel();
        Vec3 ground = groundTarget(sp, 48);
        Vec3 core = ground.add(0, 14, 0);
        lock(sp, st, 150);
        PathsNetwork.cutscene(sp, CutType.CHIBAKU, sp.getId(), enc(core));
        PathsNetwork.fx(lvl, FxType.CORE_THROW, sp.getId(), -1, core, 0, 0);
        Combat.sound(sp, SoundEvents.BEACON_ACTIVATE, 2f, 0.3f);
        final int SEAL = 125;
        List<FallingBlockEntity> ripped = new ArrayList<>();
        List<BlockState> palette = new ArrayList<>();
        for (int t = 22; t < SEAL; t++) {
            final int tick = t;
            Scheduler.after(t, () -> {
                float k = (tick - 22) / (float) (SEAL - 22);
                double reach = 10 + 14 * k;
                for (LivingEntity e : foes(sp, core, reach + 6)) {
                    Vec3 to = core.subtract(e.position().add(0, e.getBbHeight() / 2, 0));
                    double d = to.length();
                    if (d < 1.0e-3) continue;
                    e.setDeltaMovement(e.getDeltaMovement().scale(0.75).add(to.scale((0.12 + 0.22 * k) / d)));
                    e.hurtMarked = true;
                    e.fallDistance = 0;
                    if (tick % 15 == 0) Combat.damage(sp, e, 2f + 3f * k, true);
                }
                if (griefing(lvl) && ripped.size() < 220 && tick % 1 == 0) {
                    for (int n = 0; n < 2; n++) rip(lvl, ground, 4 + 16 * k, ripped, palette);
                }
                for (FallingBlockEntity fb : ripped) {
                    if (!fb.isAlive()) continue;
                    Vec3 to = core.subtract(fb.position());
                    double d = to.length();
                    fb.time = 1;
                    double shell = 6.5 + (fb.getId() % 3) * 0.6;
                    if (d < shell) {
                        // locked into the forming moon: orbit slowly on the shell
                        Vec3 tan = to.cross(new Vec3(0, 1, 0)).normalize().scale(0.06);
                        fb.setDeltaMovement(tan.add(to.normalize().scale((d - shell) * -0.1)));
                    } else {
                        fb.setDeltaMovement(fb.getDeltaMovement().scale(0.86).add(to.scale(0.11 / Math.max(1, d) * 5)));
                    }
                }
                if (tick % 20 == 0) Combat.sound(lvl, core, SoundEvents.WARDEN_HEARTBEAT, 5f, 0.3f);
                if (tick % 12 == 0) Combat.sound(lvl, core, SoundEvents.GRAVEL_BREAK, 4f, 0.4f);
            });
        }
        Scheduler.after(SEAL, () -> {
            for (FallingBlockEntity fb : ripped) fb.discard();
            PathsNetwork.fx(lvl, FxType.SPHERE_SEAL, sp.getId(), -1, core, 8, 0);
            Combat.sound(lvl, core, SoundEvents.GENERIC_EXPLODE, 8f, 0.4f);
            Combat.sound(lvl, core, SoundEvents.ANVIL_LAND, 6f, 0.3f);
            List<LivingEntity> trapped = foes(sp, core, 10);
            for (LivingEntity e : trapped) {
                Combat.damage(sp, e, 40f, true);
                Vec3 in = core.add(e.position().subtract(core).normalize().scale(3));
                e.teleportTo(in.x, in.y, in.z);
                Combat.stun(e, 120);
                e.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 140, 0));
            }
            if (griefing(lvl)) buildMoon(lvl, core, 7.5, palette);
        });
        sp.displayClientMessage(Component.literal("§5Planetary Devastation"), true);
        return true;
    }

    static void rip(ServerLevel lvl, Vec3 around, double radius, List<FallingBlockEntity> out, List<BlockState> palette) {
        for (int tries = 0; tries < 6; tries++) {
            double a = RNG.nextDouble() * Math.PI * 2, r = Math.sqrt(RNG.nextDouble()) * radius;
            int x = Mth.floor(around.x + Math.cos(a) * r), z = Mth.floor(around.z + Math.sin(a) * r);
            int y = ground(lvl, x, z) - 1;
            BlockPos p = new BlockPos(x, y, z);
            BlockState s = lvl.getBlockState(p);
            if (!breakable(lvl, p, s) || !s.getFluidState().isEmpty()) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(lvl, p, s);
            fb.dropItem = false;
            fb.setNoGravity(true);
            fb.setDeltaMovement((RNG.nextDouble() - 0.5) * 0.2, 0.5 + RNG.nextDouble() * 0.4, (RNG.nextDouble() - 0.5) * 0.2);
            out.add(fb);
            if (s.isSolidRender(lvl, p) && palette.size() < 64) palette.add(s);
            if (RNG.nextInt(3) == 0) PathsNetwork.fx(lvl, FxType.RIP_BLOCK, fb.getId(), -1, Vec3.atCenterOf(p), 0, 0);
            return;
        }
    }

    static void buildMoon(ServerLevel lvl, Vec3 c, double r, List<BlockState> palette) {
        if (palette.isEmpty()) palette.add(Blocks.STONE.defaultBlockState());
        palette.add(Blocks.COBBLESTONE.defaultBlockState());
        palette.add(Blocks.COARSE_DIRT.defaultBlockState());
        int cx = Mth.floor(c.x), cy = Mth.floor(c.y), cz = Mth.floor(c.z), R = (int) Math.ceil(r) + 1;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz) + (RNG.nextDouble() - 0.5) * 0.6;
            if (d > r || d < r - 1.6) continue;
            mp.set(cx + dx, cy + dy, cz + dz);
            if (!lvl.getBlockState(mp).isAir()) continue;
            lvl.setBlock(mp, palette.get(RNG.nextInt(palette.size())), 2);
        }
    }

    /** 神羅天征・天降 — rise above the battlefield and flatten everything below into a crater. */
    static boolean descent(ServerPlayer sp, PathsState st) {
        ServerLevel lvl = sp.serverLevel();
        long now = lvl.getGameTime();
        Vec3 origin = sp.position();
        double topY = origin.y + 22;
        st.lockTicks = 175;
        st.noFallUntil = now + 260;
        PathsNetwork.cutscene(sp, CutType.DESCENT, sp.getId(), enc(origin));
        PathsNetwork.fx(sp, FxType.DESCENT_RISE, sp, null, origin, 0, 0);
        Combat.sound(sp, SoundEvents.BEACON_ACTIVATE, 2f, 0.5f);
        Combat.sound(sp, SoundEvents.ELYTRA_FLYING, 1f, 0.6f);
        for (int t = 1; t <= 70; t++) {
            Scheduler.after(t, () -> {
                double dy = topY - sp.getY();
                sp.setDeltaMovement(0, Mth.clamp(dy * 0.12, -0.2, 0.9), 0);
                sp.hurtMarked = true;
                sp.fallDistance = 0;
            });
        }
        final int BLAST = 72;
        Scheduler.after(BLAST - 18, () -> Combat.sound(sp, SoundEvents.WARDEN_SONIC_CHARGE, 4f, 0.5f));
        Scheduler.after(BLAST, () -> {
            Vec3 g = new Vec3(origin.x, ground(lvl, origin.x, origin.z), origin.z);
            PathsNetwork.fx(lvl, FxType.DESCENT_BLAST, sp.getId(), -1, g, 30, (float) (topY - g.y));
            Combat.sound(lvl, g, SoundEvents.WARDEN_SONIC_BOOM, 10f, 0.4f);
            Combat.sound(lvl, g, SoundEvents.GENERIC_EXPLODE, 10f, 0.4f);
            Combat.sound(lvl, g, SoundEvents.LIGHTNING_BOLT_THUNDER, 10f, 0.5f);
            for (LivingEntity e : foes(sp, g, 32)) {
                double d = e.position().distanceTo(g);
                Combat.damage(sp, e, (float) Math.max(12, 60 * (1 - d / 34)), true);
                Vec3 away = new Vec3(e.getX() - g.x, 0, e.getZ() - g.z);
                away = away.lengthSqr() < 1.0e-3 ? new Vec3(1, 0, 0) : away.normalize();
                Combat.launch(e, away.scale(3.2 * (1 - d / 36)).add(0, 0.6, 0));
            }
            for (Entity e : lvl.getEntities(sp, new AABB(g, g).inflate(30), x -> !(x instanceof LivingEntity))) {
                Vec3 away = e.position().subtract(g).normalize();
                e.setDeltaMovement(away.scale(2.4).add(0, 0.6, 0));
                e.hurtMarked = true;
            }
            if (griefing(lvl)) for (int s = 0; s < 8; s++) {
                final int slice = s;
                Scheduler.after(s + 1, () -> crater(lvl, g, 19, 9, slice, 8));
            }
        });
        for (int t = BLAST + 6; t < 200; t++) {
            Scheduler.after(t, () -> {
                if (sp.onGround()) return;
                sp.setDeltaMovement(0, -0.32, 0);
                sp.hurtMarked = true;
                sp.fallDistance = 0;
            });
        }
        sp.displayClientMessage(Component.literal("§fAlmighty Push — Heavenly Descent"), true);
        return true;
    }

    /** Carve one height-slice of a bowl crater; the rim gets a scorched lip of coarse dirt. */
    static void crater(ServerLevel lvl, Vec3 c, double r, double depth, int slice, int slices) {
        int cx = Mth.floor(c.x), cy = Mth.floor(c.y), cz = Mth.floor(c.z);
        int top = 14, bottom = -(int) depth;
        int span = top - bottom + 1;
        int from = bottom + span * slice / slices, to = bottom + span * (slice + 1) / slices - 1;
        BlockPos.MutableBlockPos mp = new BlockPos.MutableBlockPos();
        int R = (int) r + 1;
        for (int dy = from; dy <= to; dy++) for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
            double h = Math.sqrt(dx * dx + dz * dz);
            double jitter = (RNG.nextDouble() - 0.5) * 1.2;
            if (h > r + jitter) continue;
            double floor = -depth * Math.sqrt(Math.max(0, 1 - (h / r) * (h / r)));
            mp.set(cx + dx, cy + dy, cz + dz);
            if (dy > floor) {
                BlockState s = lvl.getBlockState(mp);
                if (breakable(lvl, mp, s)) lvl.setBlock(mp, Blocks.AIR.defaultBlockState(), 2);
            } else if (dy > floor - 1 && RNG.nextFloat() < 0.35) {
                BlockState s = lvl.getBlockState(mp);
                if (breakable(lvl, mp, s) && s.isSolidRender(lvl, mp)) lvl.setBlock(mp, Blocks.COARSE_DIRT.defaultBlockState(), 2);
            }
        }
    }

    // ══════════════════════════ ASURA PATH ══════════════════════════

    static boolean barrage(ServerPlayer sp, PathsState st) {
        List<LivingEntity> targets = new ArrayList<>(foes(sp, sp.position(), 30).stream()
                .filter(e -> e instanceof Enemy || e instanceof Player || (e instanceof Mob m && m.getTarget() == sp) || sp.getLastHurtMob() == e).toList());
        LivingEntity aimed = aimed(sp, 40, 0.9);
        if (aimed != null) {
            targets.remove(aimed);
            targets.add(0, aimed);
        }
        Combat.sound(sp, SoundEvents.PISTON_EXTEND, 1.5f, 0.6f);
        PathsNetwork.fx(sp, FxType.MISSILE_LAUNCH, sp, null, sp.position(), 1, 0);
        for (int i = 0; i < 10; i++) {
            final int k = i;
            Scheduler.after(4 + i * 2, () -> {
                if (!sp.isAlive()) return;
                int tid = targets.isEmpty() ? -1 : targets.get(k % targets.size()).getId();
                MissileEntity m = new MissileEntity(sp.level(), sp, tid);
                float yaw = sp.getYRot() * Mth.DEG_TO_RAD;
                Vec3 right = new Vec3(-Mth.cos(yaw), 0, -Mth.sin(yaw));
                double side = (k % 2 == 0 ? 1 : -1) * (0.5 + 0.1 * (k / 2));
                Vec3 at = sp.position().add(0, 1.6, 0).add(right.scale(side));
                m.setPos(at.x, at.y, at.z);
                Vec3 v = sp.getLookAngle().scale(0.6).add(right.scale(side * 0.5)).add(0, 0.8, 0);
                m.shoot(v.x, v.y, v.z, 1.0f, 4f);
                sp.level().addFreshEntity(m);
                PathsNetwork.fx(sp, FxType.MISSILE_LAUNCH, sp, null, at, 0, (float) side);
                Combat.sound(sp, SoundEvents.FIREWORK_ROCKET_LAUNCH, 1.2f, 0.7f + k * 0.03f);
            });
        }
        return true;
    }

    static boolean cannon(ServerPlayer sp, PathsState st) {
        ServerLevel lvl = sp.serverLevel();
        Vec3 dir = sp.getLookAngle();
        Vec3 start = sp.getEyePosition().add(dir.scale(1.2)).subtract(0, 0.25, 0);
        double maxLen = 48;
        BlockHitResult hit = lvl.clip(new ClipContext(start, start.add(dir.scale(maxLen)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double len = hit.getType() == HitResult.Type.MISS ? maxLen : hit.getLocation().distanceTo(start);
        Vec3 end = start.add(dir.scale(len));
        lock(sp, st, 40);
        PathsNetwork.cutscene(sp, CutType.CANNON, sp.getId(), cat(enc(end), Mth.floor(sp.getYRot() * 10)));
        PathsNetwork.fx(sp, FxType.CANNON_CHARGE, sp, null, end, 0, 0);
        Combat.sound(sp, SoundEvents.BEACON_POWER_SELECT, 2f, 0.6f);
        Combat.sound(sp, SoundEvents.WARDEN_SONIC_CHARGE, 2f, 1.2f);
        Scheduler.after(18, () -> {
            PathsNetwork.fx(lvl, FxType.CANNON_BEAM, sp.getId(), -1, end, 0, 0);
            Combat.sound(sp, SoundEvents.WARDEN_SONIC_BOOM, 4f, 0.8f);
            Combat.sound(lvl, end, SoundEvents.GENERIC_EXPLODE, 5f, 0.6f);
            Vec3 s = sp.getEyePosition().add(dir.scale(1.2)).subtract(0, 0.25, 0);
            Vec3 e2 = s.add(dir.scale(len));
            AABB box = new AABB(s, e2).inflate(2.2);
            for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, box, x -> foe(sp, x))) {
                Vec3 c = e.getBoundingBox().getCenter();
                double along = Mth.clamp(c.subtract(s).dot(dir), 0, len);
                if (s.add(dir.scale(along)).distanceTo(c) > 2.0 + e.getBbWidth() / 2) continue;
                Combat.damage(sp, e, 30f, true);
                e.setSecondsOnFire(5);
                Combat.launch(e, dir.scale(2.0).add(0, 0.5, 0));
            }
            for (LivingEntity e : foes(sp, e2, 4.5)) {
                Combat.damage(sp, e, 14f, true);
                Combat.launch(e, e.position().subtract(e2).normalize().scale(1.4).add(0, 0.6, 0));
            }
            if (griefing(lvl)) {
                for (double d = 2; d <= len + 1; d += 1.0) bore(lvl, s.add(dir.scale(d)), 1.4);
                bore(lvl, e2, 3.2);
            }
        });
        return true;
    }

    static void bore(ServerLevel lvl, Vec3 c, double r) {
        int R = (int) Math.ceil(r);
        BlockPos base = BlockPos.containing(c);
        for (BlockPos p : BlockPos.betweenClosed(base.offset(-R, -R, -R), base.offset(R, R, R))) {
            if (Vec3.atCenterOf(p).distanceTo(c) > r + (RNG.nextDouble() - 0.5) * 0.6) continue;
            BlockState s = lvl.getBlockState(p);
            if (breakable(lvl, p, s)) lvl.setBlock(p, Blocks.AIR.defaultBlockState(), 2);
        }
    }

    // ══════════════════════════ HUMAN PATH ══════════════════════════

    static boolean soul(ServerPlayer sp, PathsState st) {
        LivingEntity t = aimed(sp, 7, 0.75);
        if (t == null) {
            sp.displayClientMessage(Component.literal("§7Get closer — the Human Path needs a hand on the target."), true);
            return false;
        }
        lock(sp, st, 56);
        Combat.stun(t, 56);
        PathsNetwork.cutscene(sp, CutType.SOUL, sp.getId(), t.getId());
        PathsNetwork.fx(sp, FxType.SOUL_GRAB, sp, t, t.getBoundingBox().getCenter(), 0, 0);
        Combat.sound(t, SoundEvents.SOUL_ESCAPE, 3f, 0.5f);
        Combat.sound(t, SoundEvents.WARDEN_HEARTBEAT, 2f, 0.6f);
        Scheduler.after(6, () -> {
            if (!t.isAlive()) return;
            Vec3 hold = sp.position().add(Combat.flatLook(sp).scale(1.6));
            t.teleportTo(hold.x, t.getY(), hold.z);
        });
        Scheduler.after(44, () -> {
            if (!t.isAlive()) return;
            boolean boss = t.getType().is(Tags.EntityTypes.BOSSES);
            boolean oneShot = !boss && t.getMaxHealth() <= 60 && !(t instanceof Player);
            PathsNetwork.fx(sp, FxType.SOUL_TORN, sp, t, t.getBoundingBox().getCenter(), oneShot ? 1 : 0, 0);
            Combat.sound(t, SoundEvents.SOUL_ESCAPE, 4f, 0.3f);
            Combat.sound(t, SoundEvents.WITHER_HURT, 1.5f, 0.6f);
            if (oneShot) {
                Combat.damage(sp, t, t.getHealth() + t.getAbsorptionAmount() + 1000f, true);
            } else {
                Combat.damage(sp, t, 30f, true);
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 2));
                t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 3));
            }
            sp.heal(6f);
        });
        return true;
    }

    // ══════════════════════════ ANIMAL PATH ══════════════════════════

    static EntityType<? extends BeastEntity> beastType(int kind) {
        return switch (kind) {
            case 0 -> ModRegistry.RHINO.get();
            case 1 -> ModRegistry.CENTIPEDE.get();
            case 2 -> ModRegistry.HOUND.get();
            default -> ModRegistry.BIRD.get();
        };
    }

    static boolean summon(ServerPlayer sp, PathsState st, int kind) {
        ServerLevel lvl = sp.serverLevel();
        List<BeastEntity> mine = new ArrayList<>(lvl.getEntitiesOfClass(BeastEntity.class, sp.getBoundingBox().inflate(96), b -> b.ownerId() == sp.getId()));
        mine.sort(Comparator.comparingInt(b -> -b.tickCount));
        while (mine.size() >= 4) mine.remove(0).vanish();
        Vec3 fwd = Combat.flatLook(sp);
        Vec3 spot = sp.position().add(fwd.scale(kind == 0 ? 5 : 4));
        spot = new Vec3(spot.x, Math.min(ground(lvl, spot.x, spot.z), sp.getY() + 3), spot.z);
        if (spot.y < sp.getY() - 6) spot = new Vec3(spot.x, sp.getY(), spot.z);
        final Vec3 at = spot;
        float size = switch (kind) { case 0 -> 3.4f; case 1 -> 2.6f; case 2 -> 2.4f; default -> 4.0f; };
        lock(sp, st, 26);
        PathsNetwork.cutscene(sp, CutType.SUMMON, sp.getId(), cat(enc(at), kind));
        PathsNetwork.fx(lvl, FxType.SUMMON_SEAL, sp.getId(), -1, at, kind, size);
        Combat.sound(sp, SoundEvents.EVOKER_PREPARE_SUMMON, 1.6f, 0.6f);
        Scheduler.after(12, () -> {
            BeastEntity b = beastType(kind).create(lvl);
            if (b == null) return;
            b.moveTo(at.x, at.y + (kind == 3 ? 2 : 0), at.z, sp.getYRot(), 0);
            b.bind(sp, kind == 3 ? 20 * 300 : 20 * 90);
            lvl.addFreshEntity(b);
            Combat.sound(lvl, at, SoundEvents.GENERIC_EXPLODE, 1.5f, 1.2f);
            Combat.sound(lvl, at, switch (kind) {
                case 0 -> SoundEvents.RAVAGER_ROAR;
                case 1 -> SoundEvents.SPIDER_AMBIENT;
                case 2 -> SoundEvents.WOLF_HOWL;
                default -> SoundEvents.PHANTOM_AMBIENT;
            }, 3f, 0.5f);
        });
        return true;
    }

    // ══════════════════════════ PRETA PATH ══════════════════════════

    static boolean preta(ServerPlayer sp, PathsState st) {
        long now = sp.level().getGameTime();
        st.pretaUntil = now + 90;
        PathsNetwork.fx(sp, FxType.PRETA_ON, sp, null, sp.position(), 90, 0);
        Combat.sound(sp, SoundEvents.BEACON_ACTIVATE, 1.5f, 1.5f);
        Combat.sound(sp, SoundEvents.CONDUIT_ACTIVATE, 1.5f, 0.8f);
        // the seal also drinks from whoever is grappling with you
        for (LivingEntity e : foes(sp, sp.position(), 3.5)) {
            Combat.damage(sp, e, 6f, true);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
            sp.heal(3f);
            PathsNetwork.fx(sp, FxType.PRETA_DRINK, sp, e, e.getBoundingBox().getCenter(), 0, 0);
        }
        return true;
    }

    /** Called from the attack event: returns true if the Preta seal swallowed the hit. */
    public static boolean pretaAbsorb(ServerPlayer sp, net.minecraft.world.damagesource.DamageSource src, float amount) {
        PathsState st = PathsState.peek(sp);
        if (st == null || !st.preta(sp.level().getGameTime())) return false;
        Entity direct = src.getDirectEntity(), cause = src.getEntity();
        boolean melee = direct != null && direct == cause && direct instanceof LivingEntity;
        if (melee || src.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) return false;
        if (src.is(net.minecraft.tags.DamageTypeTags.IS_FALL)) return false;
        sp.heal(amount * 0.5f);
        PathsNetwork.fx(sp, FxType.PRETA_DRINK, sp, null, direct != null ? direct.position() : sp.getEyePosition(), 1, 0);
        Combat.sound(sp, SoundEvents.BEACON_POWER_SELECT, 1f, 1.8f);
        return true;
    }

    // ══════════════════════════ NARAKA PATH ══════════════════════════

    static boolean kingOfHell(ServerPlayer sp, PathsState st) {
        Vec3 fwd = Combat.flatLook(sp);
        Vec3 at = sp.position().add(fwd.scale(3.2));
        at = new Vec3(at.x, sp.getY(), at.z);
        final Vec3 king = at;
        lock(sp, st, 90);
        PathsNetwork.cutscene(sp, CutType.KING, sp.getId(), cat(enc(king), Mth.floor(sp.getYRot() * 10)));
        PathsNetwork.fx(sp, FxType.KING_RISE, sp, null, king, sp.getYRot() + 180f, 0);
        Combat.sound(sp, SoundEvents.WITHER_SPAWN, 1.2f, 0.5f);
        Combat.sound(sp, SoundEvents.RESPAWN_ANCHOR_CHARGE, 2f, 0.4f);
        Scheduler.after(22, () -> Combat.sound(sp, SoundEvents.RAVAGER_ROAR, 2f, 0.3f));
        Scheduler.after(48, () -> {
            sp.setHealth(sp.getMaxHealth());
            for (MobEffectInstance m : new ArrayList<>(sp.getActiveEffects()))
                if (m.getEffect().getCategory() == MobEffectCategory.HARMFUL) sp.removeEffect(m.getEffect());
            sp.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 1200, 2));
            sp.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
            sp.clearFire();
            Combat.sound(sp, SoundEvents.TOTEM_USE, 1f, 0.6f);
            // the King judges whoever stands closest — the tongue tears out a lie
            List<LivingEntity> near = foes(sp, king, 9);
            if (!near.isEmpty()) {
                LivingEntity j = near.get(0);
                Combat.damage(sp, j, 20f, true);
                Combat.stun(j, 30);
                PathsNetwork.fx(sp, FxType.SOUL_TORN, sp, j, j.getBoundingBox().getCenter(), 0, 1);
            }
        });
        return true;
    }

    // ══════════════════════════ OUTER PATH ══════════════════════════

    static boolean samsara(ServerPlayer sp, PathsState st) {
        ServerLevel lvl = sp.serverLevel();
        lock(sp, st, 220);
        PathsNetwork.cutscene(sp, CutType.SAMSARA, sp.getId(), enc(sp.position()));
        PathsNetwork.fx(lvl, FxType.SAMSARA, sp.getId(), -1, sp.position(), 96, 0);
        Combat.sound(sp, SoundEvents.WITHER_SPAWN, 1.5f, 0.3f);
        Combat.sound(sp, SoundEvents.BEACON_ACTIVATE, 3f, 0.4f);
        Scheduler.after(60, () -> Combat.sound(sp, SoundEvents.BEACON_AMBIENT, 4f, 0.5f));
        Scheduler.after(170, () -> {
            for (ServerPlayer p : lvl.players()) {
                if (p == sp || p.distanceTo(sp) > 96 || !p.isAlive()) continue;
                p.setHealth(p.getMaxHealth());
                for (MobEffectInstance m : new ArrayList<>(p.getActiveEffects()))
                    if (m.getEffect().getCategory() == MobEffectCategory.HARMFUL) p.removeEffect(m.getEffect());
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 600, 2));
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 1));
                p.getFoodData().eat(20, 1f);
                p.clearFire();
            }
            // tamed animals and villagers nearby are healed too
            for (LivingEntity e : lvl.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(96),
                    e -> !(e instanceof Enemy) && !(e instanceof Player) && e.isAlive())) {
                e.setHealth(e.getMaxHealth());
            }
            sp.setHealth(Math.min(sp.getHealth(), 2f));
            sp.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 1200, 2));
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 1));
            sp.addEffect(new MobEffectInstance(MobEffects.HUNGER, 600, 1));
            Combat.sound(sp, SoundEvents.TOTEM_USE, 2f, 0.5f);
        });
        sp.displayClientMessage(Component.literal("§aSamsara of Heavenly Life"), true);
        return true;
    }

    // ══════════════════════════ TRANSFORMATION / UTILITY ══════════════════════════

    static boolean ascend(ServerPlayer sp, PathsState st) {
        long now = sp.level().getGameTime();
        st.ascendUntil = now + 1200;
        st.ascended = true;
        lock(sp, st, 40);
        sp.getAbilities().mayfly = true;
        sp.onUpdateAbilities();
        sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 1200, 0, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1, false, false));
        sp.addEffect(new MobEffectInstance(MobEffects.JUMP, 1200, 1, false, false));
        PathsNetwork.cutscene(sp, CutType.ASCEND, sp.getId(), enc(sp.position()));
        PathsNetwork.fx(sp, FxType.ASCEND, sp, null, sp.position(), 1200, 0);
        Combat.sound(sp, SoundEvents.BEACON_ACTIVATE, 2f, 0.7f);
        Combat.sound(sp, SoundEvents.ENDER_DRAGON_FLAP, 2f, 0.6f);
        Scheduler.after(30, () -> Combat.launch(sp, new Vec3(0, 0.9, 0)));
        sp.displayClientMessage(Component.literal("§eDeva Ascension — flight, halved cooldowns, wider Push (60 s)"), true);
        return true;
    }

    static boolean sight(ServerPlayer sp, PathsState st) {
        int n = 0;
        for (LivingEntity e : foes(sp, sp.position(), 48)) {
            if (!(e instanceof Enemy) && !(e instanceof Player) && !(e instanceof Mob m && m.getTarget() == sp)) continue;
            e.addEffect(new MobEffectInstance(MobEffects.GLOWING, 240, 0, false, false));
            n++;
        }
        sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false));
        PathsNetwork.fx(sp, FxType.SIGHT, sp, null, sp.getEyePosition(), 48, n);
        Combat.sound(sp, SoundEvents.AMETHYST_BLOCK_RESONATE, 2f, 0.5f);
        sp.displayClientMessage(Component.literal("§dShared Sight — " + n + " hostile" + (n == 1 ? "" : "s") + " marked"), true);
        return true;
    }

    private PathsLogic() {}
}
