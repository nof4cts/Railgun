package com.firestar.sage.client;

import com.firestar.sage.SageMod;
import com.firestar.sage.common.SMove;
import com.firestar.sage.server.Rites;
import com.firestar.sage.server.Sage;
import com.firestar.styles.Style;
import com.firestar.styles.Styles;
import com.firestar.styles.StylesPrefs;
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
import net.minecraft.world.level.block.Blocks;

/**
 * Development only: after Requiem Rose's test, the Celestial Sage plays its whole kit in the real game against iron golems,
 * photographed on its beats; then Starfall once more with the style menu's cutscene switch off. Logs what each rite did.
 */
public final class SageTest {
    private static boolean on, done;
    private static int index = -1;
    private static long castAt;
    private static boolean cast;
    private static int wait;
    private static final List<int[]> SHOTS = new ArrayList<>();
    private static final List<IronGolem> GOLEMS = new ArrayList<>();

    private record Step(SMove move, String label, double dist, CameraType cam, int hold, boolean cutscenes, int[] beats) {}

    private static final Step[] ORDER = {
            new Step(SMove.MANTLE, "mantle", 8, CameraType.THIRD_PERSON_BACK, 0, true, new int[]{4, 16, 30, 46, 60, 72, 86, 94, 97, 100, 106, 118}),
            new Step(null, "mantled_front", 8, CameraType.THIRD_PERSON_FRONT, 30, true, new int[]{14, 26}),
            new Step(null, "mantled_back", 8, CameraType.THIRD_PERSON_BACK, 20, true, new int[]{12}),
            new Step(null, "first_person", 8, CameraType.FIRST_PERSON, 20, true, new int[]{12}),
            new Step(SMove.STAFF, "staff_0", 2.8, CameraType.THIRD_PERSON_BACK, 12, true, new int[]{2, 4, 6}),
            new Step(SMove.STAFF, "staff_1", 2.8, CameraType.THIRD_PERSON_BACK, 12, true, new int[]{2, 4, 6}),
            new Step(SMove.STAFF, "staff_2", 2.8, CameraType.THIRD_PERSON_BACK, 20, true, new int[]{2, 4, 6, 10}),
            new Step(SMove.PULL, "pull", 14, CameraType.THIRD_PERSON_BACK, 0, true, new int[]{4, 8, 12, 16, 19, 21, 24, 30}),
            new Step(SMove.ORBS, "orbs", 16, CameraType.THIRD_PERSON_BACK, 0, true, new int[]{6, 10, 16, 22, 30, 40, 50}),
            new Step(SMove.MIRROR, "mirror", 9, CameraType.THIRD_PERSON_BACK, 0, true, new int[]{2, 4, 7, 9, 11, 13, 18}),
            new Step(SMove.STARFALL, "starfall", 18, CameraType.THIRD_PERSON_BACK, 0, true,
                    new int[]{8, 20, 34, 50, 70, 86, 94, 96, 97, 99, 104, 112, 117, 120, 132, 146}),
            new Step(SMove.THORNWOOD, "thornwood", 10, CameraType.THIRD_PERSON_BACK, 0, true, new int[]{6, 12, 15, 18, 22, 28, 38, 52}),
            new Step(SMove.COLOSSUS, "colossus", 16, CameraType.THIRD_PERSON_BACK, 0, true,
                    new int[]{6, 20, 36, 52, 64, 76, 90, 97, 99, 101, 106, 116, 130, 146}),
            new Step(SMove.ECLIPSE, "eclipse", 14, CameraType.THIRD_PERSON_BACK, 0, true,
                    new int[]{8, 24, 40, 60, 84, 104, 120, 138, 142, 148, 160, 176, 190, 204, 218}),
            new Step(SMove.STARFALL, "starfall_nocut", 18, CameraType.THIRD_PERSON_BACK, 0, false, new int[]{8, 40, 90, 97, 100, 118, 130}),
            new Step(SMove.UNMANTLE, "unmantle", 8, CameraType.THIRD_PERSON_BACK, 0, true, new int[]{2, 8, 14, 20}),
    };

    private SageTest() {}

