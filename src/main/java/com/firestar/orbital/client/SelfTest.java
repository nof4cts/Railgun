package com.firestar.orbital.client;

import com.firestar.orbital.OrbitalMod;
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
 * Development only (-Dsi.selftest=true): a flat world with a village and a hill; Helios fired and steered across the
 * village, then Antipode fired on the hill, both films photographed on their beats and the aftermath from the air.
 */
public final class SelfTest {
    public static final boolean ON = Boolean.getBoolean("si.selftest");
    private static boolean worldRequested;
    private static int ticks;
    private static long calledAt = -1;
    private static String phase = "";
    private static boolean done;
    private static final List<int[]> SHOTS = new ArrayList<>();
    private static final int[] HELIOS_BEATS = {20, 56, 88, 120, 160, 196, 232, 258, 276, 300, 312, 326, 333, 336, 344, 362, 384, 420, 470, 512};
    private static final int[] ANTIPODE_BEATS = {20, 60, 90, 120, 160, 200, 240, 276, 300, 316, 334, 352, 365, 368, 372, 392, 410, 430, 480, 520};
    private static final int HX = 0, AX = 160;

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
                OrbitalMod.LOGGER.info("[selftest] creating a flat world");
                LevelSettings settings = new LevelSettings("oa_selftest", GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("oa_selftest", settings, new WorldOptions(1033L, false, false),
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
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(com.firestar.orbital.registry.ModItems.UPLINK.get()));
                BlockPos o = BlockPos.containing(0.5, p.getY(), 0.5);
                town(level, o.offset(HX, 0, 40), 26);
                hill(level, o.offset(AX, 0, 55), 20, 14);
                town(level, o.offset(AX, 0, 55), 12);
                OrbitalMod.LOGGER.info("[selftest] test site built");
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks == 220) {
            begin(server, mc, "helios", HX, com.firestar.orbital.common.Cannon.HELIOS, HELIOS_BEATS);
        }
        if (calledAt < 0) {
            return;
        }
        long t = mc.level.getGameTime() - calledAt;
        if (t % 40 == 0) {
            OrbitalMod.LOGGER.info("[selftest] {} t={} fps={} smoke={}", phase, t, mc.getFps(), com.firestar.orbital.client.fx.Smoke.count());
        }
        if (phase.equals("helios")) {
            if (t >= 395 && t <= 495 && t % 4 == 0) {
                // steer the lance: sweep the gaze across the town
                float yaw = (float) ((t - 395) / 100.0 * 70.0 - 35.0);
                server.execute(() -> {
                    ServerPlayer p = player(server);
                    p.connection.teleport(HX + 0.5, p.getY(), 0.5, yaw, 6);
                });
            }
            if (t == 506) {
                vantage(server, mc, HX + 40, 45, -10, 60, 40);
            }
            if (t > 545) {
                begin(server, mc, "antipode", AX, com.firestar.orbital.common.Cannon.ANTIPODE, ANTIPODE_BEATS);
            }
        } else if (phase.equals("antipode")) {
            if (t == 474) {
                vantage(server, mc, AX + 50, 40, 10, 50, 30);
            }
            if (t == 514) {
                vantage(server, mc, AX, 70, 10, 0, 60);
            }
            if (t > 540) {
                done = true;
                OrbitalMod.LOGGER.info("[selftest] finished; strikes still active: {}; smoke {}; stopping", Strikes.ACTIVE.size(),
                        com.firestar.orbital.client.fx.Smoke.count());
                mc.stop();
            }
        }
    }

    private static void begin(MinecraftServer server, Minecraft mc, String name, int x, com.firestar.orbital.common.Cannon c, int[] beats) {
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        server.execute(() -> {
            ServerPlayer p = player(server);
            p.getAbilities().flying = false;
            p.onUpdateAbilities();
            int y = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, 0);
            p.connection.teleport(x + 0.5, y, 0.5, 0.0F, 18.0F);
            int ty = p.serverLevel().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, c == com.firestar.orbital.common.Cannon.HELIOS ? 40 : 55);
            if (c == com.firestar.orbital.common.Cannon.HELIOS) {
                // as a player would: aim at the mark and right-click the uplink
                double tz = 40.5, dy = ty - (p.getEyeY());
                float pitch = (float) -Math.toDegrees(Math.atan2(dy, tz - 0.5));
                p.connection.teleport(x + 0.5, p.getY(), 0.5, 0.0F, pitch);
                p.setXRot(pitch);
                p.setYRot(0);
                net.minecraft.world.item.ItemStack up = p.getMainHandItem();
                up.use(p.serverLevel(), p, net.minecraft.world.InteractionHand.MAIN_HAND);
                OrbitalMod.LOGGER.info("[selftest] uplink used: strikes={}", com.firestar.orbital.server.Strikes.count());
                if (com.firestar.orbital.server.Strikes.count() == 0) {
                    com.firestar.orbital.server.Strikes.begin(p, c, new Vec3(x + 0.5, ty, 40.5));
                }
            } else {
                com.firestar.orbital.server.Strikes.begin(p, c, new Vec3(x + 0.5, ty, 55.5));
            }
        });
        calledAt = mc.level.getGameTime() + 2;
        phase = name;
        SHOTS.clear();
        for (int b : beats) {
            SHOTS.add(new int[]{b, 0});
        }
        OrbitalMod.LOGGER.info("[selftest] {} fired", name);
    }

    /** Fly up to a vantage point to look at what is left. */
    private static void vantage(MinecraftServer server, Minecraft mc, double x, double dy, double z, float yaw, float pitch) {
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        server.execute(() -> {
            ServerPlayer p = player(server);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.connection.teleport(x, -60 + dy, z, yaw, pitch);
        });
    }

    private static void hill(ServerLevel level, BlockPos c, int r, int h) {
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz) / r;
                if (d > 1) {
                    continue;
                }
                int top = (int) Math.round(h * (1 - d * d));
                for (int y = 0; y < top; y++) {
                    level.setBlock(c.offset(dx, y, dz), (y == top - 1 ? Blocks.GRASS_BLOCK : y > top - 4 ? Blocks.DIRT : Blocks.STONE).defaultBlockState(), 2);
                }
            }
        }
    }

    /** A town in their path: houses, towers, a wall, trees, a few cows. */
    private static void town(ServerLevel level, BlockPos o, int count) {
        java.util.Random r = new java.util.Random(5);
        for (int i = 0; i < count; i++) {
            int x = -40 + r.nextInt(80), z = -30 + r.nextInt(60);
            if (Math.abs(x) < 4 && Math.abs(z) < 4) {
                continue;
            }
            if (i % 5 == 0) {
                tower(level, o.offset(x, 0, z), 5 + r.nextInt(3), 14 + r.nextInt(14));
            } else {
                house(level, o.offset(x, 0, z), 5 + r.nextInt(4), 4 + r.nextInt(3), 5 + r.nextInt(4));
            }
        }
        for (int i = 0; i < count; i++) {
            tree(level, o.offset(-45 + r.nextInt(90), 0, -35 + r.nextInt(70)));
        }
        for (int i = 0; i < 8; i++) {
            Cow c = EntityType.COW.create(level);
            if (c != null) {
                c.moveTo(o.getX() - 20 + r.nextInt(40), o.getY(), o.getZ() - 15 + r.nextInt(30), 0, 0);
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
                String name = String.format("%s_t%03d.png", phase, shot[0]);
                Screenshot.grab(mc.gameDirectory, "selftest/" + name, mc.getMainRenderTarget(), msg -> {});
                return;
            }
        }
    }
}
