package com.gojo.rework.client;

import com.gojo.rework.Gojo2;
import com.gojo.rework.common.GMove;
import com.gojo.rework.server.GRites;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Development only: after the Sukuna self-test (shrine.selftest), Gojo's rework plays every technique against iron
 * golems among stone buildings, each in its own arena, photographed on its beats and logged.
 */
public final class GojoTest {
    private static boolean on, done;
    private static int index = -1;
    private static long castAt;
    private static boolean cast;
    private static int wait;
    private static final List<int[]> SHOTS = new ArrayList<>();

    private record Step(GMove move, String label, double dist, float pitch, CameraType cam, int hold, int[] beats) {}

    private static final Step[] ORDER = {
            new Step(null, "infinity", 6, 0, CameraType.THIRD_PERSON_FRONT, 50, new int[]{2, 6, 12, 20, 34, 44}),
            new Step(GMove.RED, "red", 20, 4, CameraType.THIRD_PERSON_BACK, 0, new int[]{4, 10, 14, 16, 18, 20, 23, 30, 44}),
            new Step(GMove.MAX_BLUE, "max_blue", 14, 0, CameraType.THIRD_PERSON_BACK, 0, new int[]{8, 20, 30, 44, 60, 80, 99, 104}),
            new Step(GMove.MAX_RED, "max_red", 18, 0, CameraType.THIRD_PERSON_BACK, 0, new int[]{8, 20, 27, 28, 29, 31, 35, 42, 56}),
            new Step(GMove.PURPLE, "purple", 26, 0, CameraType.THIRD_PERSON_BACK, 0, new int[]{6, 12, 18, 24, 29, 30, 31, 34, 40, 52}),
            new Step(GMove.IMAGINARY, "imaginary", 30, 0, null, 0,
                    new int[]{8, 20, 40, 62, 84, 100, 110, 120, 130, 134, 135, 137, 146, 160, 176}),
            new Step(GMove.PURPLE_200, "purple_200", 30, 0, null, 0,
                    new int[]{20, 48, 70, 92, 112, 124, 138, 150, 151, 153, 162, 180, 200}),
            new Step(GMove.NUKE, "nuke", 40, 16, null, 0, new int[]{10, 40, 70, 95, 115, 122, 123, 128, 134, 136, 140, 150, 170, 200, 220}),
            new Step(GMove.DOMAIN, "domain", 8, 0, null, 0, new int[]{4, 12, 24, 40, 49, 56, 70, 81, 90, 105, 120, 135, 150}),
            new Step(null, "domain_inside", 8, 0, CameraType.THIRD_PERSON_BACK, 80, new int[]{10, 40, 70}),
            new Step(GMove.DOMAIN_02, "domain_02", 10, 0, CameraType.THIRD_PERSON_BACK, 0, new int[]{2, 6, 8, 10, 14, 30}),
    };

    private GojoTest() {}

