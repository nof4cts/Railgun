package com.gojo.mod;

import com.gojo.mod.cine.Cine;
import com.gojo.mod.fx.Fx;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class Techniques {
   public static final int BLUE = 4033535;
   public static final int BLUE_CORE = 14546175;
   public static final int RED = 16722731;
   public static final int RED_CORE = 16767392;
   public static final int EMBER = 16742938;
   public static final int PURPLE = 10636287;
   public static final int WHITE = 16777215;
   public static final int VOID_GLOW = 10475775;
   public static final int PURPLE_CHARGE_TICKS = 40;
   private static final double BLUE_RANGE = 12.0;
   private static final double BLUE_RADIUS = 10.0;
   private static final double RED_RANGE = 30.0;
   private static final double RED_RADIUS = 8.0;
   private static final int PURPLE_RANGE = 48;
   private static final int PURPLE_RADIUS = 2;
   private static final double VOID_RADIUS = 20.0;
   private static final double TELEPORT_RANGE = 40.0;
   private static final double SIX_EYES_RADIUS = 48.0;
   private static final double CURTAIN_RADIUS = 32.0;
   private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

   private Techniques() {
   }

   public static boolean infinity(ServerLevel level, ServerPlayer player) {
      boolean active = !CursedEnergy.isInfinity(player);
      CursedEnergy.setInfinity(player, active);
      player.displayClientMessage(
         Component.literal(active ? "Infinity: ON" : "Infinity: OFF").withStyle(active ? ChatFormatting.AQUA : ChatFormatting.GRAY), true
      );
      playSound(level, player.position(), active ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 1.5F);
      com.gojo.rework.server.GRites.infinity(player, active);

      return true;
   }

   public static Vec3 fingertip(ServerPlayer p) {
      Vec3 look = p.getLookAngle();
      Vec3 right = look.cross(UP).lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : look.cross(UP).normalize();
      return p.getEyePosition().add(look.scale(0.7)).add(right.scale(0.38)).add(0.0, -0.3, 0.0);
   }

   public static boolean blue(ServerLevel level, ServerPlayer player) {
      Vec3 center = rayEnd(level, player, 12.0);
      Cine.shoot(level, player, (byte)0, fingertip(player), center);
      return true;
   }

   public static void blueImpact(ServerLevel level, ServerPlayer player, Vec3 center) {
      Fx.shake(level, center, 0.5F, 809339903, 10);
      playSound(level, center, SoundEvents.WARDEN_SONIC_BOOM, 1.6F);
      playSound(level, center, SoundEvents.BEACON_POWER_SELECT, 0.6F);
      playSound(level, center, SoundEvents.PORTAL_TRIGGER, 1.8F);
      DamageSource source = level.damageSources().indirectMagic(player, player);

      for (LivingEntity target : livingAround(level, player, center, 10.0)) {
         Vec3 body = target.position().add(0.0, (double)target.getBbHeight() / 2.0, 0.0);
         Vec3 pull = center.subtract(body);
         double distance = pull.length();
         if (!(distance > 10.0)) {
            if (distance > 0.5) {
               target.setDeltaMovement(target.getDeltaMovement().scale(0.2).add(pull.normalize().scale(1.6)));
               target.hurtMarked = true;
               Fx.trail(level, body, center, 4033535);
            }

            if (distance < 3.0) {
               target.hurt(source, 14.0F);
            }
         }
      }
   }

   public static boolean red(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.RED);
   }

   public static boolean maxBlue(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.MAX_BLUE);
   }

   public static boolean maxRed(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.MAX_RED);
   }

   public static boolean imaginaryPurple(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.IMAGINARY);
   }

   public static boolean hollowNuke(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.NUKE);
   }

   public static boolean domain02(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.DOMAIN_02);
   }

   public static void redImpact(ServerLevel level, ServerPlayer player, Vec3 center) {
      Fx.shake(level, center, 1.6F, 1358913600, 14);
      level.explode(player, center.x, center.y, center.z, 3.5F, ExplosionInteraction.BLOCK);
      DamageSource source = level.damageSources().indirectMagic(player, player);

      for (LivingEntity target : livingAround(level, player, center, 8.0)) {
         Vec3 away = target.position().subtract(center);
         Vec3 flat = new Vec3(away.x, 0.0, away.z);
         Vec3 dir = flat.lengthSqr() < 1.0E-4 ? player.getLookAngle() : flat.normalize();
         target.setDeltaMovement(dir.x * 2.8, 0.9, dir.z * 2.8);
         target.hurtMarked = true;
         if (away.length() < 6.0) {
            target.hurt(source, 18.0F);
         }
      }

      playSound(level, center, SoundEvents.GENERIC_EXPLODE, 0.7F);
      level.playSound(null, center.x, center.y, center.z, (SoundEvent)Cine.SND_CRACK.get(), SoundSource.PLAYERS, 1.6F, 0.9F);
   }

   public static boolean purple(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.PURPLE);
   }

   public static boolean purple200(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.PURPLE_200);
   }

   private static Vec3 flatLook(ServerPlayer p) {
      Vec3 l = p.getLookAngle();
      return new Vec3(l.x, Math.max(-0.25, Math.min(0.35, l.y)), l.z).normalize();
   }

   public static void purpleChargeTick(ServerLevel level, ServerPlayer player, int ticksLeft) {
      int elapsed = 40 - ticksLeft;
      if (elapsed % 4 == 0) {
         float progress = (float)elapsed / 40.0F;
         level.playSound(
            null,
            player.getX(),
            player.getY(),
            player.getZ(),
            SoundEvents.AMETHYST_BLOCK_RESONATE,
            SoundSource.PLAYERS,
            0.5F + progress,
            0.5F + progress * 1.5F
         );
      }
   }

   public static void firePurple(ServerLevel level, ServerPlayer player) {
      Vec3 start = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Set<LivingEntity> hit = new HashSet<>();
      MutableBlockPos cursor = new MutableBlockPos();
      int radiusSq = 5;
      Vec3 right = look.cross(UP).lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : look.cross(UP).normalize();
      Vec3 muzzle = start.add(look.scale(1.4)).add(right.scale(0.45)).add(0.0, -0.4, 0.0);
      Fx.beam(level, muzzle, start.add(look.scale(48.0)), 2.8F, 10636287, 16777215, 26);
      Fx.shockwave(level, start.add(look.scale(3.0)), look, 6.0F, 10636287, 16);
      Fx.burst(level, start.add(look.scale(4.0)), 1.0F, 16777215, 10636287, 60, (byte)0);
      Fx.shake(level, player.position(), 2.6F, -1863790337, 20);
      playSound(level, player.position(), SoundEvents.END_PORTAL_SPAWN, 1.4F);
      playSound(level, player.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.6F);
      player.displayClientMessage(Component.literal("Hollow Purple").withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD}), true);

      for (int step = 3; step <= 48; step++) {
         Vec3 point = start.add(look.scale((double)step));
         if (step % 8 == 0) {
            Fx.burst(level, point, 0.5F, 10636287, 5251232, 14, (byte)1);
         }

         BlockPos center = BlockPos.containing(point);

         for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
               for (int dz = -2; dz <= 2; dz++) {
                  if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                     cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                     BlockState state = level.getBlockState(cursor);
                     if (!state.isAir() && state.getDestroySpeed(level, cursor) >= 0.0F) {
                        level.setBlock(cursor, Blocks.AIR.defaultBlockState(), 3);
                     }
                  }
               }
            }
         }

         hit.addAll(livingAround(level, player, point, 3.0));
      }

      DamageSource source = level.damageSources().indirectMagic(player, player);

      for (LivingEntity target : hit) {
         target.hurt(source, 1000.0F);
      }
   }

   public static boolean unlimitedVoid(ServerLevel level, ServerPlayer player) {
      return com.gojo.rework.server.GRites.ask(player, com.gojo.rework.common.GMove.DOMAIN);
   }

   public static boolean unlimitedVoidOld(ServerLevel level, ServerPlayer player) {
      Vec3 center = player.position();
      Fx.domain(level, center.add(0.0, 1.0, 0.0), 20.0F, 200, (byte)0, 10475775);
      Fx.shake(level, center, 0.8F, -1325400065, 12);
      playSound(level, center, SoundEvents.END_PORTAL_SPAWN, 0.6F);
      List<LivingEntity> targets = livingAround(level, player, center, 20.0);

      for (LivingEntity target : targets) {
         target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 9));
         target.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 9));
         target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 4));
         target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200, 0));
         target.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 200, 0));
      }

      player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, 1));
      player.displayClientMessage(
         Component.literal("Domain Expansion: Unlimited Void (" + targets.size() + " trapped)")
            .withStyle(new ChatFormatting[]{ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD}),
         true
      );
      return true;
   }

   public static boolean teleport(ServerLevel level, ServerPlayer player) {
      Vec3 start = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 end = start.add(look.scale(40.0));
      BlockHitResult hit = level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, player));
      Vec3 eyeTarget = hit.getType() == Type.MISS ? end : hit.getLocation().subtract(look.scale(0.6));
      Vec3 feet = new Vec3(eyeTarget.x, eyeTarget.y - (double)player.getEyeHeight(), eyeTarget.z);

      for (int i = 0; i < 10 && !fits(level, player, feet); i++) {
         feet = feet.subtract(look.scale(0.5));
      }

      if (!fits(level, player, feet)) {
         player.displayClientMessage(Component.literal("No room to teleport").withStyle(ChatFormatting.RED), true);
         return false;
      } else {
         Vec3 from = player.position().add(0.0, 1.0, 0.0);
         Vec3 to = feet.add(0.0, 1.0, 0.0);
         Fx.burst(level, from, 0.5F, 14546175, 4033535, 30, (byte)0);
         Fx.trail(level, from, to, 4033535);
         player.connection.teleport(feet.x, feet.y, feet.z, player.getYRot(), player.getXRot());
         player.fallDistance = 0.0F;
         Fx.burst(level, to, 0.5F, 14546175, 4033535, 30, (byte)0);
         Fx.shimmer(level, to, look, 1.6F, 4033535, 12, (byte)1, -1);
         playSound(level, feet, SoundEvents.ENDERMAN_TELEPORT, 1.2F);
         return true;
      }
   }

   public static boolean reverseCursedTechnique(ServerLevel level, ServerPlayer player) {
      player.heal(player.getMaxHealth());
      player.getFoodData().setFoodLevel(20);
      player.getFoodData().setSaturation(20.0F);
      player.clearFire();

      for (MobEffectInstance effect : new ArrayList<>(player.getActiveEffects())) {
         if (effect.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            player.removeEffect(effect.getEffect());
         }
      }

      player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 1));
      Vec3 pos = player.position();
      Fx.burst(level, pos.add(0.0, 1.0, 0.0), 0.15F, 8191920, 16777215, 50, (byte)2);
      Fx.shockwave(level, pos.add(0.0, 0.1, 0.0), UP, 2.5F, 8191920, 14);
      playSound(level, pos, SoundEvents.PLAYER_LEVELUP, 1.4F);
      player.displayClientMessage(Component.literal("Reverse Cursed Technique").withStyle(ChatFormatting.GREEN), true);
      return true;
   }

   public static boolean sixEyes(ServerLevel level, ServerPlayer player) {
      List<LivingEntity> sensed = livingAround(level, player, player.position(), 48.0);

      for (LivingEntity target : sensed) {
         target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 300, 0, false, false));
      }

      player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 900, 0, false, false));
      Vec3 eye = player.getEyePosition();
      Fx.shockwave(level, player.position().add(0.0, 0.2, 0.0), UP, 48.0F, 4033535, 30);
      Fx.shimmer(level, eye, UP, 1.3F, 14546175, 30, (byte)0, player.getId());
      Fx.orb(level, eye, 0.25F, 4033535, 16777215, 14, (byte)1, player.getId());
      playSound(level, player.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F);
      player.displayClientMessage(Component.literal("Six Eyes: " + sensed.size() + " presences sensed").withStyle(ChatFormatting.AQUA), true);
      return true;
   }

   public static boolean curtain(ServerLevel level, ServerPlayer player) {
      level.setWeatherParameters(0, 6000, true, true);

      for (LivingEntity target : livingAround(level, player, player.position(), 32.0)) {
         target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 300, 0));
      }

      player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600, 0, false, false));
      Vec3 pos = player.position();
      level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.x, pos.y + 6.0, pos.z, 300, 10.0, 2.0, 10.0, 0.0);
      Fx.shake(level, pos, 0.4F, -1879048192, 16);
      playSound(level, pos, SoundEvents.WITHER_SPAWN, 0.5F);
      player.displayClientMessage(
         Component.literal("Emerge from the darkness, blacker than black...").withStyle(new ChatFormatting[]{ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC}),
         true
      );
      return true;
   }

   private static Vec3 rayEnd(ServerLevel level, ServerPlayer player, double range) {
      Vec3 start = player.getEyePosition();
      Vec3 end = start.add(player.getLookAngle().scale(range));
      BlockHitResult hit = level.clip(new ClipContext(start, end, Block.COLLIDER, Fluid.NONE, player));
      return hit.getType() == Type.MISS ? end : hit.getLocation();
   }

   private static List<LivingEntity> livingAround(ServerLevel level, ServerPlayer caster, Vec3 center, double radius) {
      return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), entity -> entity != caster && entity.isAlive());
   }

   private static boolean fits(ServerLevel level, ServerPlayer player, Vec3 feet) {
      AABB box = player.getBoundingBox().move(feet.subtract(player.position()));
      return level.noCollision(player, box);
   }

   private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float pitch) {
      level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, 1.0F, pitch);
   }
}
