package com.shrine.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.Builder;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;
import net.minecraftforge.common.ForgeConfigSpec.IntValue;
import org.apache.commons.lang3.tuple.Pair;

public final class ShrineConfig {
   public static final ShrineConfig.Client CLIENT;
   public static final ForgeConfigSpec CLIENT_SPEC;
   public static final ShrineConfig.Server SERVER;
   public static final ForgeConfigSpec SERVER_SPEC;

   private ShrineConfig() {
   }

   static {
      Pair<ShrineConfig.Client, ForgeConfigSpec> c = new Builder().configure(ShrineConfig.Client::new);
      CLIENT = (ShrineConfig.Client)c.getLeft();
      CLIENT_SPEC = (ForgeConfigSpec)c.getRight();
      Pair<ShrineConfig.Server, ForgeConfigSpec> s = new Builder().configure(ShrineConfig.Server::new);
      SERVER = (ShrineConfig.Server)s.getLeft();
      SERVER_SPEC = (ForgeConfigSpec)s.getRight();
   }

   public static final class Client {
      public final DoubleValue bloom;
      public final DoubleValue aberration;
      public final DoubleValue filmGrain;
      public final DoubleValue cameraShake;
      public final DoubleValue flash;
      public final BooleanValue impactFrames;
      public final IntValue volumeSteps;

      Client(Builder b) {
         b.push("rendering");
         this.bloom = b.comment("Bloom strength.").defineInRange("bloom", 1.0, 0.0, 3.0);
         this.aberration = b.comment("Chromatic aberration strength.").defineInRange("aberration", 1.0, 0.0, 3.0);
         this.filmGrain = b.comment("Film grain strength.").defineInRange("filmGrain", 1.0, 0.0, 3.0);
         this.volumeSteps = b.comment("Raymarch steps for fire volumes (higher = finer flames, slower).").defineInRange("volumeSteps", 48, 16, 160);
         b.pop();
         b.push("comfort");
         this.cameraShake = b.comment("Camera shake strength (0 disables).").defineInRange("cameraShake", 1.0, 0.0, 2.0);
         this.flash = b.comment("Flash strength (lower if bright flashes bother you).").defineInRange("flash", 1.0, 0.0, 1.0);
         this.impactFrames = b.comment("Impact frames (stark stylised frames on big hits). Contains rapid flashing.").define("impactFrames", true);
         b.pop();
      }
   }

   public static final class Server {
      public final DoubleValue damage;
      public final BooleanValue cutBlocks;
      public final DoubleValue cooldownScale;
      public final IntValue dismantleLength;
      public final IntValue fugaRadius;
      public final IntValue fugaRange;
      public final IntValue domainRadius;
      public final IntValue domainSeconds;
      public final IntValue worldSlashLength;
      public final IntValue blocksPerTick;
      public final IntValue cutsceneRadius;
      public final BooleanValue opOnly;

      Server(Builder b) {
         b.push("technique");
         this.damage = b.comment("Damage of a direct hit (each move scales this: barrage slashes less, Fuga and World-Cutting Slash more). 16 = 8 hearts.")
            .defineInRange("hitDamage", 16.0, 1.0, 1.0E9);
         this.cutBlocks = b.comment("Whether the technique actually cuts the world (false = visuals and damage only).").define("cutBlocks", true);
         this.cooldownScale = b.comment("Multiplier on every cooldown (0 = no cooldowns).").defineInRange("cooldownScale", 1.0, 0.0, 10.0);
         this.dismantleLength = b.comment("Reach of a Dismantle slash, in blocks.").defineInRange("dismantleLength", 48, 4, 512);
         this.fugaRadius = b.comment("Radius of Open's (the fire arrow's) blast, in blocks: everything inside is burnt away into a lava crater.")
            .defineInRange("openRadius", 38, 4, 256);
         this.fugaRange = b.comment("How far the fire arrow flies, in blocks.").defineInRange("fugaRange", 220, 16, 1024);
         this.domainRadius = b.comment("Radius of the domain, in blocks (everything inside is cut; the ground is shredded in the middle part only).")
            .defineInRange("domainRange", 40, 8, 512);
         this.domainSeconds = b.comment("How long the domain lasts once open, in seconds.").defineInRange("domainSeconds", 14, 2, 120);
         this.worldSlashLength = b.comment("Length of the World-Cutting Slash, in blocks. It cuts from the sky down into the void.")
            .defineInRange("worldSlashLength", 1000, 16, 8192);
         this.blocksPerTick = b.comment("Maximum blocks changed per server tick by large cuts (performance budget).")
            .defineInRange("blocksPerTick", 16000, 100, 500000);
         this.cutsceneRadius = b.comment("Players within this many blocks of the caster also see the cutscenes.")
            .defineInRange("cutsceneRadius", 160, 0, 100000);
         this.opOnly = b.comment("Only operators can use the technique.").define("opOnly", false);
         b.pop();
      }
   }
}
