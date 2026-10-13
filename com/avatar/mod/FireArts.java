package com.avatar.mod;

import com.jjk.mod.Energy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;

public final class FireArts {
   public static final int WHIP_SWEEP = 6;
   public static final int WHIP_DELAY = 3;
   public static final float WHIP_RADIUS = 7.0F;
   public static final int INHALE = 14;
   public static final int EXHALE = 50;
   public static final int BLUE_TICKS = 600;
   public static final int BLUE_CUT = 32;
   private static final Map<UUID, FireArts.Breath> BREATHS = new HashMap<>();
   private static final Map<UUID, Long> BLUE = new HashMap<>();

   private FireArts() {
   }

   static float mul(ServerPlayer p) {
      return isBlue(p) ? 1.35F : 1.0F;
   }

   static int flags(ServerPlayer p) {
      return isBlue(p) ? 1 : 0;
   }

   static void whip(ServerPlayer p) {
      if (Energy.has(p, 10)) {
         Elem.spend(p, 10);
         Bending.setCooldown(p, 1, 24);
         Bending.pose(p, 6, 14);
         ServerLevel l = p.serverLevel();
         Vec3 look = p.getLookAngle();
         Vec3 fwd = new Vec3(look.x, 0.0, look.z).normalize();
         Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
         Vec3 c = p.position().add(0.0, 1.0, 0.0);
         float dmg = 10.0F * mul(p);
         Net.fx(l, new Net.Fx((byte)8, c, fwd, 7.0F, p.getId(), 0, 6, flags(p)));
         Bending.play(l, c, (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 1.2F, 0.85F);
         Set<Integer> hit = new HashSet<>();

         for (int i = 0; i < 6; i++) {
            int k = i;
            Bending.later(
               3 + i,
               () -> {
                  double a0 = 75.0 - 150.0 * (double)k / 6.0;
                  double a1 = 75.0 - 150.0 * (double)(k + 1) / 6.0;

                  for (LivingEntity e : l.getEntitiesOfClass(
                     LivingEntity.class, new AABB(c, c).inflate(8.0), ex -> ex != p && ex.isAlive() && !ex.isSpectator() && !hit.contains(ex.getId())
                  )) {
                     Vec3 d = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).subtract(c);
                     double dist = Math.sqrt(d.x * d.x + d.z * d.z);
                     if (!(dist > (double)(7.0F + e.getBbWidth() / 2.0F)) && !(Math.abs(d.y) > 2.5)) {
                        double ang = Math.toDegrees(Math.atan2(d.dot(right), d.dot(fwd)));
                        if (ang <= a0 + 10.0 && ang >= a1 - 10.0) {
                           hit.add(e.getId());
                           Bending.burn(p, e, dmg, 5);
                           Vec3 out = new Vec3(d.x, 0.0, d.z).normalize();
                           Bending.knock(e, out.add(right.scale(-0.8)).normalize(), 0.8, 0.25);
                        }
                     }
                  }

                  if (k == 2) {
                     Bending.play(l, c, (SoundEvent)AvatarMod.FIRE_BURST.get(), 0.8F, 1.15F);
                  }
               }
            );
         }
      }
   }

   static void daggers(ServerPlayer p) {
      if (Energy.has(p, 15)) {
         Elem.spend(p, 15);
         Bending.setCooldown(p, 2, 40);

         for (int i = 0; i < 6; i++) {
            int k = i;
            Bending.later(i * 2, () -> {
               if (p.isAlive() && !p.isRemoved()) {
                  int side = k % 2 == 0 ? 1 : -1;
                  Bending.pose(p, side > 0 ? 1 : 2, 4);
                  Vec3 target = Bending.aim(p, 48.0);
                  Vec3 from = Bending.fist(p, side);
                  Vec3 d = Bending.dir(p, from, target);
                  Bending.launch(p, from, d, 0.22F, 2.6F, 4.0F, 18, 0.0F, 2, false);
               }
            });
         }
      }
   }

   static void breath(ServerPlayer p) {
      if (Energy.has(p, 50)) {
         Elem.spend(p, 50);
         Bending.setCooldown(p, 3, 400);
         ServerLevel l = p.serverLevel();
         Vec3 look = p.getLookAngle();
         boolean blue = isBlue(p);
         Bending.hold(p, 64);
         BREATHS.put(p.getUUID(), new FireArts.Breath(Bending.now, look, blue));
         Net.fx(l, new Net.Fx((byte)14, p.getEyePosition(), look, 0.0F, p.getId(), 0, 72, flags(p)));
         Bending.pose(p, 7, 14);
         Bending.play(l, p.position(), SoundEvents.BLAZE_AMBIENT, 1.2F, 0.5F);
         Bending.later(14, () -> {
            Bending.pose(p, 8, 50);
            Net.fx(l, new Net.Fx((byte)9, mouth(p, look), look, 0.0F, p.getId(), 0, 50, blue ? 1 : 0));
            Bending.play(l, p.position(), (SoundEvent)AvatarMod.DRAGON_ROAR.get(), 2.2F, 0.8F);
            Bending.play(l, p.position(), (SoundEvent)AvatarMod.FIRE_ROAR.get(), 2.2F, 0.85F);
         });
         Bending.later(34, () -> Bending.play(l, p.position(), (SoundEvent)AvatarMod.FIRE_ROAR.get(), 2.0F, 0.8F));
         Bending.later(52, () -> Bending.play(l, p.position(), (SoundEvent)AvatarMod.FIRE_ROAR.get(), 1.6F, 0.9F));
      }
   }

