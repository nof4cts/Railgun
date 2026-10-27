package com.abomination.horror.config;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class AbomConfig {
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final Common COMMON;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        Pair<Common, ForgeConfigSpec> c = new ForgeConfigSpec.Builder().configure(Common::new);
        COMMON = c.getLeft();
        COMMON_SPEC = c.getRight();
        Pair<Client, ForgeConfigSpec> cl = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = cl.getLeft();
        CLIENT_SPEC = cl.getRight();
    }

    private AbomConfig() {}

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue finisherEnabled;
        public final ForgeConfigSpec.BooleanValue naturalSpawning;
        public final ForgeConfigSpec.BooleanValue blockBreaking;
        public final ForgeConfigSpec.DoubleValue maxBreakHardness;
        public final ForgeConfigSpec.BooleanValue corruption;
        public final ForgeConfigSpec.BooleanValue jumpscares;
        public final ForgeConfigSpec.DoubleValue healthMultiplier;
        public final ForgeConfigSpec.DoubleValue damageMultiplier;

        Common(ForgeConfigSpec.Builder b) {
            b.push("abomination");
            finisherEnabled = b.comment("Play the head-and-spine-rip cinematic when The Abomination kills a player. If false, players die normally.")
                    .define("finisherEnabled", true);
            naturalSpawning = b.comment("Allow The Abomination to spawn naturally in the dark (max one per 128 blocks).")
                    .define("naturalSpawning", true);
            blockBreaking = b.comment("Allow it to tear through blocks while hunting (also respects the mobGriefing gamerule).")
                    .define("blockBreaking", true);
            maxBreakHardness = b.comment("Hardest block it can break. Stone = 1.5, iron block = 5, obsidian = 50.")
                    .defineInRange("maxBreakHardness", 3.0D, 0.0D, 50.0D);
            corruption = b.comment("Let it grow temporary flesh corruption around its lairs and arenas.")
                    .define("corruption", true);
            jumpscares = b.comment("Allow Nightmare-phase jumpscare teleports.")
                    .define("jumpscares", true);
            healthMultiplier = b.defineInRange("healthMultiplier", 1.0D, 0.1D, 20.0D);
            damageMultiplier = b.defineInRange("damageMultiplier", 1.0D, 0.1D, 20.0D);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue cinematicCamera;
        public final ForgeConfigSpec.BooleanValue screenEffects;
        public final ForgeConfigSpec.BooleanValue cameraShake;
        public final ForgeConfigSpec.BooleanValue dynamicMusic;
        public final ForgeConfigSpec.IntValue goreLevel;

        Client(ForgeConfigSpec.Builder b) {
            b.push("abomination");
            cinematicCamera = b.comment("Let the finisher take over your camera (close-up, third-person rip shot). Gore is still shown when false.")
                    .define("cinematicCamera", true);
            screenEffects = b.comment("Vignettes, chromatic fringing, static, flicker and flashes.")
                    .define("screenEffects", true);
            cameraShake = b.define("cameraShake", true);
            dynamicMusic = b.comment("Escalating music while it stalks and hunts you.")
                    .define("dynamicMusic", true);
            goreLevel = b.comment("0 = no blood particles, 1 = light, 2 = heavy, 3 = absurd.")
                    .defineInRange("goreLevel", 2, 0, 3);
            b.pop();
        }
    }
}
