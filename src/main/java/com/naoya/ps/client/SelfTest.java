package com.naoya.ps.client;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.naoya.ps.Naoya;
import com.naoya.ps.server.Projection;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
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
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Development only (-Dnaoya.selftest=true): a flat world, Naoya chosen, golems to fight, every move photographed. */
public final class SelfTest {
    public static final boolean ON = Boolean.getBoolean("naoya.selftest");
    private static boolean requested;
    private static int ticks;
    private static int index = -1;
    private static long castAt;
    private static boolean done;
    private static final List<int[]> SHOTS = new ArrayList<>();

    private record Step(int slot, String label, double dist, int hold, int[] beats, boolean front) {}

    private static final Step[] ORDER = {
            new Step(0, "dash", 12, 30, new int[]{1, 2, 3, 5, 8}, false),
            new Step(1, "freeze", 7, 40, new int[]{1, 2, 4, 8, 16, 30}, false),
            new Step(1, "freeze_front", 7, 30, new int[]{3, 10, 24}, true),
            new Step(2, "barrage", 4, 40, new int[]{2, 6, 12, 18, 24, 25, 28}, false),
            new Step(3, "step", 6, 24, new int[]{1, 3, 8}, false),
            new Step(4, "boom", 10, 34, new int[]{1, 2, 4, 8, 14}, false),
            new Step(5, "trap", 4, 30, new int[]{2, 5, 10, 20}, false),
            new Step(6, "shatter", 4, 30, new int[]{1, 2, 3, 6, 12}, false),
            new Step(7, "ult", 9, Projection.ULT_LENGTH + 30, new int[]{4, 12, 20, 30, 40, 46, 47, 51, 56, 58, 60, 61, 63, 66, 72, 84, 98}, false),
    };

    private SelfTest() {}

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    public static void tick() {
        if (!ON || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!requested && mc.screen instanceof TitleScreen) {
                requested = true;
                Naoya.LOGGER.info("[selftest] creating a flat world");
                LevelSettings settings = new LevelSettings("naoya_selftest", GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("naoya_selftest", settings, new WorldOptions(77L, false, false),
                        a -> a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
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
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                p.getInventory().clearContent();
                Sorcerers.choose(p, Sorcerer.NAOYA);
                Naoya.LOGGER.info("[selftest] Naoya chosen: {}", Sorcerers.get(p));
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks < 160) {
            return;
        }
        long now = mc.level.getGameTime();
        if (index < 0 || (index < ORDER.length && now > castAt + ORDER[index].hold)) {
            index++;
            if (index >= ORDER.length) {
                done = true;
                Naoya.LOGGER.info("[selftest] finished; stopping");
                mc.stop();
                return;
            }
            Step s = ORDER[index];
            boolean keep = s.slot == 6 || s.label.equals("freeze_front");
            server.execute(() -> {
                ServerPlayer p = player(server);
                ServerLevel level = p.serverLevel();
                if (!keep) {
                    level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(80)).forEach(Mob::discard);
                    p.teleportTo(level, 0.5, p.getY(), 0.5, 0.0F, 5.0F);
                    p.connection.teleport(0.5, p.getY(), 0.5, 0.0F, 5.0F);
                    for (int i = -1; i <= 1; i++) {
                        IronGolem g = EntityType.IRON_GOLEM.create(level);
                        if (g != null) {
                            g.moveTo(0.5 + i * 2.6, p.getY(), 0.5 + s.dist + Math.abs(i) * 0.6, 180.0F, 0.0F);
                            g.setNoAi(true);
                            g.setPersistenceRequired();
                            level.addFreshEntity(g);
                        }
                    }
                } else {
                    p.connection.teleport(p.getX(), p.getY(), p.getZ(), p.getYRot(), 5.0F);
                }
            });
            mc.options.setCameraType(s.front ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
            castAt = now + 6;
            SHOTS.clear();
            for (int b : s.beats) {
                SHOTS.add(new int[]{b, 0});
            }
            Naoya.LOGGER.info("[selftest] {}", s.label);
        }
        if (now == castAt) {
            int slot = ORDER[index].slot;
            server.execute(() -> Projection.useKey(player(server), slot));
        }
    }

    public static void afterFrame() {
        if (!ON || done || index < 0 || index >= ORDER.length) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float t = mc.level.getGameTime() + mc.getFrameTime() - castAt;
        for (int[] shot : SHOTS) {
            if (shot[1] == 0 && t >= shot[0]) {
                shot[1] = 1;
                new File(mc.gameDirectory, "screenshots/naoya").mkdirs();
                String name = String.format("%02d_%s_t%03d.png", index, ORDER[index].label, shot[0]);
                Screenshot.grab(mc.gameDirectory, "naoya/" + name, mc.getMainRenderTarget(), msg -> {});
                return;
            }
        }
    }
}
