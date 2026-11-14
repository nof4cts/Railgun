package com.aegis.command;

import net.minecraftforge.common.ForgeConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class AegisConfig {
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

    private AegisConfig() {}

    public static final class Common {
        public final ForgeConfigSpec.BooleanValue destroyBlocks;
        public final ForgeConfigSpec.BooleanValue weaponBlockDamage;
        public final ForgeConfigSpec.IntValue cooldownSeconds;
        public final ForgeConfigSpec.IntValue maxRange;
        public final ForgeConfigSpec.IntValue approachDistance;

        Common(ForgeConfigSpec.Builder b) {
            b.push("airstrike");
            destroyBlocks = b.comment("If true, airstrike ordnance breaks blocks. Off by default: strikes are visual + entity damage only.")
                    .define("destroyBlocks", false);
            cooldownSeconds = b.comment("Cooldown between strikes per player, in seconds.")
                    .defineInRange("cooldownSeconds", 20, 0, 3600);
            maxRange = b.comment("Maximum target distance from the laptop, in blocks.")
                    .defineInRange("maxRange", 96, 16, 112);
            approachDistance = b.comment("How far out the aircraft spawns before reaching the target, in blocks.")
                    .defineInRange("approachDistance", 130, 60, 200);
            b.pop();
            b.push("weapons");
            weaponBlockDamage = b.comment("If true, the TITAN-X shell breaks blocks.")
                    .define("weaponBlockDamage", false);
            b.pop();
        }
    }

    public static final class Client {
        public final ForgeConfigSpec.IntValue volume;
        public final ForgeConfigSpec.BooleanValue muted;
        public final ForgeConfigSpec.BooleanValue uiSounds;
        public final ForgeConfigSpec.IntValue accent;
        public final ForgeConfigSpec.BooleanValue scanlines;
        public final ForgeConfigSpec.BooleanValue noise;
        public final ForgeConfigSpec.BooleanValue fullscreenTerminal;
        public final ForgeConfigSpec.BooleanValue bootSequence;
        public final ForgeConfigSpec.BooleanValue cinematicCamera;
        public final ForgeConfigSpec.BooleanValue letterbox;
        public final ForgeConfigSpec.BooleanValue subtitles;
        public final ForgeConfigSpec.IntValue shake;

        Client(ForgeConfigSpec.Builder b) {
            b.push("audio");
            volume = b.defineInRange("volume", 80, 0, 100);
            muted = b.define("muted", false);
            uiSounds = b.define("uiSounds", true);
            b.pop();
            b.push("display");
            accent = b.comment("0 = green, 1 = cyan, 2 = amber").defineInRange("accent", 0, 0, 2);
            scanlines = b.define("scanlines", true);
            noise = b.define("interfaceNoise", true);
            fullscreenTerminal = b.define("fullscreenTerminal", false);
            bootSequence = b.define("bootSequence", true);
            b.pop();
            b.push("cinematic");
            cinematicCamera = b.define("cinematicCamera", true);
            letterbox = b.define("letterbox", true);
            subtitles = b.define("subtitles", true);
            shake = b.defineInRange("screenShake", 70, 0, 100);
            b.pop();
        }
    }
}
