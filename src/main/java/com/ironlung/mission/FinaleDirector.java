package com.ironlung.mission;

import com.ironlung.IronLungMod;
import com.ironlung.block.ControlType;
import com.ironlung.config.IronLungConfig;
import com.ironlung.network.ModNetwork;
import com.ironlung.network.Packets;
import com.ironlung.registry.ModSounds;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * The end of the ascent. It tears through the back of the boat, wrecks the cabin, and takes the crew
 * one at a time with its tongue. The creature, tongue and remains are built from block display entities
 * (summoned with vanilla commands) so every player sees the same thing in the world.
 */
public final class FinaleDirector {
    private FinaleDirector() {}

    public static final int BREACH = 650;
    private static final int VICTIMS_FROM = 718;
    private static final int GRAB = 9, PULL_END = 40, RETRACT_END = 50, GAP = 14;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.45f, 0.0f, 0.0f), 2.2f);
    private static final DustParticleOptions MIST = new DustParticleOptions(new Vector3f(0.3f, 0.0f, 0.0f), 3.5f);

    /** Per-dive finale state; lives only while the server is running. */
    private static final class State {
        final List<UUID> victims = new ArrayList<>();
        int victim = -1;
        int victimStart;
        Vec3 grabFrom;
        int finishAt = -1;
        int consoles;
        final List<ItemEntity> items = new ArrayList<>();
        int gore;
    }

    private static final java.util.Map<UUID, State> STATES = new java.util.HashMap<>();

    private static State state(Mission m) {
        return STATES.computeIfAbsent(m.id, k -> new State());
    }

    // ------------------------------------------------------------------ geometry

    /** Where the monster's head sits: just outside the back wall, facing into the cabin (-z). */
    private static Vec3 origin(Mission m) {
        BlockPos b = m.base();
        return new Vec3(b.getX() + 1.5, b.getY() + 1.15, b.getZ() + 6.3);
    }

    private static Vec3 mouth(Mission m) {
        return origin(m).add(0, -0.1, -1.2);
    }

    private static String tag(Mission m) {
        return "ilf" + m.slot;
    }

    // ------------------------------------------------------------------ commands

    private static void cmd(ServerLevel lvl, String c) {
        MinecraftServer server = lvl.getServer();
        CommandSourceStack src = server.createCommandSourceStack().withLevel(lvl).withSuppressedOutput().withPermission(4);
        int result = server.getCommands().performPrefixedCommand(src, c);
        if (result <= 0 && !c.startsWith("kill")) IronLungMod.LOGGER.warn("Finale command did nothing: {}", c);
    }

    private static String f(double v) {
        return String.format(Locale.ROOT, "%.4ff", v);
    }

    /** Quaternion (x,y,z,w) rotating +Z to point along (dx,dy,dz), with an extra roll about that axis. */
    private static double[] lookQuat(double dx, double dy, double dz, double roll) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9;
        double yaw = Math.atan2(dx, dz), pitch = -Math.asin(Math.max(-1, Math.min(1, dy / len)));
        double cy = Math.cos(yaw / 2), sy = Math.sin(yaw / 2), cp = Math.cos(pitch / 2), sp = Math.sin(pitch / 2);
        double cr = Math.cos(roll / 2), sr = Math.sin(roll / 2);
        double[] qYaw = {0, sy, 0, cy}, qPitch = {sp, 0, 0, cp}, qRoll = {0, 0, sr, cr};
        return mul(mul(qYaw, qPitch), qRoll);
    }

    private static double[] mul(double[] a, double[] b) {
        return new double[]{
                a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
                a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
                a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
                a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2]};
    }

    private static double[] rotate(double[] q, double x, double y, double z) {
        double[] p = mul(mul(q, new double[]{x, y, z, 0}), new double[]{-q[0], -q[1], -q[2], q[3]});
        return new double[]{p[0], p[1], p[2]};
    }

    /**
     * NBT for a box of size (sx,sy,sz) whose centre sits at (cx,cy,cz) relative to the entity, rotated by q.
     */
    private static String transform(double cx, double cy, double cz, double sx, double sy, double sz, double[] q) {
        double[] off = rotate(q, -sx / 2, -sy / 2, -sz / 2);
        return "{left_rotation:[" + f(q[0]) + "," + f(q[1]) + "," + f(q[2]) + "," + f(q[3]) + "],right_rotation:[0f,0f,0f,1f],"
                + "translation:[" + f(cx + off[0]) + "," + f(cy + off[1]) + "," + f(cz + off[2]) + "],"
                + "scale:[" + f(sx) + "," + f(sy) + "," + f(sz) + "]}";
    }

    /** A box that starts at the entity origin and extends `len` along direction (dx,dy,dz). */
    private static String beam(double dx, double dy, double dz, double len, double w, double h, double roll) {
        double[] q = lookQuat(dx, dy, dz, roll);
        double[] off = rotate(q, -w / 2, -h / 2, 0);
        return "{left_rotation:[" + f(q[0]) + "," + f(q[1]) + "," + f(q[2]) + "," + f(q[3]) + "],right_rotation:[0f,0f,0f,1f],"
                + "translation:[" + f(off[0]) + "," + f(off[1]) + "," + f(off[2]) + "],"
                + "scale:[" + f(w) + "," + f(h) + "," + f(Math.max(0.001, len)) + "]}";
    }

    private static void summon(ServerLevel lvl, Vec3 at, String block, String transformation, String tags, int bright) {
        cmd(lvl, "summon minecraft:block_display " + f(at.x).replace("f", "") + " " + f(at.y).replace("f", "") + " " + f(at.z).replace("f", "")
                + " {Tags:[" + tags + "],block_state:{Name:\"minecraft:" + block + "\"},brightness:{sky:0,block:" + bright + "},"
                + "transformation:" + transformation + "}");
    }

    private static void animate(ServerLevel lvl, String selectorTag, String transformation, int ticks) {
        cmd(lvl, "data merge entity @e[type=minecraft:block_display,tag=" + selectorTag + ",limit=1] {transformation:" + transformation
                + ",start_interpolation:0,interpolation_duration:" + ticks + "}");
    }

    // ------------------------------------------------------------------ the creature

    /** One box of the creature in its own frame: x right, y up, z toward the cabin. */
    private record Part(String block, double x, double y, double z, double sx, double sy, double sz, double pitch, double roll) {}

    private static List<Part> creature() {
        List<Part> p = new ArrayList<>();
        // the head, mostly hidden behind the hull
        p.add(new Part("packed_mud", 0, 0.3, -2.6, 4.4, 4.0, 4.4, 0, 0));
        p.add(new Part("gray_terracotta", 0.4, 1.9, -1.4, 3.6, 1.2, 2.4, 0, 0.05));
        // torn lips framing the opening
        p.add(new Part("red_terracotta", 0, 1.3, 0.55, 3.0, 0.5, 0.9, 0.12, 0));
        p.add(new Part("red_terracotta", 0, -1.35, 0.55, 3.0, 0.45, 0.9, -0.1, 0));
        p.add(new Part("red_terracotta", -1.4, 0, 0.5, 0.5, 2.5, 0.9, 0, 0.06));
        p.add(new Part("red_terracotta", 1.4, -0.05, 0.5, 0.5, 2.5, 0.9, 0, -0.05));
        p.add(new Part("crimson_nylium", -0.5, 1.1, 0.75, 0.9, 0.25, 0.5, 0.3, 0.2));
        p.add(new Part("crimson_nylium", 0.9, -1.15, 0.8, 0.8, 0.25, 0.5, -0.3, -0.25));
        // inside: raw red walls narrowing to a black throat
        p.add(new Part("nether_wart_block", 0, 0, -0.7, 2.4, 2.3, 0.3, 0, 0));
        p.add(new Part("nether_wart_block", 0, 0.95, -0.1, 2.4, 0.35, 1.2, 0, 0));
        p.add(new Part("nether_wart_block", 0, -0.95, -0.1, 2.4, 0.35, 1.2, 0, 0));
        p.add(new Part("nether_wart_block", -1.05, 0, -0.1, 0.35, 2.2, 1.2, 0, 0));
        p.add(new Part("nether_wart_block", 1.05, 0, -0.1, 0.35, 2.2, 1.2, 0, 0));
        p.add(new Part("black_concrete", 0.05, -0.1, -0.5, 0.95, 1.3, 0.15, 0, 0));
        p.add(new Part("crimson_hyphae", 0, -0.75, 0.2, 1.4, 0.2, 0.8, 0.05, 0));
        // needle teeth: dense and drooping on top, sparse below, angled into the cabin
        for (int i = 0; i < 11; i++) {
            double x = -1.15 + i * 0.23, len = 0.45 + 0.35 * Math.abs(Math.sin(i * 2.7));
            p.add(new Part(i % 3 == 0 ? "bone_block" : "white_terracotta", x, 1.0 - len / 2, 0.85, 0.07, len, 0.07, 0.35 + 0.1 * Math.sin(i), 0.1 * Math.sin(i * 1.3)));
        }
        for (int i = 0; i < 7; i++) {
            double x = -1.0 + i * 0.33, len = 0.35 + 0.3 * Math.abs(Math.sin(i * 1.9));
            p.add(new Part("bone_block", x, -1.05 + len / 2, 0.85, 0.07, len, 0.07, -0.3, 0.12 * Math.sin(i * 2.1)));
        }
        for (int i = 0; i < 4; i++) {
            double y = -0.7 + i * 0.45;
            p.add(new Part("white_terracotta", -1.12, y, 0.8, 0.45, 0.06, 0.06, 0, 0.2));
            p.add(new Part("white_terracotta", 1.12, y + 0.1, 0.8, 0.45, 0.06, 0.06, 0, -0.2));
        }
        // blood and slime strung between the jaws
        double[] sx = {-0.7, -0.2, 0.35, 0.8};
        for (int i = 0; i < sx.length; i++) p.add(new Part("redstone_block", sx[i], -0.05 * i, 0.7 - 0.1 * i, 0.035, 1.9 - 0.2 * i, 0.035, 0.08, 0));
        // sunken eyes in the brow: black wells with a pale, clouded centre
        double[][] eyes = {{-0.85, 1.32, 0.86, 0.36}, {0.75, 1.36, 0.86, 0.32}, {1.3, 1.2, 0.83, 0.2}, {-1.3, 1.15, 0.83, 0.18}};
        for (double[] e : eyes) {
            p.add(new Part("black_concrete", e[0], e[1], e[2], e[3], e[3], 0.12, 0, 0));
            p.add(new Part("light_gray_concrete", e[0] + 0.02, e[1] - 0.01, e[2] + 0.02, e[3] * 0.42, e[3] * 0.42, 0.12, 0, 0));
        }
        // tendrils reaching into the cabin
        double[][] tend = {{-1.5, 0.9, 0.9, -0.5, 0.4}, {1.5, 0.6, 0.9, 0.6, 0.3}, {-1.45, -1.0, 0.9, -0.4, -0.5}, {1.4, -1.2, 0.9, 0.5, -0.4}};
        for (double[] t : tend) p.add(new Part("crimson_stem", t[0] + t[3] * 0.6, t[1] + t[4] * 0.6, t[2] + 0.6, 0.08, 0.08, 1.6, t[4], t[3]));
        return p;
    }

    private static void spawnCreature(Mission m, ServerLevel lvl) {
        Vec3 o = origin(m);
        List<Part> parts = creature();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            double[] q = mul(lookQuat(0, 0, -1, p.roll()), new double[]{Math.sin(-p.pitch() / 2), 0, 0, Math.cos(-p.pitch() / 2)});
            // local z points into the cabin, which is world -z
            String far = transform(-p.x(), p.y(), -(p.z() - 7.0), p.sx(), p.sy(), p.sz(), q);
            summon(lvl, o, p.block(), far, "\"" + tag(m) + "\",\"" + tag(m) + "c" + i + "\"", p.block().startsWith("black") ? 3 : 13);
        }
    }

    private static void lunge(Mission m, ServerLevel lvl, double extra, int ticks) {
        List<Part> parts = creature();
        for (int i = 0; i < parts.size(); i++) {
            Part p = parts.get(i);
            double[] q = mul(lookQuat(0, 0, -1, p.roll()), new double[]{Math.sin(-p.pitch() / 2), 0, 0, Math.cos(-p.pitch() / 2)});
            animate(lvl, tag(m) + "c" + i, transform(-p.x(), p.y(), -(p.z() + extra), p.sx(), p.sy(), p.sz(), q), ticks);
        }
    }

    // ------------------------------------------------------------------ the sequence

    public static void tick(Mission m, ServerLevel lvl, RandomSource r, int t) {
        State s = state(m);
        BlockPos b = m.base();
        if (t == BREACH) breach(m, lvl, r);
        if (t == BREACH + 2) {
            lunge(m, lvl, 0, 6);
            lvl.playSound(null, b.getX() + 1.5, b.getY() + 1.2, b.getZ() + 4.5, ModSounds.JUMPSCARE.get(), SoundSource.HOSTILE, 3.0f, 0.85f);
            lvl.playSound(null, b.getX() + 1.5, b.getY() + 1.2, b.getZ() + 5.5, ModSounds.LEVIATHAN.get(), SoundSource.HOSTILE, 2.5f, 1.1f);
        }
        // it breathes in and out of the hole while it wrecks the cabin
        if (t > BREACH + 10 && t < VICTIMS_FROM + 400 && (t - BREACH) % 24 == 0) lunge(m, lvl, ((t - BREACH) / 24) % 2 == 0 ? 0.25 : -0.1, 12);
        if (t >= BREACH + 10 && t <= BREACH + 60 && (t - BREACH - 10) % 7 == 0) wreckNext(m, lvl, r, s);
        if (t % 5 == 0 && t > BREACH) {
            // emergency light stutter from the torn wiring
            BlockPos light = b.offset(1, 2, 2);
            int lv = r.nextFloat() < 0.3f ? 3 : 8 + r.nextInt(6);
            lvl.setBlock(light, lv == 0 ? Blocks.AIR.defaultBlockState() : Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, lv), 3);
            if (r.nextFloat() < 0.3f) lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.getX() + 0.3 + r.nextDouble() * 2.4, b.getY() + 2.8, b.getZ() + 0.3 + r.nextDouble() * 4.4, 8, 0.1, 0.1, 0.1, 0.2);
        }

        if (!IronLungConfig.FINALE_KILLS.get()) {
            if (t == VICTIMS_FROM) {
                lvl.playSound(null, b.getX() + 1.5, b.getY() + 1.2, b.getZ() + 5.5, ModSounds.LEVIATHAN.get(), SoundSource.HOSTILE, 3.0f, 0.7f);
                lunge(m, lvl, -8, 20);
                s.finishAt = t + 60;
            }
        } else {
            if (t == VICTIMS_FROM) {
                for (ServerPlayer p : MissionManager.aboard(m)) if (p.isAlive() && !p.isSpectator()) s.victims.add(p.getUUID());
                java.util.Collections.shuffle(s.victims, new java.util.Random(r.nextLong()));
                s.victim = 0;
                s.victimStart = t;
                if (s.victims.isEmpty()) s.finishAt = t + 40;
            }
            if (s.victim >= 0 && s.victim < s.victims.size() && t >= s.victimStart) takeOne(m, lvl, r, s, t);
        }
        if (s.finishAt > 0 && t >= s.finishAt) {
            cleanup(m, lvl);
            MissionManager.finish(lvl.getServer(), m, IronLungConfig.FINALE_KILLS.get() ? Packets.MissionEnd.TAKEN : Packets.MissionEnd.RECOVERED, true);
        }
    }

    private static void breach(Mission m, ServerLevel lvl, RandomSource r) {
        BlockPos b = m.base();
        // tear out the back of the boat: hatch, wall and outer skin
        for (int x = 0; x <= 2; x++)
            for (int y = 0; y <= 2; y++)
                for (int z = 5; z <= 6; z++) {
                    BlockPos p = b.offset(x, y, z);
                    if (!lvl.getBlockState(p).isAir()) {
                        lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, lvl.getBlockState(p)), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.2, 40, 0.4, 0.4, 0.4, 0.4);
                        lvl.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
        // shards of hull flung into the cabin
        for (int i = 0; i < 10; i++) {
            double[] q = lookQuat(r.nextDouble() - 0.5, r.nextDouble() - 0.5, -1, r.nextDouble() * 6);
            Vec3 at = new Vec3(b.getX() + 0.5 + r.nextDouble() * 2, b.getY() + 0.3 + r.nextDouble() * 2, b.getZ() + 5.1);
            String name = tag(m) + "d" + i;
            summon(lvl, at, i % 2 == 0 ? "iron_block" : "packed_mud", transform(0, 0, 0, 0.5 + r.nextDouble() * 0.4, 0.08, 0.4 + r.nextDouble() * 0.5, q),
                    "\"" + tag(m) + "\",\"" + name + "\"", 5);
            double[] q2 = lookQuat(r.nextDouble() - 0.5, r.nextDouble(), r.nextDouble() - 0.5, r.nextDouble() * 6);
            double fy = -(at.y - b.getY()) + 0.05;
            animate(lvl, name, transform(r.nextDouble() * 1.6 - 0.8, fy, -1.5 - r.nextDouble() * 2.5, 0.5, 0.08, 0.5, q2), 8);
        }
        lvl.sendParticles(MIST, b.getX() + 1.5, b.getY() + 1.2, b.getZ() + 4.6, 50, 1.0, 0.9, 0.4, 0.15);
        lvl.sendParticles(ParticleTypes.EXPLOSION, b.getX() + 1.5, b.getY() + 1.2, b.getZ() + 4.8, 3, 0.6, 0.6, 0.3, 0);
        lvl.playSound(null, b.getX() + 1.5, b.getY() + 1.2, b.getZ() + 5.0, ModSounds.HATCH_BREACH.get(), SoundSource.HOSTILE, 3.0f, 0.75f);
        lvl.playSound(null, b.getX() + 1.5, b.getY() + 1.2, b.getZ() + 5.0, ModSounds.IMPLOSION.get(), SoundSource.HOSTILE, 2.0f, 1.2f);
        // lamps shatter
        for (BlockPos l : SubInterior.LAMPS) {
            BlockPos p = b.offset(l);
            lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 30, 0.2, 0.2, 0.2, 0.3);
            lvl.playSound(null, p, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.7f, 1.2f);
        }
        spawnCreature(m, lvl);
        // everyone is thrown around to face the hole
        for (ServerPlayer p : MissionManager.aboard(m)) {
            if (!p.isAlive()) continue;
            p.teleportTo(lvl, p.getX(), p.getY(), p.getZ(), 0f, 5f);
        }
        MissionManager.broadcast(m, new Packets.HorrorEvent(HorrorType.FINALE_CHAOS.ordinal(), 2.5f, r.nextLong(), 40f, 0));
    }

    private static final ControlType[] WRECK_ORDER = {ControlType.HELM, ControlType.CAMERA, ControlType.BREAKER, ControlType.PROXIMITY,
            ControlType.PRESSURE_GAUGE, ControlType.DEPTH_GAUGE, ControlType.CHART, ControlType.BALLAST};

    private static void wreckNext(Mission m, ServerLevel lvl, RandomSource r, State s) {
        if (s.consoles >= WRECK_ORDER.length) {
            // pipes burst
            BlockPos p = m.base().offset(r.nextBoolean() ? 0 : 2, 2, 1 + r.nextInt(3));
            lvl.sendParticles(MIST, p.getX() + 0.5, p.getY() + 0.3, p.getZ() + 0.5, 60, 0.1, 0.6, 0.1, 0.1);
            lvl.playSound(null, p, ModSounds.PRESSURE_HISS.get(), SoundSource.BLOCKS, 1.5f, 0.7f);
            return;
        }
        BlockPos p = m.base().offset(SubInterior.CONTROL_POS.get(WRECK_ORDER[s.consoles++]));
        lvl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 40, 0.3, 0.3, 0.3, 0.4);
        lvl.sendParticles(ParticleTypes.LARGE_SMOKE, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 12, 0.2, 0.2, 0.2, 0.02);
        lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, lvl.getBlockState(p)), p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 30, 0.3, 0.3, 0.3, 0.3);
        lvl.playSound(null, p, ModSounds.POWER_DOWN.get(), SoundSource.BLOCKS, 1.4f, 1.3f);
        lvl.playSound(null, p, ModSounds.HULL_IMPACT.get(), SoundSource.BLOCKS, 1.2f, 1.4f);
        lvl.setBlock(p, com.ironlung.registry.ModBlocks.BOLTED_PANEL.get().defaultBlockState(), 3);
        MissionManager.broadcast(m, new Packets.HorrorEvent(HorrorType.IMPACT.ordinal(), 0.5f, r.nextLong(), 0, 0));
    }

    private static void takeOne(Mission m, ServerLevel lvl, RandomSource r, State s, int t) {
        ServerPlayer p = lvl.getServer().getPlayerList().getPlayer(s.victims.get(s.victim));
        int e = t - s.victimStart;
        Vec3 mouth = mouth(m);
        String tb = tag(m) + "tb", tt = tag(m) + "tt";
        if (p == null || !p.isAlive() || !p.level().dimension().equals(IronLungMod.DIMENSION)) {
            nextVictim(m, lvl, s, t);
            return;
        }
        Vec3 target = p.position().add(0, 1.0, 0);
        if (e == 0) {
            s.grabFrom = p.position();
            summon(lvl, mouth, "pink_terracotta", beam(target.x - mouth.x, target.y - mouth.y, target.z - mouth.z, 0.01, 0.32, 0.22, 0),
                    "\"" + tag(m) + "\",\"" + tb + "\"", 8);
            summon(lvl, mouth, "red_terracotta", transform(0, 0, 0, 0.01, 0.01, 0.01, new double[]{0, 0, 0, 1}), "\"" + tag(m) + "\",\"" + tt + "\"", 8);
            lvl.playSound(null, mouth.x, mouth.y, mouth.z, ModSounds.TONGUE.get(), SoundSource.HOSTILE, 2.0f, 1.0f);
        }
        if (e <= GRAB) {
            double k = Math.min(1, (e + 1) / (double) GRAB);
            setTongue(lvl, m, mouth, target, k, 1);
        }
        if (e == GRAB) {
            ModNetwork.toPlayer(p, new Packets.HorrorEvent(HorrorType.GRABBED.ordinal(), 2.0f, r.nextLong(), (PULL_END - GRAB) / 20f, 0));
            lvl.playSound(null, p.getX(), p.getY() + 1, p.getZ(), ModSounds.GORE.get(), SoundSource.HOSTILE, 1.0f, 1.6f);
            MissionManager.tellCrew(m, net.minecraft.network.chat.Component.translatable("ironlung.msg.taken", p.getGameProfile().getName())
                    .withStyle(net.minecraft.ChatFormatting.DARK_RED), true);
        }
        if (e > GRAB && e <= PULL_END) {
            // dragged off their feet, through the air, into the mouth
            double k = (e - GRAB) / (double) (PULL_END - GRAB);
            k = k * k * (3 - 2 * k);
            Vec3 end = mouth.add(0, -0.9, 0.3);
            Vec3 from = s.grabFrom;
            double lift = Math.sin(k * Math.PI) * 0.8;
            double x = from.x + (end.x - from.x) * k, y = from.y + (end.y - from.y) * k + lift, z = from.z + (end.z - from.z) * k;
            float yaw = (float) Math.toDegrees(Math.atan2(-(mouth.x - x), mouth.z - z));
            p.teleportTo(lvl, x, y, z, yaw, 10f + (float) (Math.sin(e * 1.7) * 25));
            setTongue(lvl, m, mouth, new Vec3(x, y + 1.0, z), 1, 2);
            if (e % 3 == 0) lvl.sendParticles(BLOOD, x, y + 1.0, z, 8, 0.2, 0.3, 0.2, 0.05);
        }
        if (e == PULL_END) {
            Vec3 at = p.position().add(0, 1.0, 0);
            burst(m, lvl, r, s, at);
            if (!p.isCreative()) {
                DamageSource src = new DamageSource(lvl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(MissionManager.TAKEN));
                p.hurt(src, Float.MAX_VALUE);
            } else {
                p.teleportTo(lvl, s.grabFrom.x, s.grabFrom.y, s.grabFrom.z, p.getYRot(), p.getXRot());
            }
        }
        if (e > PULL_END && e <= RETRACT_END) {
            double k = 1 - (e - PULL_END) / (double) (RETRACT_END - PULL_END);
            setTongue(lvl, m, mouth, mouth.add(0, -0.5, -1.5), k, 2);
        }
        if (e >= RETRACT_END) {
            cmd(lvl, "kill @e[type=minecraft:block_display,tag=" + tb + "]");
            cmd(lvl, "kill @e[type=minecraft:block_display,tag=" + tt + "]");
            nextVictim(m, lvl, s, t);
        }
    }

    private static void nextVictim(Mission m, ServerLevel lvl, State s, int t) {
        s.victim++;
        s.victimStart = t + GAP;
        if (s.victim >= s.victims.size()) {
            s.finishAt = t + 50;
            lvl.playSound(null, origin(m).x, origin(m).y, origin(m).z, ModSounds.LEVIATHAN.get(), SoundSource.HOSTILE, 2.0f, 0.6f);
        }
    }

    private static void setTongue(ServerLevel lvl, Mission m, Vec3 mouth, Vec3 target, double reach, int ticks) {
        double dx = target.x - mouth.x, dy = target.y - mouth.y, dz = target.z - mouth.z;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz) * reach;
        animate(lvl, tag(m) + "tb", beam(dx, dy, dz, len, 0.32, 0.22, 0), ticks);
        double l = Math.sqrt(dx * dx + dy * dy + dz * dz) + 1e-9;
        double[] q = lookQuat(dx, dy, dz, 0.4);
        animate(lvl, tag(m) + "tt", transform(dx / l * len, dy / l * len, dz / l * len, 0.55, 0.45, 0.55, q), ticks);
    }

    /** The body bursts: blood, meat, bone and entrails thrown across the cabin, and a spreading pool on the floor. */
    private static void burst(Mission m, ServerLevel lvl, RandomSource r, State s, Vec3 at) {
        BlockPos b = m.base();
        lvl.sendParticles(BLOOD, at.x, at.y, at.z, 400, 0.6, 0.7, 0.6, 0.35);
        lvl.sendParticles(MIST, at.x, at.y, at.z, 40, 0.8, 0.6, 0.8, 0.1);
        lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, 160, 0.4, 0.5, 0.4, 0.6);
        lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.NETHER_WART_BLOCK.defaultBlockState()), at.x, at.y, at.z, 120, 0.4, 0.5, 0.4, 0.6);
        lvl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.BONE_BLOCK.defaultBlockState()), at.x, at.y, at.z, 50, 0.3, 0.4, 0.3, 0.5);
        lvl.playSound(null, at.x, at.y, at.z, ModSounds.GORE.get(), SoundSource.HOSTILE, 2.5f, 0.9f);
        lvl.playSound(null, at.x, at.y, at.z, ModSounds.IMPLOSION.get(), SoundSource.HOSTILE, 1.6f, 1.6f);
        // meat and bone
        ItemStack[] chunks = {new ItemStack(Items.BEEF), new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.BONE), new ItemStack(Items.PORKCHOP),
                new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.MUTTON), new ItemStack(Items.BONE)};
        for (int i = 0; i < 14; i++) {
            ItemStack st = chunks[i % chunks.length].copy();
            ItemEntity it = new ItemEntity(lvl, at.x, at.y, at.z, st, (r.nextDouble() - 0.5) * 0.9, 0.2 + r.nextDouble() * 0.5, (r.nextDouble() - 0.5) * 0.9);
            it.setNeverPickUp();
            it.setUnlimitedLifetime();
            it.addTag(tag(m));
            lvl.addFreshEntity(it);
            s.items.add(it);
        }
        // entrails: long wet strands flung out and draped across the cabin
        for (int i = 0; i < 9; i++) {
            String name = tag(m) + "g" + (s.gore++);
            String block = i % 3 == 0 ? "red_terracotta" : "pink_terracotta";
            double[] q0 = lookQuat(r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() - 0.5, r.nextDouble() * 6);
            summon(lvl, at, block, transform(0, 0, 0, 0.16, 0.16, 0.3, q0), "\"" + tag(m) + "\",\"" + name + "\"", 6);
            double dx = (r.nextDouble() - 0.5) * 3.0, dz = (r.nextDouble() - 0.5) * 4.0;
            double floor = b.getY() + 0.03 - at.y;
            double dy = r.nextFloat() < 0.35f ? (r.nextDouble() * 1.2) : floor; // some stick to the walls
            double[] q1 = lookQuat(r.nextDouble() - 0.5, (r.nextDouble() - 0.5) * 0.3, r.nextDouble() - 0.5, r.nextDouble() * 6);
            animate(lvl, name, transform(dx, dy, dz, 0.14 + r.nextDouble() * 0.06, 0.12, 0.9 + r.nextDouble() * 1.1, q1), 6 + r.nextInt(5));
        }
        // the pool
        String pool = tag(m) + "p" + (s.gore++);
        Vec3 floorAt = new Vec3(at.x, b.getY() + 0.01, at.z);
        summon(lvl, floorAt, "redstone_block", transform(0, 0, 0, 0.05, 0.02, 0.05, new double[]{0, 0, 0, 1}), "\"" + tag(m) + "\",\"" + pool + "\"", 4);
        animate(lvl, pool, transform(0, 0, 0, 2.2, 0.02, 2.6, new double[]{0, Math.sin(r.nextDouble()), 0, Math.cos(r.nextDouble())}), 30);
        // splatter on the walls
        for (int i = 0; i < 6; i++) {
            String sp = tag(m) + "s" + (s.gore++);
            boolean left = r.nextBoolean();
            Vec3 wall = new Vec3(b.getX() + (left ? 0.02 : 2.98), b.getY() + 0.3 + r.nextDouble() * 2.2, b.getZ() + 0.3 + r.nextDouble() * 4.4);
            double[] q = lookQuat(left ? 1 : -1, 0, 0, r.nextDouble() * 6);
            summon(lvl, wall, "redstone_block", transform(0, 0, 0, 0.6 + r.nextDouble() * 0.7, 0.4 + r.nextDouble() * 0.8, 0.01, q), "\"" + tag(m) + "\",\"" + sp + "\"", 3);
        }
        MissionManager.broadcast(m, new Packets.HorrorEvent(HorrorType.GORE.ordinal(), 1.6f, r.nextLong(), 0, 0));
    }

    public static void cleanup(Mission m, ServerLevel lvl) {
        cmd(lvl, "kill @e[tag=" + tag(m) + "]");
        State s = STATES.remove(m.id);
        if (s != null) for (ItemEntity it : s.items) if (it.isAlive()) it.discard();
        lvl.setBlock(m.base().offset(1, 2, 2), Blocks.AIR.defaultBlockState(), 3);
    }
}
