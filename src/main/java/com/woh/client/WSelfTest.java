package com.woh.client;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.woh.W;
import com.woh.common.Beat;
import com.woh.common.Ctx;
import com.woh.common.Script;
import com.woh.common.WMove;
import com.woh.server.Woh;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.function.Consumer;

/**
 * Development only, off unless the game runs with -Dwoh.selftest=true: Modulo Woh in a flat world, every move cast
 * in its own arena of golems and buildings, photographed on its blows and logged (damage dealt, blocks destroyed).
 */
@Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
public final class WSelfTest {
    public static final boolean ON = Boolean.getBoolean("woh.selftest");
    private static boolean worldRequested, done;
    private static int tick, next;
    private static String pendingShot;
    private static final List<Step> SCRIPT = new ArrayList<>();
    private static final WMove[] ORDER = {
            WMove.ASCENDED_BARRAGE, WMove.OVERWRITE_BLOW, WMove.ERASURE_HOOK, WMove.REWRITE_RUSH,
            WMove.REALITY_OVERWRITE, WMove.WOUNDS_UNDONE, WMove.GRACE_OVER_HEAVEN, WMove.NEGATION,
            WMove.HEAVENLY_LIGHTNING, WMove.FOURTEEN_PHRASES, WMove.REALITY_KNIVES, WMove.ROAD_TO_HEAVEN,
            WMove.TIME_STOP, WMove.KNIFE_RAIN, WMove.STILLNESS,
            WMove.WORLDS_TRUTH, WMove.RETURN_OVER_HEAVEN, WMove.HEAVEN_ASCENSION,
            WMove.REALITY_COLLAPSE, WMove.COLLAPSE_FINISHER};

    private record Step(int at, String shot, CameraType camera, Consumer<ServerPlayer> server) {}

    private WSelfTest() {}

    private static void step(int at, String shot, CameraType cam, Consumer<ServerPlayer> server) {
        SCRIPT.add(new Step(at, shot, cam, server));
    }

