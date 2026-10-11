package com.firestar.mih.client;

import com.firestar.mih.MihMod;
import com.firestar.mih.common.Beats;
import com.firestar.mih.common.Move;
import com.firestar.mih.server.StandUsers;
import com.firestar.mih.server.Techniques;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development only, off unless the game runs with -Dmih.selftest=true: a flat world with some scenery, the Stand
 * granted and summoned, iron golems to hit, then every move in turn, Time Acceleration and the Universe Reset,
 * photographed on their beats. The build server plays it so every change is seen in the real game.
 */
public final class SelfTest {
    public static final boolean ON = Boolean.getBoolean("mih.selftest");
    private static boolean worldRequested;
    private static int ticks;
    private static int index = -1;
    private static long castAt;
    private static boolean done;
    private static final List<int[]> SHOTS = new ArrayList<>();
    private static final List<int[]> ACCEL_SHOTS = new ArrayList<>();
    private static final List<int[]> RESET_SHOTS = new ArrayList<>();

    private static final Move[] ORDER = {Move.SUMMON, Move.PUNCH, Move.BARRAGE, Move.FINISHER, Move.KNIVES, Move.SPEED_SLICE,
            Move.INFINITE_SPEED, Move.JUDGEMENT, Move.DOUBLE_ACCEL, Move.TIME_ACCEL};

    private SelfTest() {}

    private static int accelLength() {
        return Integer.getInteger("mih.selftest.accel", 200);
    }

    private static int[] beats(Move m) {
        return switch (m) {
            case SUMMON -> new int[]{2, 5, 10, 22};
            case PUNCH -> new int[]{2, 3, 5};
            case BARRAGE -> new int[]{6, 14, 24, 40};
            case FINISHER -> new int[]{7, 10, 11, 13};
            case KNIVES -> new int[]{6, 8, 10, 13};
            case SPEED_SLICE -> new int[]{4, 13, 19, 23, 25, 26, 28, 33, 45};
            case INFINITE_SPEED -> new int[]{6, 12, 15, 17, 18, 20, 30, 40};
            case JUDGEMENT -> new int[]{8, 14, 17, 23, 30, 38, 47, 55, 60, 61, 63, 68, 85};
            case DOUBLE_ACCEL -> new int[]{8, 18, 24, 28, 29, 31, 38, 48};
            case TIME_ACCEL -> new int[]{8, 20, 30, 42, 50, 51, 53, 60, 72, 84};
            default -> new int[0];
        };
    }

    private static int waitFor(Move m) {
        return m == Move.TIME_ACCEL ? Beats.TA_START + accelLength() + Move.UNIVERSE_RESET.length + 60 : m.length + 26;
    }