   static Vec3 mouth(ServerPlayer p, Vec3 dir) {
      return p.getEyePosition().add(dir.scale(0.45)).add(0.0, -0.12, 0.0);
   }

   public static boolean breathing(ServerPlayer p) {
      return BREATHS.containsKey(p.getUUID());
   }

   static void blue(ServerPlayer p) {
      ServerLevel l = p.serverLevel();
      if (isBlue(p)) {
         end(p);
      } else if (Energy.has(p, 40)) {
         Elem.spend(p, 40);
         BLUE.put(p.getUUID(), Bending.now + 600L);
         Bending.hold(p, 26);
         Net.fx(l, new Net.Fx((byte)18, p.getEyePosition(), p.getLookAngle(), 0.0F, p.getId(), 0, 32, 1));
         Net.fx(l, new Net.Fx((byte)15, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 600, 1));
         Bending.pose(p, 12, 28);
         Bending.play(l, p.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 1.0F, 1.4F);
         Bending.later(14, () -> {
            Bending.play(l, p.position(), (SoundEvent)AvatarMod.FIRE_BURST.get(), 1.6F, 1.35F);
            Bending.play(l, p.position(), (SoundEvent)AvatarMod.FIRE_ROAR.get(), 1.2F, 1.4F);
         });
      }
   }

   private static void end(ServerPlayer p) {
      if (BLUE.remove(p.getUUID()) != null) {
         Net.fx(p.serverLevel(), new Net.Fx((byte)15, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
         Bending.setCooldown(p, 6, 900);
         Bending.play(p.serverLevel(), p.position(), SoundEvents.FIRE_EXTINGUISH, 0.6F, 1.2F);
      }
   }

   public static boolean isBlue(ServerPlayer p) {
      Long t = BLUE.get(p.getUUID());
      return t != null && t > Bending.now;
   }

   static int blueLeft(ServerPlayer p) {
      Long t = BLUE.get(p.getUUID());
      return t == null ? 0 : (int)Math.max(0L, t - Bending.now);
   }

   static void tickPlayer(ServerPlayer p) {
      UUID id = p.getUUID();
      Long blue = BLUE.get(id);
      if (blue != null && (blue <= Bending.now || !Bending.isFire(p))) {
         end(p);
      }

      FireArts.Breath b = BREATHS.get(id);
      if (b != null) {
         long t = Bending.now - b.start;
         if (t < 64L && p.isAlive()) {
            if (t >= 14L && t % 3L == 0L) {
               ServerLevel l = p.serverLevel();
               Vec3 m = mouth(p, b.dir);
               float dmg = 3.5F * (b.blue ? 1.35F : 1.0F);

               for (LivingEntity e : l.getEntitiesOfClass(
                  LivingEntity.class, new AABB(m, m.add(b.dir.scale(16.0))).inflate(4.0), ex -> ex != p && ex.isAlive() && !ex.isSpectator()
               )) {
                  Vec3 v = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).subtract(m);
                  double along = v.dot(b.dir);
                  if (!(along < 0.0) && !(along > 16.0)) {
                     double perp = v.subtract(b.dir.scale(along)).length();
                     if (perp < 0.6 + along * 0.2 + (double)(e.getBbWidth() / 2.0F)) {
                        Bending.burn(p, e, dmg, 6);
                        Bending.knock(e, b.dir, 0.18, 0.04);
                     }
                  }
               }

               if (t % 6L == 0L && l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
                  BlockHitResult bh = l.clip(new ClipContext(m, m.add(b.dir.scale(16.0)), Block.COLLIDER, Fluid.NONE, p));
                  if (bh.getType() != Type.MISS) {
                     BlockPos pos = bh.getBlockPos().relative(bh.getDirection());
                     if (l.isEmptyBlock(pos) && BaseFireBlock.canBePlacedAt(l, pos, Direction.UP)) {
                        l.setBlockAndUpdate(pos, BaseFireBlock.getState(l, pos));
                     }
                  }
               }
            }
         } else {
            BREATHS.remove(id);
         }
      }
   }

   static void leave(ServerPlayer p) {
      BREATHS.remove(p.getUUID());
      end(p);
   }

   static void clear() {
      BREATHS.clear();
      BLUE.clear();
   }

   private static record Breath(long start, Vec3 dir, boolean blue) {
   }
}