    private static int count(ServerLevel l, BlockPos c, int r, int up) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, -8, -r), c.offset(r, up, r))) if (!l.getBlockState(q).isAir()) n++;
        return n;
    }

    private static void report(ServerPlayer p, String what, int x0) {
        ServerLevel l = p.serverLevel();
        List<LivingEntity> near = l.getEntitiesOfClass(LivingEntity.class, new AABB(x0 - 120, -64, -120, x0 + 120, 320, 160), e -> e != p);
        String hp = near.stream().filter(LivingEntity::isAlive).map(e -> String.format("%.0f", e.getHealth())).toList().toString();
        W.LOG.info("[selftest] {}: living {} {}, blocks near arena {}", what, near.stream().filter(LivingEntity::isAlive).count(), hp,
                count(l, new BlockPos(x0, l.getHeight(Heightmap.Types.WORLD_SURFACE, x0, 20) - 1, 20), 24, 12));
    }

    /** An arena at x0: three golems ahead (+z), stone and wood buildings around them. */
    private static void arena(ServerPlayer p, int x0) {
        ServerLevel l = p.serverLevel();
        l.getEntitiesOfClass(Mob.class, new AABB(x0 - 200, -64, -200, x0 + 200, 320, 200)).forEach(Mob::discard);
        for (int cz = -2; cz <= 4; cz++) l.getChunk((x0 >> 4), cz);
        double y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x0, 0);
        p.teleportTo(l, x0 + 0.5, y, 0.5, 0.0F, 5.0F);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(l);
            if (g != null) {
                g.moveTo(x0 + 0.5 + i * 4, y, 14.5 + Math.abs(i) * 3, 180.0F, 0.0F);
                g.setNoAi(true);
                g.setPersistenceRequired();
                l.addFreshEntity(g);
            }
        }
        for (int[] b : new int[][]{{-14, 20}, {9, 22}, {-5, 34}, {12, 38}}) {
            for (int dx = 0; dx < 6; dx++) {
                for (int dz = 0; dz < 6; dz++) {
                    for (int dy = 0; dy < 9; dy++) {
                        boolean wall = dx == 0 || dz == 0 || dx == 5 || dz == 5;
                        if (wall || dy == 8) {
                            l.setBlockAndUpdate(BlockPos.containing(x0 + b[0] + dx, y + dy, b[1] + dz),
                                    (dy % 3 == 0 ? Blocks.OAK_LOG : Blocks.STONE_BRICKS).defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    private static void cast(ServerPlayer p, WMove m) {
        Woh.testReset(p);
        Energy.set(p, Energy.MAX);
        Woh.cast(p, m);
        W.LOG.info("[selftest] cast {}", m);
    }

    /** The ticks worth photographing: just after each blow, and the aftermath. */
    private static TreeSet<Integer> moments(WMove m) {
        Ctx c = new Ctx(m, 7, Vec3.ZERO, new Vec3(0, 0, 1), new Vec3(0, 1, 14), -1, new int[0], Vec3.ZERO, 48, 160, 320, 80, false, 1f);
        TreeSet<Integer> ts = new TreeSet<>();
        for (Beat b : Script.build(c)) {
            if (b.kind() == Beat.BF || b.kind() == Beat.IMPACT || b.kind() == Beat.CLEAVE || b.kind() == Beat.DOMAIN_ON
                    || b.kind() == Beat.OVERDRIVE || b.kind() == Beat.ERASE || b.kind() == Beat.STOP || b.kind() == Beat.SHARD
                    || (b.kind() == Beat.SLASH && b.t() < 10)) {
                if (ts.isEmpty() || b.t() - ts.last() > 8) ts.add(b.t());
            }
        }
        TreeSet<Integer> out = new TreeSet<>();
        for (int t : ts) {
            out.add(t + 1);
            out.add(t + 4);
            if (out.size() > 10) break;
        }
        out.add(Math.max(2, m.end / 3));
        out.add(m.end - 6);
        return out;
    }

    private static void build() {
        int t = 100;
        for (int k = 0; k < ORDER.length; k++) {
            WMove m = ORDER[k];
            int x0 = 2000 + k * 700;
            boolean keepArena = m == WMove.COLLAPSE_FINISHER;
            CameraType cam = m.cutscene ? CameraType.FIRST_PERSON : CameraType.THIRD_PERSON_BACK;
            int base = keepArena ? 2000 + (k - 1) * 700 : x0;
            if (!keepArena) {
                step(t - 60, null, cam, p -> arena(p, x0));
                step(t - 5, String.format("%02d_%s_before", k, m.name().toLowerCase()), cam, p -> report(p, "before " + m, x0));
            } else {
                // inside the domain (or Overdrive) that the previous move opened: a fresh set of golems on the spot
                step(t - 20, null, cam, p -> {
                    ServerLevel l = p.serverLevel();
                    for (int i = -1; i <= 1; i++) {
                        IronGolem g = EntityType.IRON_GOLEM.create(l);
                        if (g != null) {
                            g.moveTo(p.getX() + i * 4, p.getY(), p.getZ() + 12 + Math.abs(i) * 2, 180, 0);
                            g.setNoAi(true);
                            g.setPersistenceRequired();
                            l.addFreshEntity(g);
                        }
                    }
                });
            }
            final int cx = keepArena ? base : x0;
            step(t, null, cam, p -> cast(p, m));
            for (int s : moments(m)) step(t + s, String.format("%02d_%s_t%03d", k, m.name().toLowerCase(), s), cam, null);
            step(t + m.end + 30, String.format("%02d_%s_after", k, m.name().toLowerCase()), CameraType.THIRD_PERSON_BACK,
                    p -> report(p, "after " + m, cx));
            boolean chain = k + 1 < ORDER.length && ORDER[k + 1] == WMove.COLLAPSE_FINISHER;
            // big destruction takes a while to stream in
            t += m.end + (m.tier >= 2 ? 120 : 60) + (chain ? 0 : 40);
            if (m == WMove.HEAVEN_ASCENSION) {
                // let the ascension go again before the next move, so it doesn't colour the rest of the run
                step(t - 10, null, CameraType.THIRD_PERSON_BACK, Woh::endOverdrive);
            }
            if (m == WMove.WORLDS_TRUTH) {
                // the great crater takes a while to stream in; look down on it, then back to ground
                final int gx = cx;
                step(t + 240, null, CameraType.FIRST_PERSON, p -> {
                    ServerLevel l = p.serverLevel();
                    int gy = l.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, 14);
                    p.teleportTo(l, gx + 0.5, gy + 110, 14.5, 0.0F, 90.0F);
                });
                step(t + 300, String.format("%02d_worlds_truth_crater", k), CameraType.FIRST_PERSON, null);
                step(t + 320, null, CameraType.THIRD_PERSON_BACK, p -> {
                    ServerLevel l = p.serverLevel();
                    p.teleportTo(l, gx + 0.5, l.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, 0), 0.5, 0.0F, 5.0F);
                });
                t += 360;
            }
        }
        step(t + 40, "END", CameraType.THIRD_PERSON_BACK, null);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || done || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                W.LOG.info("[selftest] creating a flat world");
                LevelSettings settings = new LevelSettings("woh_selftest", GameType.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("woh_selftest", settings, new WorldOptions(1033L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return;
        tick++;
        if (tick == 60) {
            build();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.serverLevel();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                Sorcerers.choose(p, Sorcerer.WORLD_OVER_HEAVEN);
                W.LOG.info("[selftest] Modulo Woh chosen");
            });
        }
        while (next < SCRIPT.size() && SCRIPT.get(next).at() <= tick) {
            Step s = SCRIPT.get(next++);
            if (s.camera() != null && mc.options.getCameraType() != s.camera() && !WCam.running()) mc.options.setCameraType(s.camera());
            if (s.server() != null) {
                Consumer<ServerPlayer> run = s.server();
                server.execute(() -> {
                    try {
                        run.accept(server.getPlayerList().getPlayers().get(0));
                    } catch (Throwable t) {
                        W.LOG.error("[selftest] step failed", t);
                    }
                });
            }
            if ("END".equals(s.shot())) {
                done = true;
                W.LOG.info("[selftest] finished");
                mc.stop();
                return;
            }
            if (s.shot() != null) pendingShot = s.shot();
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END || pendingShot == null) return;
        Minecraft mc = Minecraft.getInstance();
        new File(mc.gameDirectory, "screenshots/woh").mkdirs();
        Screenshot.grab(mc.gameDirectory, "woh/" + pendingShot + ".png", mc.getMainRenderTarget(), msg -> {});
        W.LOG.info("[selftest] shot {} cutscene={}", pendingShot, WCam.running());
        pendingShot = null;
    }
}
