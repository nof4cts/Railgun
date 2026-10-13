package com.firestar.rose.client;

import com.firestar.rose.RoseMod;
import com.firestar.rose.common.RMove;
import com.firestar.rose.server.Reaper;
import com.firestar.rose.server.Reaping;
import com.firestar.styles.Style;
import com.firestar.styles.Styles;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/**
 * Development only: after the switcher test (fs.selftest), Requiem Rose plays its whole kit in the real game against
 * iron golems, photographed on its beats, so every change is seen as it will be played.
 */
public final class RoseTest {
    private static boolean on, done;
    private static int index = -1;
    private static long castAt;
    private static final List<int[]> SHOTS = new ArrayList<>();
    private static int wait;

    private record Step(RMove move, String label, double dist, CameraType cam, int hold, int[] beats) {}

    private static final Step[] ORDER = {
            new Step(RMove.ASCEND, "ascend", 6, CameraType.THIRD_PERSON_BACK, 0, new int[]{6, 24, 40, 56, 72, 88, 96, 98, 99, 100, 102, 106, 116, 130, 146}),
            new Step(null, "stance", 6, CameraType.THIRD_PERSON_FRONT, 30, new int[]{10, 24}),
            new Step(null, "stance_back", 6, CameraType.THIRD_PERSON_BACK, 20, new int[]{12}),
            new Step(null, "first_person", 6, CameraType.FIRST_PERSON, 20, new int[]{12}),
            new Step(RMove.REAP, "reap_0", 2.8, CameraType.THIRD_PERSON_BACK, 9, new int[]{2, 3, 4, 6}),
            new Step(RMove.REAP, "reap_1", 2.8, CameraType.THIRD_PERSON_BACK, 9, new int[]{3, 4, 6}),
            new Step(RMove.REAP, "reap_2", 2.8, CameraType.THIRD_PERSON_BACK, 9, new int[]{3, 4, 6}),
            new Step(RMove.REAP, "reap_3", 2.8, CameraType.THIRD_PERSON_BACK, 20, new int[]{3, 4, 5, 6, 9, 14}),
            new Step(RMove.TETHER, "tether", 12, CameraType.THIRD_PERSON_BACK, 0, new int[]{6, 8, 11, 14, 17, 20, 24, 25, 28}),
            new Step(RMove.CRESCENT, "crescent", 10, CameraType.THIRD_PERSON_BACK, 0, new int[]{6, 10, 14, 18, 22, 26, 30, 34}),
            new Step(RMove.STEP, "step", 8, CameraType.THIRD_PERSON_BACK, 0, new int[]{2, 3, 4, 6, 7, 10}),
            new Step(RMove.RAY, "ray", 14, CameraType.THIRD_PERSON_BACK, 0, new int[]{8, 30, 55, 80, 90, 94, 95, 96, 100, 110, 122, 130, 136}),
            new Step(RMove.REQUIEM, "requiem", 6, CameraType.THIRD_PERSON_BACK, 0, new int[]{8, 12, 14, 20, 28, 36, 44, 52, 57, 58, 59, 61, 66}),
            new Step(RMove.RIFT, "rift", 9, CameraType.THIRD_PERSON_BACK, 0, new int[]{10, 22, 26, 27, 30, 40, 55, 75, 90, 96, 97, 100}),
            new Step(RMove.LAST_BLOOM, "last_bloom", 14, CameraType.THIRD_PERSON_BACK, 0,
                    new int[]{10, 30, 50, 70, 90, 110, 124, 128, 130, 134, 135, 136, 138, 144, 155, 170, 190, 205}),
            new Step(RMove.RELEASE, "release", 6, CameraType.THIRD_PERSON_BACK, 0, new int[]{2, 6, 12}),
    };

    private RoseTest() {}

