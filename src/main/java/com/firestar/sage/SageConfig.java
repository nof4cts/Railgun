package com.firestar.sage;

import net.minecraftforge.common.ForgeConfigSpec;

public final class SageConfig {
    public static final ForgeConfigSpec SERVER_SPEC;
    public static final Server SERVER;
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        var s = new ForgeConfigSpec.Builder().configure(Server::new);
        SERVER = s.getLeft();
        SERVER_SPEC = s.getRight();
        var c = new ForgeConfigSpec.Builder().configure(Client::new);
        CLIENT = c.getLeft();
        CLIENT_SPEC = c.getRight();
    }

    private SageConfig() {}

    public static final class Server {
        public final ForgeConfigSpec.DoubleValue damage;
        public final ForgeConfigSpec.DoubleValue cooldowns;
        public final ForgeConfigSpec.BooleanValue terrain;
        public final ForgeConfigSpec.IntValue blocksPerTick;
        public final ForgeConfigSpec.BooleanValue flight;
        public final ForgeConfigSpec.BooleanValue invulnerableInCinematic;

        Server(ForgeConfigSpec.Builder b) {
            b.push("celestial_sage");
            damage = b.comment("Multiplier on every rite's damage.").defineInRange("damage", 1.0, 0.0, 20.0);
            cooldowns = b.comment("Multiplier on every cooldown (0 = none).").defineInRange("cooldowns", 1.0, 0.0, 10.0);
            terrain = b.comment("Whether the rites change the land (craters, trenches, erased blocks).").define("terrain", true);
            blocksPerTick = b.comment("Most blocks changed per tick by the rites.").defineInRange("blocksPerTick", 9000, 100, 200000);
            flight = b.comment("The Mantle grants flight.").define("flight", true);
            invulnerableInCinematic = b.comment("The sage can't be hurt while a cutscene plays.").define("invulnerableInCinematic", true);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.BooleanValue cutscenes;
        public final ForgeConfigSpec.DoubleValue shake;
        public final ForgeConfigSpec.DoubleValue flash;

        Client(ForgeConfigSpec.Builder b) {
            b.push("celestial_sage");
            cutscenes = b.comment("Play the rites' cutscenes.").define("cutscenes", true);
            shake = b.comment("Camera shake strength.").defineInRange("shake", 1.0, 0.0, 2.0);
            flash = b.comment("Flash strength (lower if bright flashes bother you).").defineInRange("flash", 1.0, 0.0, 1.0);
            b.pop();
        }
    }
}
