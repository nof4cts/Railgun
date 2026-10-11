package com.firestar.worldbreaker.client;

import com.firestar.worldbreaker.WorldbreakerMod;
import com.firestar.worldbreaker.server.Marches;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/**
 * Development only (-Dsi.selftest=true): a flat world with a town in the giants' path, the march called, the whole film
 * photographed on its beats, then a flight round the aftermath. The build server plays it so every change is seen.
 */
public final class SelfTest {
    public static final boolean ON = Boolean.getBoolean("si.selftest");
    private static boolean worldRequested;
    private static int ticks;
    private static long calledAt = -1;
    private static boolean done;
    private static final List<int[]> SHOTS = new ArrayList<>();
    private static final int[] FILM_BEATS = {12, 30, 52, 68, 80, 96, 108, 116, 124, 138, 150, 160, 168, 180, 196, 214, 228, 244, 258, 268, 278, 286, 296};
    /** After the film: {tick, x, dy, z, yaw, pitch} vantage points (relative to the caller's start). */
    private static final double[][] VANTAGE = {
            {330, 45, 22, 10, 125, 18},
            {380, 0, 3, 40, 180, 8},
            {440, -60, 70, 0, -90, 42},
            {500, 0, 4, -175, 0, -6},
            {560, 30, 10, -160, 20, -16},
            {640, 0, 60, 60, 180, 35},
            {720, 60, 6, -215, 90, -4}};

