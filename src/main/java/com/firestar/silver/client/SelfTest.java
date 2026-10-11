package com.firestar.silver.client;

import com.firestar.silver.SilverMod;
import com.firestar.silver.common.Move;
import com.firestar.silver.server.Fighters;
import com.firestar.silver.server.Techniques;
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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * Development only, off unless the game runs with -Dsi.selftest=true: a flat world with some scenery, iron golems to
 * hit, then the whole kit in order - awaken, strikes, techniques, the instinct slipping blows, Zenith and its finishers -
 * photographed on their beats. The build server plays it so every change is seen in the real game.
 */
public final class SelfTest {
    public static final boolean ON = Boolean.getBoolean("si.selftest");
    private static boolean worldRequested;
    private static int ticks;
    private static int index = -1;
    private static long castAt;
    private static boolean done;
    private static final List<int[]> SHOTS = new ArrayList<>();

    /** One step: a move (or null for an instinct test where the golem swings at him), where to stand, what to shoot. */
    private record Step(Move move, String label, double dist, float pitch, int wait, int[] beats) {}

    private static final Step[] ORDER = {
            new Step(Move.AWAKEN_OMEN, "awaken_omen", 6.0, 4.0F, 0, new int[]{4, 14, 30, 46, 58, 64, 66, 70, 80, 100}),
            new Step(Move.STRIKE, "strike_a", 2.6, 4.0F, 10, new int[]{1, 3, 5}),
            new Step(Move.STRIKE, "strike_b", 2.6, 4.0F, 10, new int[]{3, 5}),
            new Step(Move.STRIKE, "strike_c", 2.6, 4.0F, 0, new int[]{3, 5}),
            new Step(Move.HEAVY, "heavy", 2.8, 4.0F, 0, new int[]{4, 8, 9, 11, 15}),
            new Step(Move.VOLLEY, "volley", 9.0, 4.0F, 0, new int[]{6, 12, 18, 24, 32}),
            new Step(Move.VANISH, "vanish", 6.0, 4.0F, 0, new int[]{2, 4, 6}),
            new Step(Move.WAVE, "wave", 9.0, 4.0F, 0, new int[]{6, 16, 26, 32, 34, 35, 37, 44, 56, 70}),
            new Step(Move.FLURRY, "flurry", 6.0, 4.0F, 0, new int[]{6, 11, 14, 20, 28, 36, 44, 45, 50}),
            new Step(Move.METEOR, "meteor", 4.0, 4.0F, 0, new int[]{6, 9, 14, 18, 24, 30, 34, 36, 42, 43, 50}),
            new Step(null, "instinct_omen", 2.4, 4.0F, 40, new int[]{1, 2, 3, 7, 8, 13, 14, 19, 25}),
            new Step(Move.AWAKEN_ZENITH, "awaken_zenith", 6.0, 4.0F, 0, new int[]{6, 20, 40, 60, 80, 94, 96, 98, 104, 120, 140}),
            new Step(Move.STRIKE, "strike_zenith", 2.6, 4.0F, 0, new int[]{2, 4}),
            new Step(Move.RUSH, "rush", 6.0, 4.0F, 0, new int[]{8, 12, 13, 18, 24, 30, 39, 44, 48, 49, 52, 60}),
            new Step(Move.SPHERE, "sphere", 10.0, -10.0F, 0, new int[]{10, 30, 50, 70, 88, 92, 98, 104, 106, 107, 110, 120, 140}),
            new Step(Move.FINAL, "final", 12.0, 4.0F, 0, new int[]{8, 24, 40, 56, 66, 70, 71, 73, 80, 96, 110, 125}),
            new Step(null, "instinct_zenith", 2.4, 4.0F, 40, new int[]{1, 2, 3, 7, 8, 13, 14, 19, 25}),
            new Step(Move.REVERT, "revert", 6.0, 4.0F, 0, new int[]{2, 5, 9, 14}),
    };

    private SelfTest() {}

    private static int waitFor(Step s) {
        if (s.wait > 0) {
            return s.wait;
        }
        return s.move == null ? 40 : s.move.length + 24;
    }

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
                SilverMod.LOGGER.info("[selftest] creating a flat world");
                LevelSettings settings = new LevelSettings("si_selftest", GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("si_selftest", settings, new WorldOptions(1033L, false, false),
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
                level.setDayTime(5000);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                p.getInventory().clearContent();
                scenery(level, BlockPos.containing(0.5, p.getY(), 0.5));
                mc.execute(() -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
                SilverMod.LOGGER.info("[selftest] scenery built");
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
                SilverMod.LOGGER.info("[selftest] finished; stopping");
                mc.stop();
                return;
            }
            Step s = ORDER[index];
            boolean keepGolems = s.move == Move.STRIKE && index > 0 && ORDER[index - 1].move == Move.STRIKE;
            server.execute(() -> {
                ServerPlayer p = player(server);
                if (!keepGolems) {
                    reset(p, s.pitch, s.dist);
                }
                // the instinct only answers real blows: survival for those, creative for the rest
                p.setGameMode(s.move == null ? GameType.SURVIVAL : GameType.CREATIVE);
                p.setHealth(p.getMaxHealth());
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 4, false, false));
            });
            castAt = now + (keepGolems ? 2 : 8);
            SHOTS.clear();
            for (int b : s.beats) {
                SHOTS.add(new int[]{b, 0});
            }
            SilverMod.LOGGER.info("[selftest] {}", s.label);
        }
        Step s = ORDER[index];
        if (s.move == null) {
            // the golem swings at him every six ticks; the instinct decides what lands
            long dt = now - castAt;
            if (dt >= 0 && dt <= 30 && dt % 6 == 0) {
                server.execute(() -> {
                    ServerPlayer p = player(server);
                    IronGolem g = p.serverLevel().getEntitiesOfClass(IronGolem.class, p.getBoundingBox().inflate(8)).stream().findFirst().orElse(null);
                    if (g != null) {
                        boolean hit = p.hurt(p.damageSources().mobAttack(g), 2.0F);
                        SilverMod.LOGGER.info("[selftest] golem swing: {} (form {})", hit ? "landed" : "slipped", Fighters.form(p));
                    }
                });
            }
            return;
        }
        if (now == castAt) {
            server.execute(() -> Techniques.request(player(server), s.move));
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

    private static void reset(ServerPlayer p, float pitch, double dist) {
        ServerLevel level = p.serverLevel();
        level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(80)).forEach(Mob::discard);
        p.teleportTo(level, 0.5, p.getY(), 0.5, 0.0F, pitch);
        p.connection.teleport(0.5, p.getY(), 0.5, 0.0F, pitch);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (g != null) {
                g.moveTo(0.5 + i * 2.6, p.getY(), 0.5 + dist + Math.abs(i) * 0.6, 180.0F, 0.0F);
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
        Step s = ORDER[index];
        String prefix = String.format("%02d_%s", index, s.label);
        if (s.move == null) {
            shoot(SHOTS, mc.level.getGameTime() + mc.getFrameTime() - castAt, prefix);
            return;
        }
        ClientCast c = Casts.of(mc.player);
        if (c != null && c.move == s.move) {
            shoot(SHOTS, c.time(mc.getFrameTime()), prefix);
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
