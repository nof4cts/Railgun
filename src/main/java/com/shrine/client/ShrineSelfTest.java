package com.shrine.client;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.shrine.common.Move;
import com.shrine.server.Techniques;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
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
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Development only, off unless the game runs with -Dshrine.selftest=true: Sukuna in a flat world with golems, Mahoraga
 * and buildings ahead; Open with its film, Open without it (the move as in the film), the World-Cutting Slash and the
 * domain, photographed and logged along the way.
 */
@Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
public final class ShrineSelfTest {
    public static final boolean ON = Boolean.getBoolean("shrine.selftest");
    private static boolean worldRequested;
    private static int tick;
    private static boolean done;
    private static String pendingShot;
    private static final List<Step> SCRIPT = new ArrayList<>();
    private static int next;

    private record Step(int at, String shot, CameraType camera, Consumer<ServerPlayer> server) {}

    private ShrineSelfTest() {}

    private static void step(int at, String shot, CameraType cam, Consumer<ServerPlayer> server) {
        SCRIPT.add(new Step(at, shot, cam, server));
    }

    private static void cast(ServerPlayer p, Move m, boolean film) {
        Techniques.testReset(p);
        Energy.set(p, Energy.MAX);
        Techniques.cast(p, m, film);
        System.out.println("[selftest] cast " + m + (film ? " (film)" : ""));
    }

