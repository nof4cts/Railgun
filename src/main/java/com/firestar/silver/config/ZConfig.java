package com.firestar.silver.config;

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
        public final ForgeConfigSpec.IntValue auraDetail;

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
            b.push("aura");
            auraDetail = b.comment("How many flame tongues the aura has (lower it on a slow computer).").defineInRange("auraDetail", 18, 4, 40);
            b.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.DoubleValue cooldowns;
        public final ForgeConfigSpec.BooleanValue invulnerableInCinematic;
        public final ForgeConfigSpec.DoubleValue omenDodge;
        public final ForgeConfigSpec.DoubleValue zenithDodge;
        public final ForgeConfigSpec.BooleanValue counters;
        public final ForgeConfigSpec.IntValue zenithSeconds;
        public final ForgeConfigSpec.IntValue zenithCooldownSeconds;

        Server(ForgeConfigSpec.Builder b) {
            b.push("instinct");
            damage = b.comment("Multiplier on every move's damage.").defineInRange("damage", 1.0, 0.0, 10.0);
            cooldowns = b.comment("Multiplier on every cooldown (0 = no cooldowns).").defineInRange("cooldowns", 1.0, 0.0, 10.0);
            invulnerableInCinematic = b.comment("The fighter can't be hurt while a cinematic move is playing.").define("invulnerableInCinematic", true);
            omenDodge = b.comment("Chance the Omen form slips an attack on its own.").defineInRange("omenDodge", 0.35, 0.0, 1.0);
            zenithDodge = b.comment("Unused: the Zenith form now slips every attack, from every direction, and counters it.").defineInRange("zenithDodge", 0.7, 0.0, 1.0);
            counters = b.comment("In Zenith, a slipped attack from close by is answered with a counter.").define("counters", true);
            zenithSeconds = b.comment("How long Silver Zenith lasts before it falls back to Instinct Omen (0 = no limit). Creative mode has no limit.")
                    .defineInRange("zenithSeconds", 120, 0, 86400);
            zenithCooldownSeconds = b.comment("After Silver Zenith ends (time up, revert or death), how long before it can be reached again. Creative mode has no cooldown.")
                    .defineInRange("zenithCooldownSeconds", 600, 0, 86400);
            b.pop();
        }
    }
}
