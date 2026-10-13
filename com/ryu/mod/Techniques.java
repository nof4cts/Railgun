package com.ryu.mod;

import com.ryu.mod.fx.CinePacket;
import com.ryu.mod.fx.Fx;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public final class Techniques {
   public static final int CYAN = 58879;
   public static final int CYAN_CORE = 14745599;
   public static final int CYAN_DEEP = 30634;
   private static final double GRANITE_RANGE = 64.0;
   private static final double SHOT_RANGE = 32.0;
   private static final double REDIRECT_RANGE = 40.0;
   public static final double DOMAIN_RADIUS = 20.0;
   public static final int DOMAIN_DURATION = 200;
   public static final int DOMAIN_BUILD = 40;
   public static final int SKYFALL_DELAY = 200;
   private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);
   public static final int GRANITE_CHARGE = 30;

   private Techniques() {
   }

   public static boolean graniteBlast(ServerLevel level, ServerPlayer player) {
      CinePacket.send(level, player.position(), 0, player.getId());
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 34, 9, false, false));
      player.addEffect(new MobEffectInstance(MobEffects.JUMP, 34, 128, false, false));
      playSound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 0.6F);
      playSound(level, player.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.0F);

      for (int i = 0; i < 30; i += 3) {
         int k = i;
         RyuTasks.after(i + 1, () -> {
            if (player.isAlive()) {
               Vec3 hair = hairPosition(player);
               float grow = 0.2F + 0.5F * (float)k / 30.0F;
               Fx.orb(level, hair.add(0.0, (double)grow * 0.6, 0.0), grow, 58879, 14745599, 5, (byte)2, -1);
               Fx.arcs(level, hair, 0.6F + grow, 58879, 3, 5);
               if (k % 9 == 0) {
                  Fx.shockwave(level, player.position().add(0.0, 0.1, 0.0), UP, 2.0F + grow * 3.0F, 58879, 8);
               }
            }
         });
      }

      RyuTasks.after(30, () -> {
         if (player.isAlive() && player.level() == level) {
            releaseGranite(level, player);
         }
      });
      return true;
   }

   private static void releaseGranite(ServerLevel level, ServerPlayer player) {
      Vec3 look = player.getLookAngle();
      Vec3 hair = hairPosition(player);
      playSound(level, player.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.7F);
      playSound(level, player.position(), SoundEvents.GENERIC_EXPLODE, 0.6F);
      Vec3 end = fireBeam(level, player, hair.add(look.scale(1.5)), look, 80.0, 45.0F, 3.0, 3);
      Fx.beam(level, hair, end, 3.0F, 58879, 14745599, 26);
      Fx.beam(level, hair, end, 1.2F, 14745599, 16777215, 30);
      Fx.shockwave(level, end, UP, 11.0F, 58879, 20);
      Fx.shockwave(level, hair.add(look.scale(3.0)), look, 5.0F, 14745599, 12);
      Fx.shockwave(level, hair.add(look.scale(8.0)), look, 3.5F, 58879, 14);
      Fx.burst(level, end, 0.9F, 14745599, 58879, 100, (byte)0);
      Fx.burst(level, end, 0.5F, 58879, 30634, 30, (byte)1);
      Fx.arcs(level, end, 3.0F, 58879, 6, 14);
      Fx.shake(level, player.position(), 1.8F, 1887502335, 16);
      level.explode(player, end.x, end.y, end.z, 4.0F, ExplosionInteraction.BLOCK);
      player.setDeltaMovement(player.getDeltaMovement().add(look.scale(-0.6)));
      player.hurtMarked = true;
      player.displayClientMessage(Component.literal("GRANITE BLAST").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}), true);
   }

   public static boolean hairCannon(ServerLevel level, ServerPlayer player) {
      playSound(level, player.position(), SoundEvents.SHULKER_SHOOT, 1.6F);
      playSound(level, player.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 1.4F);
      shot(level, player, player.getLookAngle(), 8.0F, 0.9);
      Vec3 hair = hairPosition(player);
      Fx.shockwave(level, hair.add(player.getLookAngle().scale(0.6)), player.getLookAngle(), 1.2F, 14745599, 6);
      return true;
   }

   public static boolean beamVolley(ServerLevel level, ServerPlayer player) {
      playSound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 1.8F);
      int n = 0;

      for (int angle = -16; angle <= 16; angle += 8) {
         int a = angle;
         RyuTasks.after(1 + n * 2, () -> {
            if (player.isAlive()) {
               Vec3 look = player.getLookAngle();
               playSound(level, player.position(), SoundEvents.SHULKER_SHOOT, 1.1F + (float)a * 0.01F);
               shot(level, player, look.yRot((float)Math.toRadians((double)a)), 10.0F, 1.1);
            }
         });
         n++;
      }

      return true;
   }

   public static boolean redirectBlast(ServerLevel level, ServerPlayer player) {
      Vec3 start = hairPosition(player);
      Vec3 look = player.getLookAngle();
      LivingEntity target = null;
      double bestScore = 0.7;

      for (LivingEntity candidate : livingAround(level, player, start, 40.0)) {
         Vec3 toTarget = candidate.getBoundingBox().getCenter().subtract(start);
         double distance = toTarget.length();
         if (!(distance < 1.0) && !(distance > 40.0)) {
            double score = toTarget.normalize().dot(look);
            if (score > bestScore) {
               bestScore = score;
               target = candidate;
            }
         }
      }

      if (target == null) {
         player.displayClientMessage(Component.literal("No target to redirect toward").withStyle(ChatFormatting.RED), true);
         return false;
      } else {
         Vec3 end = target.getBoundingBox().getCenter();
         Vec3 control = start.add(look.scale(start.distanceTo(end) * 0.7));
         int segments = 10;
         Vec3 previous = start;

         for (int i = 1; i <= segments; i++) {
            double t = (double)i / (double)segments;
            double u = 1.0 - t;
            Vec3 point = start.scale(u * u).add(control.scale(2.0 * u * t)).add(end.scale(t * t));
            Fx.beam(level, previous, point, 0.45F, 58879, 14745599, 10);
            previous = point;
         }

         target.hurt(source(level, player), 18.0F);
         Fx.burst(level, end, 0.6F, 14745599, 58879, 40, (byte)0);
         Fx.arcs(level, end, 1.8F, 58879, 4, 8);
         playSound(level, player.position(), SoundEvents.SHULKER_SHOOT, 0.9F);
         playSound(level, end, SoundEvents.GENERIC_EXPLODE, 1.6F);
         return true;
      }
   }

   public static boolean domainExpansion(ServerLevel level, ServerPlayer player) {
      CinePacket.send(level, player.position(), 1, player.getId());
      CursedEnergy.setDomainTicks(player, 240);
      Vec3 center = player.position();
      player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 9, false, false));
      playSound(level, center, SoundEvents.BEACON_POWER_SELECT, 0.5F);
      playSound(level, center, SoundEvents.WARDEN_SONIC_CHARGE, 0.7F);
      Fx.beam(level, hairPosition(player), hairPosition(player).add(0.0, 70.0, 0.0), 1.4F, 58879, 14745599, 24);

      for (int i = 0; i < 12; i++) {
         int k = i;
         RyuTasks.after(10 + i * 2, () -> {
            double a = (Math.PI * 2) * (double)k / 12.0 + (double)player.getYRot() * Math.PI / 180.0;
            double fx = center.x + Math.cos(a) * 20.0;
            double fz = center.z + Math.sin(a) * 20.0;
            double gy = (double)level.getHeight(Types.MOTION_BLOCKING, (int)Math.floor(fx), (int)Math.floor(fz));
            Vec3 foot = new Vec3(fx, Math.min(gy, center.y + 8.0), fz);
            Fx.beam(level, foot.add(0.0, 60.0, 0.0), foot, 1.1F, 58879, 14745599, 30);
            Fx.shockwave(level, foot.add(0.0, 0.2, 0.0), UP, 3.0F, 58879, 10);
            Fx.burst(level, foot, 0.5F, 14745599, 58879, 16, (byte)0);
            playSound(level, foot, SoundEvents.SHULKER_SHOOT, 0.6F + (float)k * 0.04F);
         });
      }

      RyuTasks.after(40, () -> {
         Fx.domain(level, center.add(0.0, 1.0, 0.0), 20.0F, 200, (byte)1, 58879);
         Fx.shockwave(level, center.add(0.0, 0.2, 0.0), UP, 20.0F, 58879, 20);
         Fx.shockwave(level, center.add(0.0, 0.6, 0.0), UP, 12.0F, 14745599, 14);
         Fx.burst(level, center.add(0.0, 1.0, 0.0), 1.0F, 14745599, 58879, 80, (byte)2);
         Fx.shake(level, center, 1.2F, -1870594049, 14);
         playSound(level, center, SoundEvents.END_PORTAL_SPAWN, 1.3F);
         playSound(level, center, SoundEvents.GENERIC_EXPLODE, 0.5F);
      });
      player.displayClientMessage(Component.literal("Domain Expansion").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}), true);
      return true;
   }

   public static void domainPulse(ServerLevel level, ServerPlayer player) {
      DamageSource source = source(level, player);

      for (LivingEntity target : livingAround(level, player, player.position(), 20.0)) {
         if (!((double)target.distanceTo(player) > 20.0)) {
            Vec3 body = target.getBoundingBox().getCenter();
            Fx.beam(level, body.add(0.0, 10.0, 0.0), body, 0.6F, 58879, 14745599, 8);
            Fx.burst(level, body, 0.4F, 14745599, 58879, 12, (byte)0);
            target.hurt(source, 4.0F);
         }
      }
   }

   public static boolean skyfall(ServerLevel level, ServerPlayer player) {
      Vec3 eye = player.getEyePosition();
      Vec3 look = player.getLookAngle();
      Vec3 far = eye.add(look.scale(64.0));
      BlockHitResult bh = level.clip(new ClipContext(eye, far, Block.COLLIDER, Fluid.NONE, player));
      Vec3 aim = bh.getLocation();
      EntityHitResult eh = ProjectileUtil.getEntityHitResult(
         level, player, eye, aim, new AABB(eye, aim).inflate(1.5), e -> e instanceof LivingEntity && e != player && e.isAlive(), 0.3F
      );
      LivingEntity mark = eh != null ? (LivingEntity)eh.getEntity() : null;
      Vec3 hair = hairPosition(player);
      Fx.beam(level, hair, hair.add(0.0, 90.0, 0.0), 1.0F, 58879, 14745599, 18);
      Fx.beam(level, hair, hair.add(0.0, 90.0, 0.0), 0.4F, 14745599, 16777215, 22);
      Fx.shockwave(level, hair, UP, 1.6F, 14745599, 8);
      Fx.shockwave(level, player.position().add(0.0, 0.1, 0.0), UP, 3.0F, 58879, 10);
      playSound(level, player.position(), SoundEvents.FIREWORK_ROCKET_LAUNCH, 0.5F);
      playSound(level, player.position(), SoundEvents.BEACON_POWER_SELECT, 1.6F);
      player.displayClientMessage(Component.literal("SKYFALL: 10 seconds").withStyle(new ChatFormatting[]{ChatFormatting.AQUA, ChatFormatting.BOLD}), true);

      for (int t = 140; t < 200; t += 10) {
         int left = 200 - t;
         RyuTasks.after(t, () -> {
            Vec3 at = ground(level, mark != null && mark.isAlive() ? mark.position() : aim);
            Fx.beam(level, at.add(0.0, 70.0, 0.0), at, 0.12F, 58879, 14745599, 11);
            Fx.shockwave(level, at.add(0.0, 0.15, 0.0), UP, 6.0F, 58879, 10);
            Fx.shockwave(level, at.add(0.0, 0.15, 0.0), UP, 1.0F + (float)left * 0.05F, 14745599, 10);
            playSound(level, at, (SoundEvent)SoundEvents.NOTE_BLOCK_PLING.value(), 2.0F - (float)left * 0.01F);
         });
      }

      RyuTasks.after(200, () -> {
         Vec3 at = ground(level, mark != null && mark.isAlive() ? mark.position() : aim);
         Fx.beam(level, at.add(0.0, 90.0, 0.0), at, 4.0F, 58879, 14745599, 26);
         Fx.beam(level, at.add(0.0, 90.0, 0.0), at, 1.6F, 14745599, 16777215, 30);
         Fx.shockwave(level, at.add(0.0, 0.2, 0.0), UP, 12.0F, 58879, 20);
         Fx.burst(level, at, 1.0F, 14745599, 58879, 90, (byte)0);
         Fx.burst(level, at, 0.6F, 58879, 30634, 30, (byte)1);
         Fx.arcs(level, at.add(0.0, 1.0, 0.0), 3.0F, 58879, 6, 14);
         Fx.shake(level, at, 1.6F, 1887502335, 16);
         playSound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 0.6F);
         playSound(level, at, SoundEvents.GENERIC_EXPLODE, 0.5F);
         DamageSource src = source(level, player);

         for (LivingEntity e : livingAround(level, player, at, 7.0)) {
            double d = e.position().distanceTo(at);
            if (d <= 7.0) {
               e.hurt(src, (float)(24.0 * (1.0 - 0.6 * d / 7.0)));
               e.setDeltaMovement(e.getDeltaMovement().add(e.position().subtract(at).normalize().scale(0.8).add(0.0, 0.5, 0.0)));
               e.hurtMarked = true;
            }
         }

         for (int dy = 0; dy < 3; dy++) {
            carve(level, at.add(0.0, (double)(-dy) * 1.2, 0.0), 3 - dy);
         }
      });
      return true;
   }

   private static Vec3 ground(ServerLevel level, Vec3 p) {
      MutableBlockPos c = BlockPos.containing(p).mutable();

      for (int i = 0; i < 12 && level.getBlockState(c.below()).getCollisionShape(level, c.below()).isEmpty(); i++) {
         c.move(0, -1, 0);
      }

      return new Vec3(p.x, (double)c.getY(), p.z);
   }

   public static boolean reinforcement(ServerLevel level, ServerPlayer player) {
      boolean active = !CursedEnergy.isReinforced(player);
      CursedEnergy.setReinforced(player, active);
      player.displayClientMessage(
         Component.literal(active ? "Reinforcement: ON" : "Reinforcement: OFF").withStyle(active ? ChatFormatting.AQUA : ChatFormatting.GRAY), true
      );
      playSound(level, player.position(), active ? SoundEvents.BEACON_ACTIVATE : SoundEvents.BEACON_DEACTIVATE, 1.4F);
      if (active) {
         Vec3 center = player.position().add(0.0, (double)player.getBbHeight() / 2.0, 0.0);
         Fx.shimmer(level, center, UP, 1.2F, 58879, 26, (byte)0, player.getId());
         Fx.arcs(level, center, 1.5F, 58879, 5, 10);
      }

      return true;
   }

   private static void shot(ServerLevel level, ServerPlayer player, Vec3 direction, float damage, double hitRadius) {
      Vec3 start = hairPosition(player);
      Vec3 end = fireBeam(level, player, start, direction, 32.0, damage, hitRadius, 0);
      Fx.beam(level, start, end, 0.42F, 58879, 14745599, 8);
      Fx.burst(level, end, 0.4F, 14745599, 58879, 18, (byte)0);
      Fx.shockwave(level, end, UP, 1.6F, 58879, 8);
   }

   private static Vec3 fireBeam(
      ServerLevel level, ServerPlayer player, Vec3 start, Vec3 direction, double range, float damage, double hitRadius, int carveRadius
   ) {
      boolean piercing = carveRadius > 0;
      Vec3 step = direction.normalize().scale(0.5);
      Vec3 point = start;
      Set<LivingEntity> hit = new HashSet<>();
      int steps = (int)(range * 2.0);

      for (int i = 0; i < steps; i++) {
         point = point.add(step);
         if (piercing) {
            if (i % 2 == 0) {
               carve(level, point, carveRadius);
            }
         } else {
            BlockPos pos = BlockPos.containing(point);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
               break;
            }
         }

         hit.addAll(livingAround(level, player, point, hitRadius));
         if (!piercing && !hit.isEmpty()) {
            break;
         }
      }

      DamageSource source = source(level, player);

      for (LivingEntity target : hit) {
         target.hurt(source, damage);
      }

      return point;
   }

   private static void carve(ServerLevel level, Vec3 point, int radius) {
      BlockPos center = BlockPos.containing(point);
      MutableBlockPos cursor = new MutableBlockPos();
      int radiusSq = radius * radius + 1;

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dy = -radius; dy <= radius; dy++) {
            for (int dz = -radius; dz <= radius; dz++) {
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
   }

   public static Vec3 hairPosition(ServerPlayer player) {
      Vec3 look = player.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z);
      flat = flat.lengthSqr() < 1.0E-4 ? Vec3.ZERO : flat.normalize();
      return player.getEyePosition().add(0.0, 0.24, 0.0).add(flat.scale(0.26));
   }

   private static DamageSource source(ServerLevel level, ServerPlayer player) {
      return level.damageSources().indirectMagic(player, player);
   }

   private static List<LivingEntity> livingAround(ServerLevel level, ServerPlayer caster, Vec3 center, double radius) {
      return level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), entity -> entity != caster && entity.isAlive());
   }

   private static void playSound(ServerLevel level, Vec3 pos, SoundEvent sound, float pitch) {
      level.playSound(null, pos.x, pos.y, pos.z, sound, SoundSource.PLAYERS, 1.0F, pitch);
   }
}