    public static void begin() {
        on = true;
        index = -1;
        wait = 30;
        com.jjk.mod.client.JjkPrefs.setSkipCutscenes(false);
        Gojo2.LOGGER.info("[gojotest] begin");
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
                    Sorcerers.choose(p, Sorcerer.GOJO);
                    Gojo2.LOGGER.info("[gojotest] Gojo chosen: {}", Sorcerers.get(p));
                });
            }
            return;
        }
        long now = mc.level.getGameTime();
        if (index < 0 || (index < ORDER.length && now > castAt + waitFor(ORDER[index]))) {
            if (index >= 0) {
                report(server, ORDER[index].label);
            }
            index++;
            if (index >= ORDER.length) {
                done = true;
                Gojo2.LOGGER.info("[gojotest] finished; stopping");
                mc.stop();
                return;
            }
            Step s = ORDER[index];
            boolean keep = "domain_inside".equals(s.label);
            int idx = index;
            server.execute(() -> {
                ServerPlayer p = player(server);
                if (!keep) {
                    arena(p, 260 * (idx + 1), s.dist, s.pitch);
                }
                p.setHealth(p.getMaxHealth());
                com.gojo.mod.CursedEnergy.refund(p, 10000);
            });
            castAt = now + (keep ? 1 : 40);
            cast = false;
            SHOTS.clear();
            for (int b : s.beats) {
                SHOTS.add(new int[]{b, 0});
            }
            if (s.cam != null) {
                mc.options.setCameraType(s.cam);
            }
            Gojo2.LOGGER.info("[gojotest] {}", s.label);
        }
        Step s = ORDER[index];
        if (!cast && now >= castAt) {
            cast = true;
            server.execute(() -> {
                ServerPlayer p = player(server);
                if (s.move != null) {
                    GRites.testReset(p);
                    boolean ok = GRites.ask(p, s.move);
                    Gojo2.LOGGER.info("[gojotest] cast {} ok={}", s.move, ok);
                } else if ("infinity".equals(s.label)) {
                    com.gojo.mod.Techniques.infinity(p.serverLevel(), p);
                    // something thrown at him, stopped by it
                    for (int i = 0; i < 3; i++) {
                        Arrow a = new Arrow(p.serverLevel(), p.getX() + 6 + i, p.getY() + 1.5, p.getZ() + 4);
                        a.shoot(-6 - i, 0.2, -4, 1.6F, 0);
                        p.serverLevel().addFreshEntity(a);
                    }
                }
            });
        }
        if ("infinity".equals(s.label) && now == castAt + 20) {
            server.execute(() -> {
                ServerPlayer p = player(server);
                com.gojo.rework.server.GRites.ripple(p, p.position().add(0, 1, 0).add(1.2, 0, 1.0), new Vec3(1, 0, 1));
            });
        }
        if ("infinity".equals(s.label) && now == castAt + 46) {
            server.execute(() -> com.gojo.mod.Techniques.infinity(player(server).serverLevel(), player(server)));
        }
    }

    private static void report(MinecraftServer server, String what) {
        server.execute(() -> {
            ServerPlayer p = player(server);
            List<String> hp = p.serverLevel().getEntitiesOfClass(IronGolem.class, p.getBoundingBox().inflate(200), LivingEntity::isAlive).stream()
                    .map(g -> String.format("%.0f", g.getHealth())).toList();
            Gojo2.LOGGER.info("[gojotest] after {}: golems alive {} {} player y={} hp={}", what, hp.size(), hp, String.format("%.1f", p.getY()),
                    p.getHealth());
        });
    }

    /** Golems ahead (+z) among stone buildings, the player at x0 facing them. */
    private static void arena(ServerPlayer p, int x0, double dist, float pitch) {
        ServerLevel l = p.serverLevel();
        l.getEntitiesOfClass(Mob.class, new AABB(x0 - 200, -64, -200, x0 + 200, 320, 260)).forEach(Mob::discard);
        for (int cx = -2; cx <= 4; cx++) {
            l.getChunk((x0 >> 4) - 2 + cx, 0);
            l.getChunk((x0 >> 4) - 2 + cx, 2);
            l.getChunk((x0 >> 4) - 2 + cx, 4);
        }
        int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x0, 0);
        p.teleportTo(l, x0 + 0.5, y, 0.5, 0.0F, pitch);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(l);
            if (g != null) {
                g.moveTo(x0 + 0.5 + i * 3.5, y, 0.5 + dist + Math.abs(i) * 1.5, 180.0F, 0.0F);
                g.setNoAi(true);
                g.setPersistenceRequired();
                l.addFreshEntity(g);
            }
        }
        for (int[] b : new int[][]{{-16, 18}, {12, 22}, {-8, 40}, {14, 48}, {-2, 64}}) {
            for (int dx = 0; dx < 7; dx++) {
                for (int dz = 0; dz < 7; dz++) {
                    for (int dy = 0; dy < 12; dy++) {
                        boolean wall = dx == 0 || dz == 0 || dx == 6 || dz == 6;
                        if (wall || dy == 11) {
                            l.setBlockAndUpdate(BlockPos.containing(x0 + b[0] + dx, y + dy, b[1] + dz),
                                    (dy % 4 == 0 ? Blocks.SMOOTH_STONE : (dx + dz) % 5 == 0 ? Blocks.GLASS : Blocks.WHITE_CONCRETE).defaultBlockState());
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
        String prefix = String.format("g%02d_%s", index, s.label);
        float t;
        if (s.move == null) {
            t = mc.level.getGameTime() + mc.getFrameTime() - castAt;
        } else {
            GCasts.Play c = GCasts.of(mc.player);
            if (c == null || c.move() != s.move) {
                return;
            }
            t = c.time(mc.getFrameTime());
        }
        for (int[] shot : SHOTS) {
            if (shot[1] == 0 && t >= shot[0]) {
                shot[1] = 1;
                new File(mc.gameDirectory, "screenshots/gojo").mkdirs();
                Screenshot.grab(mc.gameDirectory, "gojo/" + String.format("%s_t%03d.png", prefix, shot[0]), mc.getMainRenderTarget(), m -> {});
                Gojo2.LOGGER.info("[gojotest] shot {} t{} director={} cam={}", prefix, shot[0], com.gojo.rework.client.cam.GDirector.active(),
                        mc.gameRenderer.getMainCamera().getPosition());
                return;
            }
        }
    }
}