    public static void tick() {
        if (!ON || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                MihMod.LOGGER.info("[selftest] creating a flat world");
                LevelSettings settings = new LevelSettings("mih_selftest", GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("mih_selftest", settings, new WorldOptions(1033L, false, false),
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
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.serverLevel();
                level.setDayTime(5000);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                StandUsers.grant(p, true);
                p.getInventory().clearContent();
                scenery(level, BlockPos.containing(p.position()));
                mc.execute(() -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                MihMod.LOGGER.info("[selftest] Stand granted, scenery built");
            });
        }
        if (ticks < 160) {
            return;
        }
        long now = mc.level.getGameTime();
        if (index < 0 || (index < ORDER.length && now > castAt + waitFor(ORDER[index]))) {
            index++;
            if (index >= ORDER.length) {
                done = true;
                MihMod.LOGGER.info("[selftest] finished; stopping");
                mc.stop();
                return;
            }
            Move m = ORDER[index];
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                reset(p, m == Move.TIME_ACCEL ? -22.0F : 4.0F);
            });
            castAt = now + 8;
            SHOTS.clear();
            for (int b : beats(m)) {
                SHOTS.add(new int[]{b, 0});
            }
            if (m == Move.TIME_ACCEL) {
                ACCEL_SHOTS.clear();
                for (int k = 10; k < accelLength(); k += Math.max(10, accelLength() / 8)) {
                    ACCEL_SHOTS.add(new int[]{Beats.TA_START + k, 0});
                }
                RESET_SHOTS.clear();
                for (int b : new int[]{10, 24, 34, 37, 39, 42, 50, 58, 64, 72, 84, 100, 120, 140, 160, 175, 188, 198, 203, 205, 208, 216, 226, 240, 262, 290}) {
                    RESET_SHOTS.add(new int[]{b, 0});
                }
            }
            MihMod.LOGGER.info("[selftest] {}", m);
        }
        if (now == castAt) {
            Move m = ORDER[index];
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                if (m == Move.SUMMON) {
                    StandUsers.state(p).summoned = false;
                }
                Techniques.request(p, m);
            });
        }
    }

    /** Some world for the reset to tear apart: rings of wool, a little house, trees, pillars. */
    private static void scenery(ServerLevel level, BlockPos o) {
        Block[] wool = {Blocks.RED_WOOL, Blocks.ORANGE_WOOL, Blocks.YELLOW_WOOL, Blocks.LIME_WOOL, Blocks.LIGHT_BLUE_WOOL, Blocks.PURPLE_WOOL};
        for (int r = 4; r <= 18; r += 3) {
            Block b = wool[(r / 3) % wool.length];
            for (int a = 0; a < 360; a += 3) {
                int x = (int) Math.round(Math.cos(Math.toRadians(a)) * r), z = (int) Math.round(Math.sin(Math.toRadians(a)) * r);
                level.setBlockAndUpdate(o.offset(x, -1, z), b.defaultBlockState());
            }
        }
        for (int x = -3; x <= 3; x++) {
            for (int y = 0; y < 4; y++) {
                for (int z = -14; z <= -9; z++) {
                    boolean wall = x == -3 || x == 3 || z == -14 || z == -9;
                    if (wall && !(z == -9 && x == 0 && y < 2)) {
                        level.setBlockAndUpdate(o.offset(x, y, z), (y == 3 ? Blocks.OAK_PLANKS : Blocks.STONE_BRICKS).defaultBlockState());
                    }
                }
            }
        }
        for (int[] t : new int[][]{{12, 6}, {-12, 4}, {9, -12}, {-10, -6}}) {
            for (int y = 0; y < 5; y++) {
                level.setBlockAndUpdate(o.offset(t[0], y, t[1]), Blocks.OAK_LOG.defaultBlockState());
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 3; dy <= 5; dy++) {
                        if (Math.abs(dx) + Math.abs(dz) + (dy - 3) < 5 && !(dx == 0 && dz == 0 && dy < 5)) {
                            level.setBlockAndUpdate(o.offset(t[0] + dx, dy, t[1] + dz), Blocks.OAK_LEAVES.defaultBlockState());
                        }
                    }
                }
            }
        }
        for (int x = -6; x <= 6; x += 4) {
            for (int y = 0; y < 3; y++) {
                level.setBlockAndUpdate(o.offset(x, y, 14), Blocks.GOLD_BLOCK.defaultBlockState());
            }
        }
    }

    private static void reset(ServerPlayer p, float pitch) {
        ServerLevel level = p.serverLevel();
        level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(80)).forEach(Mob::discard);
        p.teleportTo(level, 0.5, p.getY(), 0.5, 0.0F, pitch);
        p.connection.teleport(0.5, p.getY(), 0.5, 0.0F, pitch);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (g != null) {
                g.moveTo(0.5 + i * 2.6, p.getY(), 7.5 + Math.abs(i) * 0.6, 180.0F, 0.0F);
                g.setNoAi(true);
                g.setPersistenceRequired();
                level.addFreshEntity(g);
            }
        }
    }

    /** After each rendered frame: photograph the beats we are waiting for. */
    public static void afterFrame() {
        if (!ON || done || index < 0 || index >= ORDER.length) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Move m = ORDER[index];
        float now = mc.level.getGameTime() + mc.getFrameTime();
        ClientCast c = Casts.of(mc.player);
        if (c != null && c.move == m) {
            float t = c.time(mc.getFrameTime());
            shoot(SHOTS, t, String.format("%02d_%s", index, m.key));
        }
        if (m == Move.TIME_ACCEL) {
            shoot(ACCEL_SHOTS, now - castAt, "11_accel");
            ClientCast r = Casts.reset();
            if (r != null) {
                shoot(RESET_SHOTS, r.time(mc.getFrameTime()), "12_reset");
            }
        }
    }

    private static void shoot(List<int[]> shots, float t, String prefix) {
        Minecraft mc = Minecraft.getInstance();
        for (int[] shot : shots) {
            if (shot[1] == 0 && t >= shot[0]) {
                shot[1] = 1;
                new File(mc.gameDirectory, "screenshots/selftest").mkdirs();
                String name = String.format("%s_t%03d.png", prefix, shot[0]);
                Screenshot.grab(mc.gameDirectory, "selftest/" + name, mc.getMainRenderTarget(), msg -> {});
                return;
            }
        }
    }
}