    public static void begin() {
        on = true;
        index = -1;
        wait = 20;
        SageMod.LOGGER.info("[sagetest] begin");
    }

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static int waitFor(Step s) {
        return s.hold > 0 ? s.hold : s.move.length + 30;
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
                    Styles.choose(p, Style.SAGE);
                    ground(p.serverLevel(), BlockPos.containing(0.5, p.getY(), 0.5));
                });
            }
            return;
        }
        long now = mc.level.getGameTime();
        if (index < 0 || (index < ORDER.length && now > castAt + waitFor(ORDER[index]))) {
            if (index >= 0) {
                report(server, ORDER[index]);
            }
            index++;
            if (index >= ORDER.length) {
                done = true;
                StylesPrefs.setCutscenes(true);
                SageMod.LOGGER.info("[sagetest] finished; stopping");
                mc.stop();
                return;
            }
            Step s = ORDER[index];
            StylesPrefs.setCutscenes(s.cutscenes);
            boolean keep = s.move == null || s.move == SMove.UNMANTLE
                    || (s.move == SMove.STAFF && index > 0 && ORDER[index - 1].move == SMove.STAFF);
            server.execute(() -> {
                ServerPlayer p = player(server);
                if (!keep) {
                    golems(p, s.dist);
                }
                p.setHealth(p.getMaxHealth());
                Rites.testReset(p);
            });
            castAt = now + (keep ? 1 : 6);
            cast = false;
            SHOTS.clear();
            for (int b : s.beats) {
                SHOTS.add(new int[]{b, 0});
            }
            mc.options.setCameraType(s.cam);
            SageMod.LOGGER.info("[sagetest] {}", s.label);
        }
        Step s = ORDER[index];
        if (s.move != null && !cast && now >= castAt) {
            cast = true;
            server.execute(() -> {
                ServerPlayer p = player(server);
                Rites.ask(p, s.move == SMove.UNMANTLE ? SMove.MANTLE : s.move);
                SageMod.LOGGER.info("[sagetest] cast {} form={}", s.move, Sage.form(p));
            });
        }
    }

    private static void report(MinecraftServer server, Step s) {
        server.execute(() -> {
            ServerPlayer p = player(server);
            StringBuilder hp = new StringBuilder();
            for (IronGolem g : GOLEMS) {
                hp.append(g.isAlive() ? String.format("%.0f", g.getHealth()) : "dead").append(' ');
            }
            SageMod.LOGGER.info("[sagetest] after {}: golems [{}] form={} player hp={} y={}", s.label, hp.toString().trim(), Sage.form(p),
                    p.getHealth(), String.format("%.1f", p.getY()));
        });
    }

    private static void golems(ServerPlayer p, double dist) {
        ServerLevel level = p.serverLevel();
        level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(120)).forEach(Mob::discard);
        GOLEMS.clear();
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0);
        double px = 0.5 - Math.min(3.0, 0.3 * dist + 0.6);
        float yaw = (float) Math.toDegrees(Math.atan2(-(0.5 - px), dist));
        p.connection.teleport(px, y, 0.5, yaw, 6);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (g != null) {
                double gx = 0.5 + i * 2.8, gz = 0.5 + dist + Math.abs(i) * 0.8;
                int gy = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(gx),
                        (int) Math.floor(gz));
                g.moveTo(gx, gy, gz, 180, 0);
                g.setNoAi(true);
                g.setPersistenceRequired();
                level.addFreshEntity(g);
                GOLEMS.add(g);
            }
        }
    }

    /** A wide stone court to fight on, so the craters and the cuts read against something other than flat grass. */
    private static void ground(ServerLevel level, BlockPos o) {
        for (int x = -40; x <= 40; x++) {
            for (int z = -24; z <= 60; z++) {
                boolean line = (x % 8 == 0) || (z % 8 == 0);
                level.setBlock(o.offset(x, -1, z), (line ? Blocks.POLISHED_DEEPSLATE : Blocks.SMOOTH_STONE).defaultBlockState(), 2);
            }
        }
        for (int[] t : new int[][]{{-12, 30}, {12, 34}, {-20, 12}, {20, 16}, {0, 52}}) {
            for (int y = 0; y < 7; y++) {
                level.setBlock(o.offset(t[0], y, t[1]), Blocks.QUARTZ_PILLAR.defaultBlockState(), 2);
            }
            level.setBlock(o.offset(t[0], 7, t[1]), Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState(), 2);
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
        String prefix = String.format("s%02d_%s", index, s.label);
        float t;
        if (s.move == null) {
            t = mc.level.getGameTime() + mc.getFrameTime() - castAt;
        } else {
            SageCasts.Play c = SageCasts.of(mc.player);
            if (c == null || c.rite() != s.move) {
                return;
            }
            t = c.time(mc.getFrameTime());
        }
        for (int[] shot : SHOTS) {
            if (shot[1] == 0 && t >= shot[0]) {
                shot[1] = 1;
                new File(mc.gameDirectory, "screenshots/styles").mkdirs();
                Screenshot.grab(mc.gameDirectory, "styles/" + String.format("%s_t%03d.png", prefix, shot[0]), mc.getMainRenderTarget(), m -> {});
                SageMod.LOGGER.info("[sagetest] shot {} t{} cam={} director={} mantle={}", prefix, shot[0],
                        mc.gameRenderer.getMainCamera().getPosition(), com.firestar.sage.client.cam.Director.active(),
                        SageForms.mantle(mc.player, 0));
                return;
            }
        }
    }
}
