package com.firestar.mih.config;

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
        public final ForgeConfigSpec.IntValue galaxyRadius;
        public final ForgeConfigSpec.IntValue galaxyBlocks;
        public final ForgeConfigSpec.BooleanValue sunStreaks;

        Client(ForgeConfigSpec.Builder b) {
            b.push("cinema");
            cutscenes = b.comment("Play the full-length cinematics for the big moves.").define("cutscenes", true);
            titleCards = b.comment("Show the move name cards (kanji + English).").define("titleCards", true);
            impactFrames = b.comment("Anime impact frames. They flash hard: turn off if flashing images bother you.").define("impactFrames", true);
            flash = b.comment("Strength of white flashes, 0 = none.").defineInRange("flash", 1.0, 0.0, 1.5);
            shake = b.comment("Camera shake strength.").defineInRange("shake", 1.0, 0.0, 2.0);
            bloom = b.comment("Glow strength.").defineInRange("bloom", 1.0, 0.0, 2.0);
            aberration = b.comment("Chromatic aberration (colour fringing) on hits.").defineInRange("aberration", 1.0, 0.0, 2.0);
            grain = b.comment("Film grain during cinematics.").defineInRange("grain", 1.0, 0.0, 2.0);
            b.pop();
            b.push("universe");
            galaxyRadius = b.comment("Radius (blocks) of the world shown torn into the galaxy in the Universe Reset. Visual only.")
                    .defineInRange("galaxyRadius", 22, 6, 40);
            galaxyBlocks = b.comment("Most blocks drawn flying into the galaxy (lower it on a slow computer).").defineInRange("galaxyBlocks", 2600, 100, 12000);
            sunStreaks = b.comment("Draw the sun and moon smearing into streaks of light while time accelerates.").define("sunStreaks", true);
            b.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.DoubleValue cooldowns;
        public final ForgeConfigSpec.BooleanValue invulnerableInCinematic;
        public final ForgeConfigSpec.IntValue accelSeconds;
        public final ForgeConfigSpec.IntValue accelRadius;
        public final ForgeConfigSpec.BooleanValue universeReset;
        public final ForgeConfigSpec.BooleanValue speedUpWorld;

        Server(ForgeConfigSpec.Builder b) {
            b.push("stand");
            damage = b.comment("Multiplier on every move's damage.").defineInRange("damage", 1.0, 0.0, 10.0);
            cooldowns = b.comment("Multiplier on every cooldown (0 = no cooldowns).").defineInRange("cooldowns", 1.0, 0.0, 10.0);
            invulnerableInCinematic = b.comment("The Stand user can't be hurt while a cinematic move is playing.").define("invulnerableInCinematic", true);
            b.pop();
            b.push("timeAcceleration");
            accelSeconds = b.comment("How long Time Acceleration runs before the universe resets.").defineInRange("seconds", 36, 5, 600);
            accelRadius = b.comment("Players this close when time accelerates are trapped with you (blocks).").defineInRange("radius", 48, 4, 256);
            universeReset = b.comment("When the acceleration completes, the universe resets.").define("universeReset", true);
            speedUpWorld = b.comment("Non-living things speed up too: crops grow, items and arrows fly faster.").define("speedUpWorld", true);
            b.pop();
        }
    }
}
