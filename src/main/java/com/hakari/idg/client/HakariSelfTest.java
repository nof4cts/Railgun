package com.hakari.idg.client;

import com.hakari.idg.ModRegistry;
import com.hakari.idg.TechniqueScrollItem;
import com.hakari.idg.server.HakariLogic;
import com.hakari.idg.server.HakariState;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Development only, off unless the game runs with -Dhakari.selftest=true: a flat world, Hakari chosen, targets and
 * some terrain inside the domain's reach, then every move, the domain, a pity riichi into the jackpot, the aura and the
 * jackpot moves, photographed along the way. The build server plays it so every change is seen in the real game.
 */
@Mod.EventBusSubscriber(modid = "jjk", value = Dist.CLIENT)
public final class HakariSelfTest {
    public static final boolean ON = Boolean.getBoolean("hakari.selftest");
    private static boolean worldRequested;
    private static int tick;
    private static boolean done;
    private static String pendingShot;
    private static final List<Step> SCRIPT = new ArrayList<>();
    private static int next;

    private record Step(int at, String shot, CameraType camera, Consumer<ServerPlayer> server) {}

    private HakariSelfTest() {}

    private static void step(int at, String shot, CameraType cam, Consumer<ServerPlayer> server) {
        SCRIPT.add(new Step(at, shot, cam, server));
    }

    private static void cast(ServerPlayer p, net.minecraftforge.registries.RegistryObject<Item> item) {
        p.getCooldowns().removeCooldown(item.get());
        HakariLogic.use(p, (TechniqueScrollItem) item.get());
    }

    private static void build() {
        int t = 140;
        CameraType back = CameraType.THIRD_PERSON_BACK;
        CameraType front = CameraType.THIRD_PERSON_FRONT;
        String[] names = {"reserve_ball", "shutter_doors", "rough_energy", "fever_breaker", "counter"};
        for (int i = 0; i < ModRegistry.MOVE_SCROLLS.size(); i++) {
            var item = ModRegistry.MOVE_SCROLLS.get(i);
            String n = names[i];
            step(t, null, back, p -> cast(p, item));
            for (int k : new int[]{3, 7, 12, 18, 26}) {
                step(t + k, String.format("1_base_%s_t%02d", n, k), back, null);
            }
            t += 70;
        }
        // the domain: its whole cinematic, then the inside
        step(t, null, back, p -> {
            reset(p);
            cast(p, ModRegistry.DOMAIN);
        });
        for (int k = 4; k <= 186; k += 9) {
            step(t + k, String.format("2_domain_t%03d", k), back, null);
        }
        t += 200;
        step(t, "3_inside_back", back, null);
        step(t + 10, "3_inside_front", front, null);
        // riichi forced to a win by the pity rule
        step(t + 20, null, back, p -> {
            HakariState st = HakariState.get(p);
            st.fails = 3;
            HakariLogic.onVisual(p, st);
            HakariLogic.onVisual(p, st);
        });
        for (int k = 4; k <= 70; k += 8) {
            step(t + 20 + k, String.format("4_riichi_t%02d", k), back, null);
        }
        t += 20 + 74;
        for (int k = 2; k <= 110; k += 9) {
            step(t + k, String.format("5_jackpot_t%03d", k), back, null);
        }
        t += 120;
        step(t, "6_aura_front", front, null);
        step(t + 10, "6_aura_back", back, null);
        step(t + 20, "6_aura_front2", front, null);
        t += 30;
        String[] jp = {"lucky_volley", "lucky_rushdown", "overwhelming_luck", "energy_surge", "rhythm"};
        for (int i = 0; i < ModRegistry.MOVE_SCROLLS.size(); i++) {
            var item = ModRegistry.MOVE_SCROLLS.get(i);
            String n = jp[i];
            step(t, null, back, p -> {
                reset(p);
                cast(p, item);
            });
            for (int k : new int[]{3, 8, 14, 22, 32}) {
                step(t + k, String.format("7_jackpot_%s_t%02d", n, k), back, null);
            }
            t += 80;
        }
        step(t + 20, "END", back, null);
    }

    private static void reset(ServerPlayer p) {
        ServerLevel level = p.serverLevel();
        level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(60)).forEach(Mob::discard);
        p.teleportTo(level, 0.5, p.getY(), 0.5, 0.0F, 2.0F);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (g != null) {
                g.moveTo(0.5 + i * 3, p.getY(), 6.5, 180.0F, 0.0F);
                g.setNoAi(true);
                g.setPersistenceRequired();
                level.addFreshEntity(g);
            }
        }
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
                LevelSettings settings = new LevelSettings("hakari_selftest", GameType.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("hakari_selftest", settings, new WorldOptions(1033L, false, false),
                        access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        tick++;
        if (tick == 80) {
            build();
            server.execute(() -> {
                ServerPlayer p = server.getPlayerList().getPlayers().get(0);
                ServerLevel level = p.serverLevel();
                level.setDayTime(6000);
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                Sorcerers.choose(p, Sorcerer.HAKARI);
                reset(p);
                // terrain the domain should swallow: pillars and a wall well inside its radius
                for (int x = -12; x <= 12; x += 6) {
                    for (int y = 0; y < 7; y++) {
                        level.setBlockAndUpdate(BlockPos.containing(x, p.getY() + y, -9), Blocks.STONE_BRICKS.defaultBlockState());
                        level.setBlockAndUpdate(BlockPos.containing(x + 0.5, p.getY() + y, 12), Blocks.OAK_LOG.defaultBlockState());
                    }
                }
                for (int x = -4; x <= 4; x++) {
                    for (int y = 0; y < 4; y++) {
                        level.setBlockAndUpdate(BlockPos.containing(x, p.getY() + y, 15), Blocks.BRICKS.defaultBlockState());
                    }
                }
                System.out.println("[selftest] Hakari chosen, arena built");
            });
        }
        while (next < SCRIPT.size() && SCRIPT.get(next).at() <= tick) {
            Step s = SCRIPT.get(next++);
            if (s.camera() != null && mc.options.getCameraType() != s.camera()) {
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
        new File(mc.gameDirectory, "screenshots/hakari").mkdirs();
        Screenshot.grab(mc.gameDirectory, "hakari/" + pendingShot + ".png", mc.getMainRenderTarget(), msg -> {});
        pendingShot = null;
    }
}
