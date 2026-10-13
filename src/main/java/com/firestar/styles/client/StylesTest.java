package com.firestar.styles.client;

import com.firestar.styles.Style;
import com.firestar.styles.Styles;
import com.firestar.styles.StylesMod;
import java.io.File;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Development only (-Dfs.selftest=true), played by the build server: the switcher, then each style in turn (switch, use
 * a move), and Silver Instinct awakened and flying, each photographed.
 */
@Mod.EventBusSubscriber(modid = StylesMod.MODID, value = Dist.CLIENT)
public final class StylesTest {
    static final boolean ON = Boolean.getBoolean("fs.selftest");
    private static boolean requested, done;
    private static int ticks;
    private static int t0 = -1;
    private static float ZENITH_HP;

    private static final class Attackers {
        static final java.util.List<net.minecraft.world.entity.LivingEntity> ATTACKERS = new java.util.ArrayList<>();
    }

    private StylesTest() {}

    private static ServerPlayer player(MinecraftServer s) {
        return s.getPlayerList().getPlayers().get(0);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        new File(mc.gameDirectory, "screenshots/styles").mkdirs();
        Screenshot.grab(mc.gameDirectory, "styles/" + name + ".png", mc.getMainRenderTarget(), m -> {});
        StylesMod.LOGGER.info("[selftest] shot {}", name);
    }

