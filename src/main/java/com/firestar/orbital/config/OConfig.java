package com.firestar.orbital.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class OConfig {
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

    private OConfig() {}

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
            smoke = b.comment("Most steam and dust clouds alive at once (lower it on a slow computer).").defineInRange("smoke", 600, 100, 6000);
            atmosphere = b.comment("Tinted sky and haze near a strike.").define("atmosphere", true);
            b.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.IntValue cooldown;
        public final ForgeConfigSpec.IntValue range;
        public final ForgeConfigSpec.BooleanValue opsOnly;
        public final ForgeConfigSpec.BooleanValue destroyBlocks;
        public final ForgeConfigSpec.IntValue blocksPerTick;
        public final ForgeConfigSpec.BooleanValue callerSafe;
        public final ForgeConfigSpec.DoubleValue heliosRadius;
        public final ForgeConfigSpec.IntValue heliosDepth;
        public final ForgeConfigSpec.DoubleValue heliosSpeed;
        public final ForgeConfigSpec.DoubleValue heliosDamage;
        public final ForgeConfigSpec.DoubleValue antipodeRadius;
        public final ForgeConfigSpec.DoubleValue antipodeDamage;
        public final ForgeConfigSpec.BooleanValue blight;
        public final ForgeConfigSpec.IntValue blightMobCap;

        Server(ForgeConfigSpec.Builder b) {
            b.push("uplink");
            cooldown = b.comment("Seconds before the same cannon can fire again for the same player (creative ignores it).").defineInRange("cooldown", 30, 0, 3600);
            range = b.comment("How far away the uplink can mark a target, in blocks.").defineInRange("range", 512, 16, 2048);
            opsOnly = b.comment("Only operators can fire.").define("opsOnly", false);
            destroyBlocks = b.comment("Strikes change the terrain.").define("destroyBlocks", true);
            blocksPerTick = b.comment("Most blocks changed per tick across all strikes (lower it if the server lags).").defineInRange("blocksPerTick", 6000, 0, 200000);
            callerSafe = b.comment("The player who fired is never hurt by their own strike.").define("callerSafe", true);
            b.pop();
            b.push("helios");
            heliosRadius = b.comment("Radius of the lance, in blocks.").defineInRange("radius", 5.0, 1.0, 40.0);
            heliosDepth = b.comment("How deep the trench is cut below the surface.").defineInRange("depth", 24, 1, 384);
            heliosSpeed = b.comment("How fast the lance can follow your crosshair, blocks per tick.").defineInRange("speed", 1.1, 0.1, 8.0);
            heliosDamage = b.comment("Damage every other tick to anything inside the lance.").defineInRange("damage", 12.0, 0.0, 10000.0);
            b.pop();
            b.push("antipode");
            antipodeRadius = b.comment("Radius of the land that is exchanged, in blocks.").defineInRange("radius", 36.0, 6.0, 160.0);
            antipodeDamage = b.comment("Damage to everything in the zone when the worlds touch.").defineInRange("damage", 60.0, 0.0, 10000.0);
            blight = b.comment("After the exchange, the other world's flesh spreads outward from the rim with no end, and things crawl out of it.",
                    "Stop a spread in progress with /orbital_arsenal blight stop.").define("blight", true);
            blightRate = b.comment("How many edge columns try to spread each tick (higher = faster; 12 is a slow creep, the old default was 60).")
                    .defineInRange("blightSpread", 12, 1, 5000);
            blightMobCap = b.comment("Most blight creatures alive near each player (0 = none spawn).").defineInRange("blightMobCap", 10, 0, 100);
            b.pop();
        }
    }
}
