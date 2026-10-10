package com.firestar.santoryu.client;

import com.firestar.santoryu.SantoryuMod;
import com.firestar.santoryu.common.Beats;
import com.firestar.santoryu.common.Move;
import com.firestar.santoryu.registry.ModItems;
import com.firestar.santoryu.server.Techniques;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

/**
 * Development only, off unless the game is started with -Dsantoryu.selftest=true: makes a flat world, arms the
 * player, puts a target in front of him, casts every technique in turn and photographs the beats, then quits.
 * The build server runs it so every change is seen in the real game, not just compiled.
 */
public final class SelfTest {
    public static final boolean ON = Boolean.getBoolean("santoryu.selftest");
    private static boolean worldRequested;
    private static int ticksInWorld;
    private static int moveIndex = -1;
    private static long castAt;
    private static final List<int[]> SHOTS = new ArrayList<>();
    private static boolean done;

    private SelfTest() {}

    /** Ticks of each technique to photograph: the beats that matter. */
    private static int[] beats(Move m) {
        return switch (m) {
            case ONI_GIRI -> new int[]{4, Beats.ONI_HIT, Beats.ONI_HIT + 1, Beats.ONI_HIT + 3, 14};
            case TATSU_MAKI -> new int[]{12, 24, 30, 31};
            case POUND_HO -> new int[]{6, 11, 16};
            case SHISHI_SONSON -> new int[]{5, 14, 22, 28, 40, Beats.SHISHI_CLICK, Beats.SHISHI_CLICK + 1, Beats.SHISHI_CLICK + 2, Beats.SHISHI_CLICK + 4, 60};
            case HYAKUHACHI -> new int[]{8, 30, Beats.HYAKU_FIRE, Beats.HYAKU_FIRE + 1, 56, 66};
            case KOKUJO -> new int[]{8, 24, 50, 76, Beats.KOKUJO_BURST, Beats.KOKUJO_BURST + 1, 92};
            case SANZEN_SEKAI -> new int[]{10, 30, 56, 80, 96, Beats.SANZEN_HIT, Beats.SANZEN_HIT + 1, Beats.SANZEN_HIT + 3, Beats.SANZEN_HIT + 6, 118};
            case ASURA -> new int[]{10, 40, 70, 80, 100, 120, 140, 158, Beats.ASURA_HIT, Beats.ASURA_HIT + 1, Beats.ASURA_HIT + 2, Beats.ASURA_HIT + 4, Beats.ASURA_HIT + 7, 185};
            case RENGOKU -> new int[]{5, 30, Beats.RENGOKU_CONQUER, 55, 70, 105, 122, Beats.RENGOKU_HIT, Beats.RENGOKU_HIT + 1, Beats.RENGOKU_HIT + 3, Beats.RENGOKU_HIT + 6, 150};
            case DRAGON -> new int[]{20, Beats.DRAGON_CONQUER, 60, 90, 125, 170, 190, Beats.DRAGON_HIT - 4, Beats.DRAGON_HIT, Beats.DRAGON_HIT + 1, Beats.DRAGON_HIT + 2, Beats.DRAGON_HIT + 5, Beats.DRAGON_HIT + 9, 230, 250};
        };
    }

    public static void tick() {
        if (!ON || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                SantoryuMod.LOGGER.info("[selftest] creating a flat world");
                LevelSettings settings = new LevelSettings("santoryu_selftest", GameType.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), net.minecraft.world.level.WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("santoryu_selftest", settings, new WorldOptions(1033L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        ticksInWorld++;
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        if (ticksInWorld == 100) {
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.serverLevel();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                p.getInventory().clearContent();
                p.getInventory().setItem(0, new ItemStack(ModItems.ENMA.get()));
                p.getInventory().setItem(1, new ItemStack(ModItems.WADO.get()));
                p.getInventory().setItem(2, new ItemStack(ModItems.KITETSU.get()));
                p.getInventory().selected = 0;
                p.teleportTo(level, 0.5, p.getY(), 0.5, 0.0F, 4.0F);
                mc.execute(() -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
                // a row of stone pillars to cut, a little behind the target
                for (int x = -3; x <= 3; x++) {
                    for (int y = 0; y < 5; y++) {
                        level.setBlockAndUpdate(BlockPos.containing(x, p.getY() + y, 16), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
                    }
                }
            });
        }
        if (ticksInWorld < 160) {
            return;
        }
        long now = mc.level.getGameTime();
        if (moveIndex < 0 || (moveIndex < Move.values().length && now > castAt + Move.byId(moveIndex).length + 30)) {
            moveIndex++;
            if (moveIndex >= Move.values().length) {
                done = true;
                SantoryuMod.LOGGER.info("[selftest] finished; stopping");
                mc.stop();
                return;
            }
            Move m = Move.byId(moveIndex);
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.serverLevel();
                level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, p.getBoundingBox().inflate(80)).forEach(z -> z.discard());
                p.teleportTo(level, 0.5, p.getY(), 0.5, 0.0F, 4.0F);
                net.minecraft.world.entity.animal.IronGolem target = EntityType.IRON_GOLEM.create(level);
                if (target != null) {
                    target.moveTo(0.5, p.getY(), 8.5, 180.0F, 0.0F);
                    target.setNoAi(true);
                    target.setPersistenceRequired();
                    level.addFreshEntity(target);
                }
                p.setYRot(0.0F);
                p.setXRot(4.0F);
                p.connection.teleport(0.5, p.getY(), 0.5, 0.0F, 4.0F);
            });
            castAt = now + 6;
            SHOTS.clear();
            for (int b : beats(m)) {
                SHOTS.add(new int[]{b, 0});
            }
            SantoryuMod.LOGGER.info("[selftest] casting {}", m);
        }
        if (now == castAt) {
            Move m = Move.byId(moveIndex);
            server.execute(() -> Techniques.request(server.getPlayerList().getPlayers().get(0), m));
        }
    }

    /** After each rendered frame: photograph the beats we are waiting for. */
    public static void afterFrame() {
        if (!ON || done || moveIndex < 0 || moveIndex >= Move.values().length) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        ClientCast c = mc.player == null ? null : Casts.of(mc.player);
        if (c == null) {
            return;
        }
        float t = c.time(mc.getFrameTime());
        for (int[] shot : SHOTS) {
            if (shot[1] == 0 && t >= shot[0]) {
                shot[1] = 1;
                new File(mc.gameDirectory, "screenshots/selftest").mkdirs();
                String name = String.format("%02d_%s_t%03d.png", moveIndex, c.move.key, shot[0]);
                Screenshot.grab(mc.gameDirectory, "selftest/" + name, mc.getMainRenderTarget(), msg -> {});
            }
        }
    }

    @SuppressWarnings("unused")
    private static File dir() {
        return new File(Minecraft.getInstance().gameDirectory, "screenshots/selftest");
    }

    @SuppressWarnings("unused")
    private static Vec3 unused() {
        return Vec3.ZERO;
    }
}