    private static void report(ServerPlayer p, String what) {
        ServerLevel l = p.serverLevel();
        List<LivingEntity> near = l.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(120), e -> e != p && e.isAlive());
        long maho = near.stream().filter(e -> e instanceof com.mahoraga.mod.MahoragaEntity).count();
        String golems = near.stream().filter(e -> e instanceof IronGolem).map(e -> String.format("%.0f", e.getHealth())).toList().toString();
        System.out.println("[selftest] " + what + ": mahoraga alive " + maho + ", golem health " + golems);
    }

    private static int countBlocks(ServerLevel l, BlockPos c, int r) {
        int n = 0;
        for (BlockPos q : BlockPos.betweenClosed(c.offset(-r, 0, -r), c.offset(r, 12, r))) {
            if (!l.getBlockState(q).isAir()) {
                n++;
            }
        }
        return n;
    }

    /** An arena at x0: golems and Mahoraga ahead (+z), stone and wood buildings around them. */
    private static void arena(ServerPlayer p, int x0) {
        ServerLevel l = p.serverLevel();
        l.getEntitiesOfClass(Mob.class, new AABB(x0 - 150, -64, -150, x0 + 150, 320, 200)).forEach(Mob::discard);
        double y = l.getHeight(Heightmap.Types.MOTION_BLOCKING, x0, 0);
        p.teleportTo(l, x0 + 0.5, y, 0.5, 0.0F, 3.0F);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(l);
            if (g != null) {
                g.moveTo(x0 + 0.5 + i * 4, y, 32.5, 180.0F, 0.0F);
                g.setNoAi(true);
                g.setPersistenceRequired();
                l.addFreshEntity(g);
            }
        }
        var maho = com.mahoraga.mod.MahoragaMod.MAHORAGA.get().create(l);
        if (maho != null) {
            maho.moveTo(x0 + 7.5, y, 38.5, 180.0F, 0.0F);
            maho.setNoAi(true);
            maho.setPersistenceRequired();
            l.addFreshEntity(maho);
        }
        for (int[] b : new int[][]{{-14, 24}, {12, 26}, {-6, 44}, {16, 46}}) {
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

    private static void build() {
        CameraType back = CameraType.THIRD_PERSON_BACK, front = CameraType.THIRD_PERSON_FRONT;
        // 1. Open with its film
        int t = 120;
        step(t - 30, "0_arena", back, p -> {
            arena(p, 0);
            report(p, "before Open (film)");
        });
        step(t, null, back, p -> cast(p, Move.FUGA, true));
        for (int k : new int[]{20, 120, 260, 420, 520, 640, 760, 840, 900, 960, 985}) {
            step(t + k, String.format("1_film_t%04d", k), back, null);
        }
        int film = com.shrine.common.Timing.FUGA_FILM;
        for (int k : new int[]{2, 6, 12, 25, 45, 70}) {
            step(t + film + k, String.format("2_film_blast_t%04d", film + k), back, null);
        }
        step(t + film + 40, null, back, p -> report(p, "after Open (film)"));
        t += film + 200;
        step(t, "2_film_crater", back, null);
        // 2. Open without its film: the move as in the film
        t += 60;
        step(t - 30, null, front, p -> {
            arena(p, 400);
            report(p, "before Open (no film)");
        });
        step(t, null, front, p -> cast(p, Move.FUGA, false));
        for (int k : new int[]{4, 12, 22, 30, 36, 44}) {
            step(t + k, String.format("3_open_t%03d", k), front, null);
        }
        for (int k : new int[]{52, 62, 72, 79, 82, 86, 90, 94, 100, 115, 140, 180, 230, 270}) {
            step(t + k, String.format("3_open_t%03d", k), back, null);
        }
        step(t + 120, null, back, p -> report(p, "after Open (no film)"));
        t += 300;
        // 3. World-Cutting Slash damage
        step(t, null, back, p -> {
            arena(p, 800);
            report(p, "before World Slash");
            cast(p, Move.WORLD_SLASH, false);
        });
        step(t + 120, "4_world_slash", back, p -> report(p, "after World Slash"));
        t += 300;
        // 4. The domain levels everything in its reach
        step(t, null, back, p -> {
            arena(p, 1200);
            System.out.println("[selftest] blocks standing in the domain's reach before: " + countBlocks(p.serverLevel(), p.blockPosition(), 30));
            cast(p, Move.DOMAIN, false);
        });
        for (int k : new int[]{60, 100, 150}) {
            step(t + k, String.format("5_domain_t%03d", k), back, null);
        }
        step(t + 200, "5_domain_after", back, p ->
                System.out.println("[selftest] blocks standing in the domain's reach after: " + countBlocks(p.serverLevel(), p.blockPosition(), 30)));
        step(t + 230, "END", back, null);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ON || done || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!worldRequested && mc.screen instanceof TitleScreen) {
                worldRequested = true;
                System.out.println("[selftest] creating a flat world");
                LevelSettings settings = new LevelSettings("shrine_selftest", GameType.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("shrine_selftest", settings, new WorldOptions(1033L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        tick++;
        if (tick == 60) {
            build();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.serverLevel();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                Sorcerers.choose(p, Sorcerer.SUKUNA);
                System.out.println("[selftest] Sukuna chosen");
            });
        }
        while (next < SCRIPT.size() && SCRIPT.get(next).at() <= tick) {
            Step s = SCRIPT.get(next++);
            if (s.camera() != null && mc.options.getCameraType() != s.camera() && !com.shrine.client.cinematic.Cutscene.active()) {
                mc.options.setCameraType(s.camera());
            }
            if (s.server() != null) {
                Consumer<ServerPlayer> run = s.server();
                server.execute(() -> run.accept(server.getPlayerList().getPlayers().get(0)));
            }
            if ("END".equals(s.shot())) {
                done = true;
                System.out.println("[selftest] finished; stopping");
                mc.stop();
                return;
            }
            if (s.shot() != null) {
                pendingShot = s.shot();
            }
        }
    }

    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END || pendingShot == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        new File(mc.gameDirectory, "screenshots/shrine").mkdirs();
        Screenshot.grab(mc.gameDirectory, "shrine/" + pendingShot + ".png", mc.getMainRenderTarget(), msg -> {});
        System.out.println("[selftest] shot " + pendingShot + " film=" + com.shrine.client.film.Film.playing());
        pendingShot = null;
    }
}