    /** Called by the switcher test when it is finished. */
    public static void begin() {
        on = true;
        index = -1;
        wait = 20;
        RoseMod.LOGGER.info("[rosetest] begin");
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static int waitFor(Step s) {
        return s.hold > 0 ? s.hold : s.move.length + 20;
    }

    static void tick() {
        if (!on || done) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null) {
            return;
        }
        if (wait > 0) {
            if (--wait == 0) {
                server.execute(() -> {
                    ServerPlayer p = player(server);
                    p.setGameMode(GameType.CREATIVE);
                    p.getInventory().clearContent();
                    Styles.choose(p, Style.ROSE);
                    scenery(p.serverLevel(), BlockPos.containing(0.5, p.getY(), 0.5));
                });
            }
            return;
        }
        long now = mc.level.getGameTime();
        if (index < 0 || (index < ORDER.length && now > castAt + waitFor(ORDER[index]))) {
            index++;
            if (index >= ORDER.length) {
                done = true;
                server.execute(() -> RoseMod.LOGGER.info("[rosetest] finished; form={} ", Reaper.form(player(server))));
                RoseMod.LOGGER.info("[rosetest] finished; stopping");
                mc.stop();
                return;
            }
            Step s = ORDER[index];
            boolean chain = s.move == RMove.REAP && index > 0 && ORDER[index - 1].move == RMove.REAP;
            boolean keep = s.move == null || chain;
            server.execute(() -> {
                ServerPlayer p = player(server);
                if (!keep) {
                    golems(p, s.dist);
                }
                p.setHealth(p.getMaxHealth());
            });
            castAt = now + (keep ? 1 : 6);
            SHOTS.clear();
            for (int b : s.beats) {
                SHOTS.add(new int[]{b, 0});
            }
            mc.options.setCameraType(s.cam);
            RoseMod.LOGGER.info("[rosetest] {}", s.label);
        }
        Step s = ORDER[index];
        if (s.move != null && now == castAt) {
            server.execute(() -> {
                Reaping.request(player(server), s.move);
                RoseMod.LOGGER.info("[rosetest] cast {} form={}", s.move, Reaper.form(player(server)));
            });
        }
    }

    private static void golems(ServerPlayer p, double dist) {
        ServerLevel level = p.serverLevel();
        level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(90)).forEach(Mob::discard);
        double y = Math.floor(p.getY());
        // stand a little to the side and face the middle golem, so the camera behind sees both
        double px = 0.5 - Math.min(3.0, 0.3 * dist + 0.6);
        float yaw = (float) Math.toDegrees(Math.atan2(-(0.5 - px), dist));
        p.connection.teleport(px, y, 0.5, yaw, 6);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (g != null) {
                g.moveTo(0.5 + i * 2.6, y, 0.5 + dist + Math.abs(i) * 0.8, 180, 0);
                g.setNoAi(true);
                g.setPersistenceRequired();
                level.addFreshEntity(g);
            }
        }
    }

    private static void scenery(ServerLevel level, BlockPos o) {
        Block[] ring = {Blocks.POLISHED_BLACKSTONE, Blocks.CRIMSON_PLANKS, Blocks.PINK_CONCRETE, Blocks.STONE_BRICKS};
        for (int r = 5; r <= 30; r += 5) {
            Block b = ring[(r / 5) % ring.length];
            for (int a = 0; a < 360; a += 2) {
                int x = (int) Math.round(Math.cos(Math.toRadians(a)) * r), z = (int) Math.round(Math.sin(Math.toRadians(a)) * r);
                level.setBlockAndUpdate(o.offset(x, -1, z), b.defaultBlockState());
            }
        }
        for (int[] t : new int[][]{{9, 20}, {-9, 22}, {16, 8}, {-15, 10}, {4, 34}, {-4, -12}}) {
            for (int y = 0; y < 6; y++) {
                level.setBlockAndUpdate(o.offset(t[0], y, t[1]), Blocks.DARK_OAK_LOG.defaultBlockState());
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 4; dy <= 6; dy++) {
                        if (Math.abs(dx) + Math.abs(dz) + (dy - 4) < 5 && !(dx == 0 && dz == 0 && dy < 6)) {
                            level.setBlockAndUpdate(o.offset(t[0] + dx, dy, t[1] + dz), Blocks.CHERRY_LEAVES.defaultBlockState());
                        }
                    }
                }
            }
        }
    }

    static void afterFrame() {
        if (!on || done || index < 0 || index >= ORDER.length) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        Step s = ORDER[index];
        String prefix = String.format("r%02d_%s", index, s.label);
        float t;
        if (s.move == null) {
            t = mc.level.getGameTime() + mc.getFrameTime() - castAt;
        } else {
            RoseCast c = RoseCasts.of(mc.player);
            if (c == null || c.move != s.move) {
                return;
            }
            t = c.time(mc.getFrameTime());
        }
        for (int[] shot : SHOTS) {
            if (shot[1] == 0 && t >= shot[0]) {
                shot[1] = 1;
                new File(mc.gameDirectory, "screenshots/styles").mkdirs();
                Screenshot.grab(mc.gameDirectory, "styles/" + String.format("%s_t%03d.png", prefix, shot[0]), mc.getMainRenderTarget(), m -> {});
                return;
            }
        }
    }
}