    private static void golems(ServerPlayer p, double dist) {
        ServerLevel level = p.serverLevel();
        level.getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(80)).forEach(Mob::discard);
        p.connection.teleport(0.5, -60, 0.5, 0, 4);
        for (int i = -1; i <= 1; i++) {
            IronGolem g = EntityType.IRON_GOLEM.create(level);
            if (g != null) {
                g.moveTo(0.5 + i * 2.6, -60, 0.5 + dist + Math.abs(i) * 0.6, 180, 0);
                g.setNoAi(true);
                g.setPersistenceRequired();
                level.addFreshEntity(g);
            }
        }
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent e) {
        if (!ON || done || e.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!requested && mc.screen instanceof TitleScreen) {
                requested = true;
                LevelSettings settings = new LevelSettings("styles_selftest", GameType.CREATIVE, false, Difficulty.EASY, true, new GameRules(),
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("styles_selftest", settings, new WorldOptions(5L, false, false),
                        a -> a.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
            }
            return;
        }
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) {
            return;
        }
        ticks++;
        if (ticks == 80) {
            server.execute(() -> {
                ServerPlayer p = player(server);
                p.serverLevel().setDayTime(6000);
                p.serverLevel().getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
                p.serverLevel().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
                // survival, so flight comes from the awakening and not from creative mode
                p.setGameMode(GameType.SURVIVAL);
                p.getInventory().clearContent();
            });
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (ticks < 120) {
            return;
        }
        int t = ticks - 120;
        switch (t) {
            case 0 -> mc.setScreen(new StyleScreen());
            case 15 -> shot("00_switcher");
            case 20 -> {
                mc.setScreen(null);
                server.execute(() -> {
                    ServerPlayer p = player(server);
                    Styles.choose(p, Style.HEAVEN);
                    golems(p, 4);
                });
            }
            case 35 -> server.execute(() -> com.firestar.mih.server.Techniques.request(player(server), com.firestar.mih.common.Move.SUMMON));
            case 60 -> shot("01_heaven_summoned");
            case 64 -> server.execute(() -> com.firestar.mih.server.Techniques.request(player(server), com.firestar.mih.common.Move.BARRAGE));
            case 80 -> shot("02_heaven_barrage");
            case 130 -> server.execute(() -> {
                ServerPlayer p = player(server);
                p.setGameMode(GameType.CREATIVE);
                Styles.choose(p, Style.SWORDS);
                p.setGameMode(GameType.SURVIVAL);
                golems(p, 6);
                p.getInventory().selected = 0;
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(com.firestar.santoryu.registry.ModItems.WADO.get()));
            });
            case 150 -> shot("03_swords_ready");
            case 154 -> server.execute(() -> com.firestar.santoryu.server.Techniques.request(player(server), com.firestar.santoryu.common.Move.ONI_GIRI));
            case 168 -> shot("04_swords_cut");
            case 200 -> server.execute(() -> {
                ServerPlayer p = player(server);
                p.setGameMode(GameType.CREATIVE);
                Styles.choose(p, Style.INSTINCT);
                p.setGameMode(GameType.SURVIVAL);
                golems(p, 6);
                p.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
            case 210 -> server.execute(() -> com.firestar.silver.server.Techniques.request(player(server), com.firestar.silver.common.Move.AWAKEN_OMEN));
            case 360 -> {
                shot("05_instinct_awake");
                StylesMod.LOGGER.info("[selftest] mayfly={} form={}", mc.player.getAbilities().mayfly,
                        com.firestar.silver.client.ClientState.form(mc.player));
                mc.player.getAbilities().flying = true;
                mc.player.onUpdateAbilities();
                mc.player.setDeltaMovement(0, 1.2, 0);
            }
            case 380 -> shot("06_instinct_hover");
            default -> {
            }
        }
        if (t > 382 && t < 430) {
            // fly forward and climb
            Vec3 look = Vec3.directionFromRotation(-5, mc.player.getYRot());
            mc.player.setDeltaMovement(look.scale(1.4));
            mc.player.getAbilities().flying = true;
        }
        if (t == 400) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        }
        if (t == 412) {
            shot("07_instinct_fly_front");
        }
        if (t == 418) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        }
        if (t == 426) {
            shot("08_instinct_fly_back");
        }
        if (t == 436) {
            server.execute(() -> {
                ServerPlayer p = player(server);
                p.getAbilities().flying = false;
                p.onUpdateAbilities();
                golems(p, 30);
                p.connection.teleport(0.5, -60, 0.5, 0, 4);
                com.firestar.silver.server.Fighters.setForm(p, com.firestar.silver.server.Fighters.ZENITH);
                ZENITH_HP = p.getHealth();
                Attackers.ATTACKERS.clear();
            });
        }
        if (t >= 446 && t < 462 && t % 2 == 0) {
            int k = (t - 446) / 2;
            server.execute(() -> {
                ServerPlayer p = player(server);
                // eight attackers round him, one after another, from every side; the last shoots from twenty blocks
                double a = Math.toRadians(k * 45);
                double r = k == 7 ? 20 : 2.2;
                net.minecraft.world.entity.monster.Zombie z = EntityType.ZOMBIE.create(p.serverLevel());
                if (z != null) {
                    z.moveTo(p.getX() + Math.sin(a) * r, p.getY(), p.getZ() + Math.cos(a) * r, 0, 0);
                    z.setNoAi(true);
                    p.serverLevel().addFreshEntity(z);
                    Attackers.ATTACKERS.add(z);
                    if (k == 7) {
                        net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(p.serverLevel(), z);
                        p.hurt(p.damageSources().arrow(arrow, z), 6.0F);
                    } else {
                        p.hurt(p.damageSources().mobAttack(z), 6.0F);
                    }
                }
            });
        }
        if (t == 466) {
            server.execute(() -> {
                ServerPlayer p = player(server);
                long countered = Attackers.ATTACKERS.stream().filter(z -> z.getHealth() < z.getMaxHealth()).count();
                StylesMod.LOGGER.info("[selftest] zenith 360: health before={} after={} attackers countered={}/{}", ZENITH_HP, p.getHealth(),
                        countered, Attackers.ATTACKERS.size());
            });
        }
        if (t == 470) {
            shot("09a_zenith_counters");
        }
        if (t == 500) {
            server.execute(() -> {
                ServerPlayer p = player(server);
                Styles.choose(p, Style.NONE);
                StylesMod.LOGGER.info("[selftest] after leaving Instinct: mayfly={} flying={}", p.getAbilities().mayfly, p.getAbilities().flying);
            });
        }
        if (t == 530) {
            shot("09_none");
            done = true;
            StylesMod.LOGGER.info("[selftest] switcher test finished; Requiem Rose next");
            com.firestar.rose.client.RoseTest.begin();
        }
    }
}
