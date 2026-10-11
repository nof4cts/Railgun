package com.firestar.worldbreaker.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class WConfig {
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

    private WConfig() {}

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue cutscenes;
        public final ForgeConfigSpec.BooleanValue impactFrames;
        public final ForgeConfigSpec.DoubleValue shake;
        public final ForgeConfigSpec.DoubleValue flash;
        public final ForgeConfigSpec.DoubleValue bloom;
        public final ForgeConfigSpec.DoubleValue aberration;
        public final ForgeConfigSpec.DoubleValue grain;
        public final ForgeConfigSpec.BooleanValue titleCards;
        public final ForgeConfigSpec.IntValue smoke;
        public final ForgeConfigSpec.BooleanValue atmosphere;

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
            b.push("world");
            smoke = b.comment("Most steam and dust clouds alive at once (lower it on a slow computer).").defineInRange("smoke", 900, 100, 6000);
            atmosphere = b.comment("Rust-red sky, haze and tremors while a march is on.").define("atmosphere", true);
            b.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.IntValue giants;
        public final ForgeConfigSpec.IntValue life;
        public final ForgeConfigSpec.IntValue cooldown;
        public final ForgeConfigSpec.BooleanValue opsOnly;
        public final ForgeConfigSpec.BooleanValue destroyBlocks;
        public final ForgeConfigSpec.IntValue blocksPerTick;
        public final ForgeConfigSpec.IntValue debris;
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.BooleanValue callerSafe;

        Server(ForgeConfigSpec.Builder b) {
            b.push("march");
            giants = b.comment("How many giants march.").defineInRange("giants", 25, 1, 60);
            life = b.comment("Ticks the march lasts (20 ticks = 1 second).").defineInRange("life", 900, 320, 6000);
            cooldown = b.comment("Ticks before the same player can call another march.").defineInRange("cooldown", 2400, 0, 72000);
            opsOnly = b.comment("Only operators can call the march.").define("opsOnly", false);
            destroyBlocks = b.comment("The giants flatten the terrain they walk through.").define("destroyBlocks", true);
            blocksPerTick = b.comment("Most blocks destroyed per tick across all giants (lower it if the server lags).").defineInRange("blocksPerTick", 2500, 0, 100000);
            debris = b.comment("Real flying blocks thrown up per footfall.").defineInRange("debris", 1, 0, 20);
            damage = b.comment("Multiplier on crushing and shockwave damage.").defineInRange("damage", 1.0, 0.0, 10.0);
            callerSafe = b.comment("The player who called the march is never hurt by it.").define("callerSafe", true);
            b.pop();
        }
    }
}
