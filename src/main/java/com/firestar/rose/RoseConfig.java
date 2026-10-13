package com.firestar.rose;

import net.minecraftforge.common.ForgeConfigSpec;

/** Requiem Rose settings: requiem-client.toml and requiem-server.toml. */
public final class RoseConfig {
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

    private RoseConfig() {}

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue cutscenes;
        public final ForgeConfigSpec.BooleanValue impactFrames;
        public final ForgeConfigSpec.DoubleValue bloom;
        public final ForgeConfigSpec.DoubleValue shake;
        public final ForgeConfigSpec.DoubleValue flash;
        public final ForgeConfigSpec.BooleanValue distortion;
        public final ForgeConfigSpec.IntValue embers;

        Client(ForgeConfigSpec.Builder b) {
            b.push("requiem");
            cutscenes = b.comment("Play the cutscenes of the big techniques.").define("cutscenes", true);
            impactFrames = b.comment("Impact frames. They flash hard: turn off if flashing images bother you.").define("impactFrames", true);
            bloom = b.comment("Strength of the glow round the energy.").defineInRange("bloom", 1.0, 0.0, 2.0);
            shake = b.comment("Camera shake.").defineInRange("shake", 1.0, 0.0, 2.0);
            flash = b.comment("Strength of white flashes, 0 = none.").defineInRange("flash", 1.0, 0.0, 1.5);
            distortion = b.comment("Heat-haze and space bending round slashes and the rift.").define("distortion", true);
            embers = b.comment("How many embers can be alive at once (lower it on a slow computer).").defineInRange("embers", 1600, 100, 6000);
            b.pop();
        }
    }

    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.DoubleValue cooldowns;
        public final ForgeConfigSpec.BooleanValue invulnerableInCinematic;
        public final ForgeConfigSpec.BooleanValue flight;

        Server(ForgeConfigSpec.Builder b) {
            b.push("requiem");
            damage = b.comment("Multiplier on every technique's damage.").defineInRange("damage", 1.0, 0.0, 10.0);
            cooldowns = b.comment("Multiplier on every cooldown (0 = none).").defineInRange("cooldowns", 1.0, 0.0, 10.0);
            invulnerableInCinematic = b.comment("The reaper can't be hurt while a cutscene technique plays.").define("invulnerableInCinematic", true);
            flight = b.comment("Ascended, the reaper can fly (double-jump).").define("flight", true);
            b.pop();
        }
    }
}
