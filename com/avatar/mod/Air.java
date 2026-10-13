package com.avatar.mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class Air {
   public static final int SCOOT_TICKS = 200;
   public static final int TORNADO_TICKS = 140;
   public static final int GLIDE_TICKS = 1200;
   private static final Map<UUID, Long> SCOOT = new HashMap<>();
   private static final Map<UUID, Long> GLIDE = new HashMap<>();
   private static final List<Air.Tornado> TORNADOS = new ArrayList<>();

   private Air() {
   }

   static void key(ServerPlayer p, int slot) {
      switch (slot) {
         case 0:
            blast(p);
            break;
         case 1:
            slice(p);
            break;
         case 2:
            scooter(p);
            break;
         case 3:
            shield(p);
            break;
         case 4:
            tornado(p);
            break;
         case 5:
            glider(p);
            break;
         case 6:
            dash(p);
      }
   }

   private static void blast(ServerPlayer p) {
      if (Elem.pay(p, 8)) {
         Bending.setCooldown(p, 0, 10);
         Bending.pose(p, 5, 9);
         ServerLevel l = p.serverLevel();
         Vec3 from = Bending.fist(p, 0);
         Vec3 dir = p.getLookAngle();
         Net.fx(l, new Net.Fx((byte)31, from, dir, 0.0F, p.getId(), 0, 0));
         Bending.play(l, from, (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 1.3F, 1.35F);
         Bending.play(l, from, SoundEvents.PHANTOM_FLAP, 1.0F, 0.6F);

         for (Entity e : l.getEntities(p, new AABB(from, from.add(dir.scale(11.0))).inflate(3.0))) {
            Vec3 d = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).subtract(from);
            double along = d.dot(dir);
            if (!(along < 0.0) && !(along > 11.0) && !(d.subtract(dir.scale(along)).length() > 1.0 + along * 0.3)) {
               if (e instanceof Projectile pr) {
                  pr.setDeltaMovement(dir.scale(1.6));
                  pr.setOwner(p);
                  pr.hasImpulse = true;
               } else if (e instanceof LivingEntity) {
                  LivingEntity le = (LivingEntity)e;
                  if (le.isAlive()) {
                     double k = 2.2 * (1.0 - along / 13.0) + 0.5;
                     le.setDeltaMovement(dir.x * k, 0.45 + Math.max(0.0, dir.y) * k, dir.z * k);
                     le.hurtMarked = true;
                     le.clearFire();
                     Bending.hit(p, le, 3.0F);
                  }
               }
            }
         }
      }
   }

   private static void slice(ServerPlayer p) {
      if (Elem.pay(p, 10)) {
         Bending.setCooldown(p, 1, 16);
         Bending.pose(p, 13, 8);
         Vec3 from = Bending.fist(p, 1);
         Bending.launchElem(p, from, Bending.dir(p, from, Bending.aim(p, 48.0)), 0.6F, 2.4F, 9.0F, 22, 0.0F, 3, 0.0);
         Bending.play(p.serverLevel(), from, (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 1.0F, 1.8F);
      }
   }

   private static void scooter(ServerPlayer p) {
      if (SCOOT.remove(p.getUUID()) != null) {
         stopScooter(p);
      } else if (Elem.pay(p, 20)) {
         SCOOT.put(p.getUUID(), Bending.now + 200L);
         Net.fx(p.serverLevel(), new Net.Fx((byte)32, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 200));
         Bending.pose(p, 18, 200);
         Bending.play(p.serverLevel(), p.position(), (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 1.0F, 1.2F);
      }
   }

   private static void stopScooter(ServerPlayer p) {
      Bending.setCooldown(p, 2, 200);
      Net.fx(p.serverLevel(), new Net.Fx((byte)32, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
      Bending.pose(p, 18, 0);
   }

   private static void shield(ServerPlayer p) {
      if (Elem.pay(p, 20)) {
         Bending.setCooldown(p, 3, 160);
         Bending.SHIELDS.put(p.getUUID(), new double[]{3.6, (double)(Bending.now + 60L), 3.0});
         Net.fx(p.serverLevel(), new Net.Fx((byte)20, p.position(), Vec3.ZERO, 3.6F, p.getId(), 3, 60));
         Bending.pose(p, 17, 60);

         for (int i = 0; i < 4; i++) {
            Bending.later(i * 15, () -> Bending.play(p.serverLevel(), p.position(), (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 1.0F, 0.8F));
         }
      }
   }

   private static void tornado(ServerPlayer p) {
      ServerLevel l = p.serverLevel();
      BlockPos g = Elem.ground(l, Bending.aim(p, 30.0));
      if (g != null && Elem.pay(p, 50)) {
         Bending.setCooldown(p, 4, 600);
         Vec3 look = p.getLookAngle();
         Vec3 drift = new Vec3(look.x, 0.0, look.z).normalize().scale(0.06);
         Vec3 at = Vec3.atBottomCenterOf(g.above());
         Bending.hold(p, 20);
         Bending.pose(p, 12, 20);
         Net.fx(l, new Net.Fx((byte)36, p.getEyePosition(), at.subtract(p.getEyePosition()).normalize(), 0.0F, p.getId(), 5, 46));
         Net.fx(l, new Net.Fx((byte)33, at, drift, 0.0F, p.getId(), 0, 140));
         TORNADOS.add(new Air.Tornado(p, l, at, drift, Bending.now));
         Bending.play(l, at, (SoundEvent)AvatarMod.FIRE_ROAR.get(), 2.5F, 0.5F);
         Bending.play(l, at, SoundEvents.ELYTRA_FLYING, 2.0F, 0.6F);
      }
   }

   private static void glider(ServerPlayer p) {
      if (GLIDE.remove(p.getUUID()) != null) {
         stopGlide(p);
      } else if (Elem.pay(p, 10)) {
         GLIDE.put(p.getUUID(), Bending.now + 1200L);
         if (p.onGround()) {
            p.setDeltaMovement(p.getDeltaMovement().add(0.0, 1.15, 0.0));
            p.hurtMarked = true;
         }

         Net.fx(p.serverLevel(), new Net.Fx((byte)34, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 1200));
         Bending.pose(p, 19, 1200);
         Bending.play(p.serverLevel(), p.position(), (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 1.2F, 0.9F);
      }
   }

   private static void stopGlide(ServerPlayer p) {
      Bending.setCooldown(p, 5, 40);
      Net.fx(p.serverLevel(), new Net.Fx((byte)34, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
      Bending.pose(p, 19, 0);
   }

   private static void dash(ServerPlayer p) {
      if (Elem.pay(p, 8)) {
         Bending.setCooldown(p, 6, 30);
         Vec3 m = p.getDeltaMovement().multiply(1.0, 0.0, 1.0);
         Vec3 look = p.getLookAngle();
         Vec3 dir = m.lengthSqr() > 0.004 ? m.normalize() : new Vec3(look.x, 0.0, look.z).normalize();
         p.setDeltaMovement(dir.x * 2.2, 0.3, dir.z * 2.2);
         p.hurtMarked = true;
         p.invulnerableTime = 15;
         p.fallDistance = 0.0F;
         Net.fx(p.serverLevel(), new Net.Fx((byte)35, p.position(), p.position().add(dir.scale(5.0)), 0.0F, p.getId(), 0, 0));
         Bending.play(p.serverLevel(), p.position(), (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 1.0F, 1.6F);
      }
   }

   static void tickPlayer(ServerPlayer p) {
      UUID id = p.getUUID();
      long now = Bending.now;
      Long s = SCOOT.get(id);
      if (s != null) {
         if (s >= now && p.isAlive() && Bending.isBender(p)) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 5, 3, false, false, false));
            p.fallDistance = 0.0F;
         } else {
            SCOOT.remove(id);
            stopScooter(p);
         }
      }

      Long g = GLIDE.get(id);
      if (g != null) {
         p.fallDistance = 0.0F;
         boolean landed = p.onGround() && now - (g - 1200L) > 10L;
         if (g < now || !p.isAlive() || landed || p.isInWater()) {
            GLIDE.remove(id);
            stopGlide(p);
         }
      }

      double[] sh = Bending.SHIELDS.get(id);
      if (sh != null && sh[2] == 3.0 && sh[1] >= (double)now) {
         Elem.shieldTick(p, sh[0], true);
      }
   }

   static void tick(MinecraftServer server) {
      long now = Bending.now;
      Iterator<Air.Tornado> it = TORNADOS.iterator();

      while (it.hasNext()) {
         Air.Tornado t = it.next();
         long age = now - t.start;
         Vec3 c = t.at.add(t.drift.scale((double)age));
         boolean end = age >= 140L;

         for (LivingEntity e : t.l
            .getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(6.5, 12.0, 6.5), ex -> ex != t.p && ex.isAlive() && !ex.isSpectator())) {
            Vec3 d = e.position().subtract(c);
            Vec3 flat = new Vec3(d.x, 0.0, d.z);
            double r = flat.length();
            if (!(r > 6.5) && !(d.y < -1.5)) {
               Vec3 in = r < 0.001 ? Vec3.ZERO : flat.scale(-1.0 / r);
               Vec3 spin = new Vec3(-in.z, 0.0, in.x);
               if (end) {
                  e.setDeltaMovement(-in.x * 1.4, 1.2, -in.z * 1.4);
               } else {
                  double lift = d.y < 8.0 ? 0.28 + (r < 2.5 ? 0.15 : 0.0) : 0.02;
                  e.setDeltaMovement(in.scale(0.18 * Math.min(1.0, r / 2.0)).add(spin.scale(0.45)).add(0.0, lift, 0.0));
                  if (age % 10L == 0L) {
                     Bending.hit(t.p, e, 2.0F);
                  }
               }

               e.fallDistance = 0.0F;
               e.hurtMarked = true;
            }
         }

         if (end) {
            it.remove();
         } else if (age % 30L == 0L) {
            Bending.play(t.l, c, (SoundEvent)AvatarMod.FIRE_ROAR.get(), 2.0F, 0.5F);
         }
      }
   }

   public static boolean gliding(ServerPlayer p) {
      return GLIDE.containsKey(p.getUUID());
   }

   static void leave(ServerPlayer p) {
      SCOOT.remove(p.getUUID());
      GLIDE.remove(p.getUUID());
   }

   static void clear() {
      SCOOT.clear();
      GLIDE.clear();
      TORNADOS.clear();
   }

   private static record Tornado(ServerPlayer p, ServerLevel l, Vec3 at, Vec3 drift, long start) {
   }
}