    private SelfTest() {}

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().get(0);
    }

    public static void tick() {
        if (!ON || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                WorldbreakerMod.LOGGER.info("[selftest] creating a flat world");
                LevelSettings settings = new LevelSettings("wb_selftest", GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("wb_selftest", settings, new WorldOptions(1033L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ticks++;
        if (ticks == 100) {
            server.execute(() -> {
                ServerPlayer p = player(server);
                ServerLevel level = p.serverLevel();
                level.setDayTime(5200);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                p.getInventory().clearContent();
                p.teleportTo(level, 0.5, p.getY(), 0.5, 0.0F, 0.0F);
                town(level, BlockPos.containing(0.5, p.getY(), 0.5));
                WorldbreakerMod.LOGGER.info("[selftest] town built");
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks == 200) {
            server.execute(() -> {
                ServerPlayer p = player(server);
                p.connection.teleport(0.5, p.getY(), 0.5, 0.0F, 0.0F);
                Marches.begin(p, new Vec3(0, 0, 1));
            });
            calledAt = mc.level.getGameTime() + 2;
            for (int b : FILM_BEATS) {
                SHOTS.add(new int[]{b, 0});
            }
            for (double[] v : VANTAGE) {
                SHOTS.add(new int[]{(int) v[0], 0});
            }
            WorldbreakerMod.LOGGER.info("[selftest] march called");
        }
        if (calledAt < 0) {
            return;
        }
        long t = mc.level.getGameTime() - calledAt;
        if (t % 40 == 0) {
            WorldbreakerMod.LOGGER.info("[selftest] t={} fps={} smoke={}", t, mc.getFps(), com.firestar.worldbreaker.client.fx.Smoke.count());
        }
        for (double[] v : VANTAGE) {
            if (t == (long) v[0] - 6) {
                mc.options.setCameraType(CameraType.FIRST_PERSON);
                server.execute(() -> {
                    ServerPlayer p = player(server);
                    p.getAbilities().flying = true;
                    p.onUpdateAbilities();
                    p.connection.teleport(0.5 + v[1], -60 + v[2], 0.5 + v[3], (float) v[4], (float) v[5]);
                });
            }
        }
        if (t > 760) {
            done = true;
            WorldbreakerMod.LOGGER.info("[selftest] finished; marches still active: {}; smoke {}; stopping", Marching.ACTIVE.size(), com.firestar.worldbreaker.client.fx.Smoke.count());
            mc.stop();
        }
    }

    /** A town in their path: houses, towers, a wall, trees, a few cows. */
    private static void town(ServerLevel level, BlockPos o) {
        java.util.Random r = new java.util.Random(5);
        for (int i = 0; i < 26; i++) {
            int x = -60 + r.nextInt(120), z = -140 + r.nextInt(240);
            if (Math.abs(x) < 6 && Math.abs(z) < 8) {
                continue;
            }
            if (i % 5 == 0) {
                tower(level, o.offset(x, 0, z), 5 + r.nextInt(3), 14 + r.nextInt(14));
            } else {
                house(level, o.offset(x, 0, z), 5 + r.nextInt(4), 4 + r.nextInt(3), 5 + r.nextInt(4));
            }
        }
        for (int x = -70; x <= 70; x++) {
            for (int y = 0; y < 6; y++) {
                level.setBlockAndUpdate(o.offset(x, y, 60), (y == 5 && x % 2 == 0 ? Blocks.AIR : Blocks.COBBLESTONE).defaultBlockState());
            }
        }
        for (int i = 0; i < 30; i++) {
            tree(level, o.offset(-80 + r.nextInt(160), 0, -160 + r.nextInt(260)));
        }
        for (int i = 0; i < 12; i++) {
            Cow c = EntityType.COW.create(level);
            if (c != null) {
                c.moveTo(o.getX() - 20 + r.nextInt(40), o.getY(), o.getZ() + 20 + r.nextInt(30), 0, 0);
                level.addFreshEntity(c);
            }
        }
    }

    private static void house(ServerLevel level, BlockPos b, int w, int h, int d) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                for (int y = 0; y < h; y++) {
                    boolean wall = x == 0 || z == 0 || x == w - 1 || z == d - 1;
                    if (wall) {
                        Block blk = (x == 0 || x == w - 1) && (z == 0 || z == d - 1) ? Blocks.OAK_LOG : y == 2 && (x + z) % 3 == 0 ? Blocks.GLASS : Blocks.OAK_PLANKS;
                        level.setBlockAndUpdate(b.offset(x, y, z), blk.defaultBlockState());
                    }
                }
                level.setBlockAndUpdate(b.offset(x, h, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                if (x > 0 && x < w - 1 && z > 0 && z < d - 1) {
                    level.setBlockAndUpdate(b.offset(x, h + 1, z), Blocks.SPRUCE_PLANKS.defaultBlockState());
                }
            }
        }
    }

    private static void tower(ServerLevel level, BlockPos b, int w, int h) {
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < w; z++) {
                for (int y = 0; y < h; y++) {
                    if (x == 0 || z == 0 || x == w - 1 || z == w - 1) {
                        level.setBlockAndUpdate(b.offset(x, y, z), (y % 6 == 3 ? Blocks.POLISHED_ANDESITE : Blocks.STONE_BRICKS).defaultBlockState());
                    }
                }
            }
        }
    }

    private static void tree(ServerLevel level, BlockPos b) {
        for (int y = 0; y < 5; y++) {
            level.setBlockAndUpdate(b.offset(0, y, 0), Blocks.OAK_LOG.defaultBlockState());
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 3; dy <= 5; dy++) {
                    if (Math.abs(dx) + Math.abs(dz) + (dy - 3) < 5 && !(dx == 0 && dz == 0 && dy < 5)) {
                        level.setBlockAndUpdate(b.offset(dx, dy, dz), Blocks.OAK_LEAVES.defaultBlockState());
                    }
                }
            }
        }
    }

    /** After each rendered frame: photograph the beats we are waiting for. */
    public static void afterFrame() {
        if (!ON || done || calledAt < 0) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        float t = mc.level.getGameTime() + mc.getFrameTime() - calledAt;
        for (int[] shot : SHOTS) {
            if (shot[1] == 0 && t >= shot[0]) {
                shot[1] = 1;
                new File(mc.gameDirectory, "screenshots/selftest").mkdirs();
                String name = String.format("%s_t%03d.png", shot[0] < 300 ? "film" : "world", shot[0]);
                Screenshot.grab(mc.gameDirectory, "selftest/" + name, mc.getMainRenderTarget(), msg -> {});
                return;
            }
        }
    }
}
