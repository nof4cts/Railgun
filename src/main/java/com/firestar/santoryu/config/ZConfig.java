package com.firestar.santoryu.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class ZConfig {
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    static {
        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        CLIENT = new Client(c);
        CLIENT_SPEC = c.build();
        ForgeConfigSpec.Builder s = new ForgeConfigSpec.Builder();
        SERVER = new Server(s);
        SERVER_SPEC = s.build();
    }

    private ZConfig() {}

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue cutscenes;
        public final ForgeConfigSpec.BooleanValue impactFrames;
        public final ForgeConfigSpec.DoubleValue shake;
        public final ForgeConfigSpec.DoubleValue flash;
        public final ForgeConfigSpec.DoubleValue bloom;
        public final ForgeConfigSpec.DoubleValue aberration;
        public final ForgeConfigSpec.DoubleValue grain;
        public final ForgeConfigSpec.BooleanValue titleCards;

        Client(ForgeConfigSpec.Builder b) {
            b.push("cinema");
            cutscenes = b.comment("Play the full-length cinematics for the big techniques.").define("cutscenes", true);
            titleCards = b.comment("Show the technique name cards (kanji + English).").define("titleCards", true);
            impactFrames = b.comment("Anime impact frames. They flash hard: turn off if flashing images bother you.").define("impactFrames", true);
            flash = b.comment("Strength of white flashes, 0 = none.").defineInRange("flash", 1.0, 0.0, 1.5);
            shake = b.comment("Camera shake strength.").defineInRange("shake", 1.0, 0.0, 2.0);
            bloom = b.comment("Glow strength of slashes and haki.").defineInRange("bloom", 1.0, 0.0, 2.0);
            aberration = b.comment("Chromatic aberration (colour fringing) on hits.").defineInRange("aberration", 1.0, 0.0, 2.0);
            grain = b.comment("Film grain during cinematics.").defineInRange("grain", 1.0, 0.0, 2.0);
            b.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.DoubleValue cooldowns;
        public final ForgeConfigSpec.BooleanValue cutBlocks;
        public final ForgeConfigSpec.IntValue maxBlocksPerTick;
        public final ForgeConfigSpec.BooleanValue invulnerableInCinematic;

        Server(ForgeConfigSpec.Builder b) {
            b.push("techniques");
            damage = b.comment("Multiplier on every technique's damage.").defineInRange("damage", 1.0, 0.0, 10.0);
            cooldowns = b.comment("Multiplier on every cooldown (0 = no cooldowns).").defineInRange("cooldowns", 1.0, 0.0, 10.0);
            cutBlocks = b.comment("Big slashes carve through terrain (also needs the mobGriefing gamerule).").define("cutBlocks", true);
            maxBlocksPerTick = b.comment("Cap on blocks a slash may cut in one tick.").defineInRange("maxBlocksPerTick", 600, 0, 20000);
            invulnerableInCinematic = b.comment("The swordsman can't be hurt while a cinematic technique is playing.").define("invulnerableInCinematic", true);
            b.pop();
        }
    }
}
