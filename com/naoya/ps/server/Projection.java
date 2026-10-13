package com.naoya.ps.server;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.naoya.ps.Net;
import com.naoya.ps.Sounds;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Projection {
   public static final int SLOTS = 8;
   private static final int[] COOLDOWN = new int[]{14, 140, 200, 40, 180, 360, 160, 1400};
   private static final int[] COST = new int[]{10, 30, 40, 10, 40, 60, 30, 150};
   private static final Map<UUID, long[]> READY = new HashMap<>();
   private static final Map<UUID, Projection.Accel> ACCEL = new HashMap<>();
   private static final Map<Integer, Projection.Frozen> FROZEN = new HashMap<>();
   private static final List<Projection.Script> SCRIPTS = new ArrayList<>();
   public static final int ULT_LENGTH = 104;
   public static final int ULT_HIT = 60;
   public static final int[] ULT_BLINKS = new int[]{46, 51, 56};

   private Projection() {
   }

   public static void useKey(ServerPlayer p, int slot) {
      if (slot >= 0 && slot < 8 && p.isAlive() && !p.isSpectator() && Sorcerers.get(p) == Sorcerer.NAOYA && !busy(p)) {
         long now = p.serverLevel().getGameTime();
         long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[8]);
         if ((now >= ready[slot] || p.getAbilities().instabuild) && Energy.has(p, COST[slot])) {
            if (switch (slot) {
               case 0 -> dash(p);
               case 1 -> palm(p);
               case 2 -> barrage(p);
               case 3 -> step(p);
               case 4 -> soundBarrier(p);
               case 5 -> trap(p);
               case 6 -> shatterAll(p);
               case 7 -> ult(p);
               default -> false;
            }) {
               Energy.spend(p, COST[slot]);
               ready[slot] = now + (long)COOLDOWN[slot];
               accelerate(p, now);
               sync(p);
            }
         }
      }
   }

   public static int[] cooldowns() {
      return (int[])COOLDOWN.clone();
   }

   private static boolean busy(ServerPlayer p) {
      for (Projection.Script s : SCRIPTS) {
         if (s.p == p) {
            return true;
         }
      }

      return false;
   }

   private static void accelerate(ServerPlayer p, long now) {
      Projection.Accel a = ACCEL.computeIfAbsent(p.getUUID(), k -> new Projection.Accel());
      a.stacks = now - a.last <= 60L ? Math.min(5, a.stacks + 1) : 1;
      a.last = now;
      p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 70, a.stacks - 1, false, false, true));
   }

   public static int stacks(Player p) {
      Projection.Accel a = ACCEL.get(p.getUUID());
      return a == null ? 0 : a.stacks;
   }

   private static float power(ServerPlayer p) {
      return 1.0F + 0.1F * (float)stacks(p);
   }

   private static void sync(ServerPlayer p) {
      long[] ready = READY.computeIfAbsent(p.getUUID(), k -> new long[8]);
      Net.state(p, new Net.State((long[])ready.clone(), (int[])COOLDOWN.clone(), stacks(p)));
   }

   private static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
      l.playSound((Player)null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
   }

   private static void fx(ServerPlayer p, int kind, Vec3 a, Vec3 b, float f, int other, int dur) {
      Net.fx(p.serverLevel(), new Net.Fx(kind, a, b, f, p.getId(), other, dur));
   }

   private static Vec3 flatLook(ServerPlayer p) {
      Vec3 l = p.getLookAngle();
      Vec3 f = new Vec3(l.x, 0.0, l.z);
      return f.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : f.normalize();
   }

   private static LivingEntity aim(ServerPlayer p, double range, double cone) {
      Vec3 eye = p.getEyePosition();
      Vec3 look = p.getLookAngle();
      LivingEntity best = null;
      double bestScore = Double.MAX_VALUE;
      AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(range * 0.35 + 1.0);

      for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, ex -> valid(p, ex))) {
         Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
         double d = to.length();
         if (!(d > range + (double)e.getBbWidth())) {
            double ang = Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, to.normalize().dot(look)))));
            if (!(ang > cone)) {
               double score = ang * 2.0 + d;
               if (score < bestScore) {
                  bestScore = score;
                  best = e;
               }
            }
         }
      }

      return best;
   }

   private static boolean valid(ServerPlayer p, LivingEntity e) {
      if (e != p && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand)) {
         if (!(e instanceof Player pl)) {
            return true;
         }

         if (!pl.isCreative()) {
            return true;
         }
      }

      return false;
   }

   private static Vec3 ray(ServerPlayer p, Vec3 from, Vec3 dir, double range) {
      BlockHitResult hit = p.level().clip(new ClipContext(from, from.add(dir.scale(range)), Block.COLLIDER, Fluid.NONE, p));
      return hit.getType() == Type.BLOCK ? hit.getLocation().subtract(dir.scale(0.7)) : from.add(dir.scale(range));
   }

   private static Vec3 safe(ServerPlayer p, Vec3 want, Vec3 fallback) {
      for (double up : new double[]{0.0, 0.6, 1.2, 2.0}) {
         Vec3 at = want.add(0.0, up, 0.0);
         if (p.level().noCollision(p, p.getBoundingBox().move(at.subtract(p.position())))) {
            return at;
         }
      }

      return fallback;
   }

   private static Vec3 ground(ServerPlayer p, Vec3 at) {
      Vec3 top = at.add(0.0, 1.5, 0.0);
      BlockHitResult hit = p.level().clip(new ClipContext(top, top.subtract(0.0, 25.5, 0.0), Block.COLLIDER, Fluid.ANY, p));
      return hit.getType() == Type.BLOCK ? new Vec3(at.x, hit.getLocation().y, at.z) : at;
   }

   private static void teleport(ServerPlayer p, Vec3 to, Vec3 face) {
      Vec3 d = face.subtract(to);
      float yaw = (float)Math.toDegrees(Math.atan2(-d.x, d.z));
      float pitch = (float)Math.max(-18.0, Math.min(18.0, -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)))));
      p.connection.teleport(to.x, to.y, to.z, 0.0F, 0.0F, EnumSet.of(RelativeMovement.X_ROT, RelativeMovement.Y_ROT));
      p.fallDistance = 0.0F;
   }

   private static void hit(ServerPlayer p, LivingEntity e, float amount, Vec3 knock) {
      if (e != null && e.isAlive()) {
         e.invulnerableTime = 0;
         e.hurt(p.damageSources().playerAttack(p), amount * power(p));
         e.invulnerableTime = 0;
         if (knock != null && !FROZEN.containsKey(e.getId())) {
            e.setDeltaMovement(knock);
            e.hurtMarked = true;
         }

         fx(p, 5, e.getBoundingBox().getCenter(), e.getBoundingBox().getCenter().subtract(p.getEyePosition()), amount, e.getId(), 0);
      }
   }

   private static List<LivingEntity> along(ServerPlayer p, Vec3 a, Vec3 b, double r) {
      List<LivingEntity> out = new ArrayList<>();
      AABB box = new AABB(a, b).inflate(r + 1.0);
      Vec3 ab = b.subtract(a);
      double len2 = Math.max(1.0E-6, ab.lengthSqr());

      for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, ex -> valid(p, ex))) {
         Vec3 c = e.getBoundingBox().getCenter();
         double k = Math.max(0.0, Math.min(1.0, c.subtract(a).dot(ab) / len2));
         if (c.distanceTo(a.add(ab.scale(k))) <= r + (double)e.getBbWidth() * 0.5) {
            out.add(e);
         }
      }

      return out;
   }

   public static void freeze(ServerPlayer by, LivingEntity e, int ticks) {
      if (e != null && e.isAlive()) {
         if (e instanceof Player pl && (pl.isCreative() || pl.isSpectator())) {
            return;
         }

         long now = by.serverLevel().getGameTime();
         Projection.Frozen old = FROZEN.get(e.getId());
         boolean var10000;
         if (old != null) {
            var10000 = old.hadNoAi;
         } else {
            label41: {
               if (e instanceof Mob m && m.isNoAi()) {
                  var10000 = true;
                  break label41;
               }

               var10000 = false;
            }
         }

         if (e instanceof Mob m) {
            m.setNoAi(true);
         }

         e.setDeltaMovement(Vec3.ZERO);
         FROZEN.put(e.getId(), new Projection.Frozen(e, e.position(), e.getYRot(), e.getXRot(), now + (long)ticks, var10000, by.getId()));
         fx(by, 2, e.position(), e.getBoundingBox().getCenter(), e.getBbHeight(), e.getId(), ticks);
         play(by.serverLevel(), e.position(), (SoundEvent)Sounds.FREEZE.get(), 1.2F, 1.0F);
      }
   }

   public static boolean frozen(Entity e) {
      return FROZEN.containsKey(e.getId());
   }

   private static void release(Projection.Frozen f, ServerLevel level, boolean shattered) {
      if (f.e instanceof Mob m) {
         m.setNoAi(f.hadNoAi);
      }

      Net.fx(level, new Net.Fx(shattered ? 9 : 3, f.e.position(), f.e.getBoundingBox().getCenter(), f.e.getBbHeight(), f.by, f.e.getId(), 0));
   }

   private static void unfreeze(LivingEntity e, ServerLevel level, boolean shattered) {
      Projection.Frozen f = FROZEN.remove(e.getId());
      if (f != null) {
         release(f, level, shattered);
      }
   }

   private static boolean dash(ServerPlayer p) {
      Vec3 eye = p.getEyePosition();
      Vec3 look = p.getLookAngle();
      double range = 18.0 + 2.5 * (double)stacks(p);
      LivingEntity t = aim(p, range, 14.0);
      Vec3 from = p.position();
      Vec3 to;
      if (t != null) {
         Vec3 d = t.position().subtract(from).multiply(1.0, 0.0, 1.0);
         d = d.lengthSqr() < 1.0E-4 ? flatLook(p) : d.normalize();
         to = safe(p, ground(p, t.position().subtract(d.scale((double)t.getBbWidth() * 0.5 + 0.9))), from);
      } else {
         Vec3 flat = flatLook(p);
         Vec3 start = from.add(0.0, 0.6, 0.0);
         Vec3 end = ray(p, start, flat, range);
         to = safe(p, ground(p, end), from);
      }

      for (LivingEntity e : along(p, from.add(0.0, 1.0, 0.0), to.add(0.0, 1.0, 0.0), 1.3)) {
         hit(p, e, e == t ? 6.0F : 4.0F, to.subtract(from).normalize().scale(0.6).add(0.0, 0.25, 0.0));
      }

      teleport(p, to, t != null ? t.getEyePosition() : to.add(look.scale(5.0)).add(0.0, (double)p.getEyeHeight(), 0.0));
      p.setDeltaMovement(to.subtract(from).multiply(1.0, 0.0, 1.0).normalize().scale(0.35));
      p.hurtMarked = true;
      fx(p, 0, from, to, (float)stacks(p), t == null ? -1 : t.getId(), 24);
      play(p.serverLevel(), from, (SoundEvent)Sounds.DASH.get(), 1.0F, 1.0F + 0.05F * (float)stacks(p));
      play(p.serverLevel(), to, (SoundEvent)Sounds.SHUTTER.get(), 0.8F, 1.2F);
      return true;
   }

   private static boolean palm(ServerPlayer p) {
      LivingEntity t = aim(p, 11.0, 32.0);
      if (t == null) {
         p.displayClientMessage(Component.literal("Nothing within reach to touch.").withStyle(ChatFormatting.GRAY), true);
         return false;
      } else {
         Vec3 from = p.position();
         Vec3 d = t.position().subtract(from).multiply(1.0, 0.0, 1.0);
         d = d.lengthSqr() < 1.0E-4 ? flatLook(p) : d.normalize();
         Vec3 to = safe(p, t.position().subtract(d.scale((double)t.getBbWidth() * 0.5 + 0.8)), from);
         teleport(p, to, t.getEyePosition());
         fx(p, 0, from, to, (float)stacks(p), t.getId(), 12);
         hit(p, t, 4.0F, (Vec3)null);
         freeze(p, t, 70);
         fx(p, 1, to.add(0.0, 1.2, 0.0).add(d.scale(0.6)), t.getBoundingBox().getCenter(), 1.0F, t.getId(), 0);
         play(p.serverLevel(), to, (SoundEvent)Sounds.HEAVY.get(), 1.0F, 1.3F);
         return true;
      }
   }

   private static boolean barrage(ServerPlayer p) {
      LivingEntity t = aim(p, 9.0, 35.0);
      if (t == null) {
         p.displayClientMessage(Component.literal("No one close enough.").withStyle(ChatFormatting.GRAY), true);
         return false;
      } else {
         SCRIPTS.add(new Projection.Script(p, 2, t, flatLook(p)));
         fx(p, 4, p.position(), t.position(), 0.0F, t.getId(), 26);
         return true;
      }
   }

   private static boolean step(ServerPlayer p) {
      LivingEntity t = aim(p, 16.0, 30.0);
      Vec3 from = p.position();
      Vec3 to;
      Vec3 face;
      if (t != null) {
         Vec3 tl = t.getLookAngle().multiply(1.0, 0.0, 1.0);
         tl = tl.lengthSqr() < 1.0E-4 ? flatLook(p) : tl.normalize();
         to = safe(p, t.position().subtract(tl.scale((double)t.getBbWidth() * 0.5 + 1.1)), from);
         face = t.getEyePosition();
      } else {
         Vec3 back = flatLook(p).scale(-1.0);
         to = safe(p, ray(p, from.add(0.0, 0.5, 0.0), back, 8.0).subtract(0.0, 0.5, 0.0), from);
         face = to.add(flatLook(p).scale(5.0)).add(0.0, (double)p.getEyeHeight(), 0.0);
      }

      teleport(p, to, face);
      p.invulnerableTime = 12;
      fx(p, 6, from, to, (float)stacks(p), t == null ? -1 : t.getId(), 30);
      play(p.serverLevel(), from, (SoundEvent)Sounds.BLINK.get(), 1.0F, 1.0F);
      return true;
   }

   private static boolean soundBarrier(ServerPlayer p) {
      Vec3 dir = flatLook(p);
      Vec3 from = p.position();
      double range = (double)(26 + 3 * stacks(p));
      Vec3 end = ray(p, from.add(0.0, 0.6, 0.0), dir, range).subtract(0.0, 0.6, 0.0);
      Vec3 to = safe(p, end, from);

      for (LivingEntity e : along(p, from.add(0.0, 1.0, 0.0), to.add(0.0, 1.0, 0.0), 2.6)) {
         hit(p, e, 11.0F, dir.scale(2.2).add(0.0, 0.8, 0.0));
      }

      teleport(p, to, to.add(dir.scale(6.0)).add(0.0, (double)p.getEyeHeight(), 0.0));
      p.setDeltaMovement(dir.scale(0.6));
      p.hurtMarked = true;
      fx(p, 7, from, to, (float)stacks(p), -1, 30);
      play(p.serverLevel(), from, (SoundEvent)Sounds.BOOM.get(), 2.5F, 1.0F);
      play(p.serverLevel(), to, (SoundEvent)Sounds.DASH.get(), 1.0F, 0.8F);
      return true;
   }

   private static boolean trap(ServerPlayer p) {
      List<LivingEntity> near = p.level()
         .getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(9.0), ex -> valid(p, ex) && ex.distanceTo(p) <= 9.0F);
      if (near.isEmpty()) {
         p.displayClientMessage(Component.literal("No one to catch.").withStyle(ChatFormatting.GRAY), true);
         return false;
      } else {
         fx(p, 8, p.position(), p.position(), 9.0F, -1, 20);
         play(p.serverLevel(), p.position(), (SoundEvent)Sounds.FILM.get(), 1.4F, 1.0F);

         for (LivingEntity e : near) {
            freeze(p, e, 50);
         }

         return true;
      }
   }

   private static boolean shatterAll(ServerPlayer p) {
      List<LivingEntity> targets = new ArrayList<>();

      for (Projection.Frozen f : FROZEN.values()) {
         if (f.e.isAlive() && f.e.level() == p.level() && f.e.distanceTo(p) < 20.0F) {
            targets.add(f.e);
         }
      }

      if (targets.isEmpty()) {
         p.displayClientMessage(Component.literal("No frames to break.").withStyle(ChatFormatting.GRAY), true);
         return false;
      } else {
         play(p.serverLevel(), p.position(), (SoundEvent)Sounds.SHATTER.get(), 1.6F, 0.9F);

         for (LivingEntity e : targets) {
            unfreeze(e, p.serverLevel(), true);
            Vec3 away = e.position().subtract(p.position()).multiply(1.0, 0.0, 1.0);
            away = away.lengthSqr() < 1.0E-4 ? flatLook(p) : away.normalize();
            hit(p, e, 14.0F, away.scale(1.1).add(0.0, 0.6, 0.0));
         }

         return true;
      }
   }

   private static boolean ult(ServerPlayer p) {
      LivingEntity t = aim(p, 32.0, 26.0);
      if (t == null) {
         p.displayClientMessage(Component.literal("Look at a target.").withStyle(ChatFormatting.GRAY), true);
         return false;
      } else {
         freeze(p, t, 64);
         SCRIPTS.add(new Projection.Script(p, 7, t, flatLook(p)));
         fx(p, 10, p.position(), t.position(), 0.0F, t.getId(), 104);
         play(p.serverLevel(), p.position(), (SoundEvent)Sounds.FILM.get(), 1.6F, 0.85F);
         return true;
      }
   }

   private static void runScripts() {
      Iterator<Projection.Script> it = SCRIPTS.iterator();

      while (it.hasNext()) {
         Projection.Script s = it.next();
         ServerPlayer p = s.p;
         int age = (int)(p.serverLevel().getGameTime() - s.start);
         if (!p.isAlive() || p.isRemoved()) {
            it.remove();
         } else if (age != s.last) {
            s.last = age;

            if (switch (s.slot) {
               case 2 -> barrageTick(s, age);
               case 7 -> ultTick(s, age);
               default -> true;
            }) {
               it.remove();
            }
         }
      }
   }

   private static boolean barrageTick(Projection.Script s, int age) {
      ServerPlayer p = s.p;
      LivingEntity t = s.target;
      if (t == null || !t.isAlive() || t.level() != p.level()) {
         return true;
      } else if (age < 24) {
         if (age % 3 == 0) {
            double ang = (double)age * 2.399 + (double)s.start;
            Vec3 around = new Vec3(Math.cos(ang), 0.0, Math.sin(ang)).scale((double)t.getBbWidth() * 0.5 + 1.0);
            Vec3 to = safe(p, t.position().add(around), p.position());
            Vec3 from = p.position();
            teleport(p, to, t.getEyePosition());
            fx(p, 0, from, to, (float)stacks(p), t.getId(), 4);
         }

         hit(p, t, 1.4F, (Vec3)null);
         if (age % 2 == 0) {
            play(p.serverLevel(), t.position(), (SoundEvent)Sounds.HIT.get(), 0.7F, 0.9F + 0.03F * (float)(age % 8));
         }

         return false;
      } else {
         Vec3 away = t.position().subtract(p.position()).multiply(1.0, 0.0, 1.0);
         away = away.lengthSqr() < 1.0E-4 ? s.dir : away.normalize();
         boolean wasFrozen = frozen(t);
         unfreeze(t, p.serverLevel(), wasFrozen);
         hit(p, t, wasFrozen ? 12.0F : 7.0F, away.scale(1.6).add(0.0, 0.55, 0.0));
         play(p.serverLevel(), t.position(), (SoundEvent)Sounds.HEAVY.get(), 1.4F, 0.8F);
         return true;
      }
   }

   private static boolean ultTick(Projection.Script s, int age) {
      ServerPlayer p = s.p;
      LivingEntity t = s.target;
      ServerLevel level = p.serverLevel();
      if (t == null || !t.isAlive() || t.level() != p.level()) {
         return true;
      } else if (age < ULT_BLINKS[0]) {
         teleport(p, s.origin, t.getEyePosition());
         p.setDeltaMovement(Vec3.ZERO);
         return false;
      } else {
         for (int i = 0; i < ULT_BLINKS.length; i++) {
            if (age == ULT_BLINKS[i]) {
               Vec3 tp = t.position();
               Vec3 d = tp.subtract(s.origin).multiply(1.0, 0.0, 1.0);
               d = d.lengthSqr() < 1.0E-4 ? s.dir : d.normalize();
               double k = (double)(i + 1) / (double)(ULT_BLINKS.length + 1);
               Vec3 from = p.position();
               Vec3 to = safe(p, s.origin.lerp(tp.subtract(d.scale(1.6)), k), from);
               teleport(p, to, t.getEyePosition());
               Net.fx(level, new Net.Fx(11, from, to, (float)(i + 1), p.getId(), t.getId(), 0));
               play(level, to, (SoundEvent)Sounds.BOOM.get(), 2.0F, 1.0F + 0.15F * (float)i);
            }
         }

         if (age > ULT_BLINKS[ULT_BLINKS.length - 1] && age < 60) {
            p.setDeltaMovement(Vec3.ZERO);
         }

         if (age == 60) {
            Vec3 tp = t.position();
            Vec3 d = tp.subtract(s.origin).multiply(1.0, 0.0, 1.0);
            d = d.lengthSqr() < 1.0E-4 ? s.dir : d.normalize();
            Vec3 at = safe(p, tp.subtract(d.scale((double)t.getBbWidth() * 0.5 + 0.9)), p.position());
            teleport(p, at, t.getEyePosition());
            unfreeze(t, level, true);
            t.invulnerableTime = 0;
            t.hurt(p.damageSources().playerAttack(p), 42.0F * power(p));
            t.setDeltaMovement(d.scale(4.2).add(0.0, 1.1, 0.0));
            t.hurtMarked = true;

            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, t.getBoundingBox().inflate(6.0), ex -> valid(p, ex) && ex != t)) {
               Vec3 away = e.position().subtract(tp).multiply(1.0, 0.0, 1.0);
               away = away.lengthSqr() < 1.0E-4 ? d : away.normalize();
               e.invulnerableTime = 0;
               e.hurt(p.damageSources().playerAttack(p), 9.0F);
               e.setDeltaMovement(away.scale(1.6).add(0.0, 0.6, 0.0));
               e.hurtMarked = true;
            }

            Net.fx(level, new Net.Fx(12, t.getBoundingBox().getCenter(), d, 1.0F, p.getId(), t.getId(), 0));
            play(level, tp, (SoundEvent)Sounds.ULT_HIT.get(), 3.0F, 1.0F);
         }

         return age >= 104;
      }
   }

   private static void holdFrozen(MinecraftServer server) {
      Iterator<Entry<Integer, Projection.Frozen>> it = FROZEN.entrySet().iterator();

      while (it.hasNext()) {
         Projection.Frozen f = it.next().getValue();
         LivingEntity e = f.e;
         if (e.isAlive() && !e.isRemoved()) {
            ServerLevel level = (ServerLevel)e.level();
            if (level.getGameTime() >= f.until) {
               it.remove();
               release(f, level, false);
            } else {
               e.setDeltaMovement(Vec3.ZERO);
               e.fallDistance = 0.0F;
               if (e.position().distanceToSqr(f.at) > 0.0025) {
                  if (e instanceof ServerPlayer sp) {
                     sp.connection.teleport(f.at.x, f.at.y, f.at.z, f.yaw, f.pitch);
                  } else {
                     e.teleportTo(f.at.x, f.at.y, f.at.z);
                  }
               }

               if (e instanceof Player) {
                  e.setYRot(f.yaw);
                  e.setXRot(f.pitch);
               }
            }
         } else {
            if (e instanceof Mob m) {
               m.setNoAi(f.hadNoAi);
            }

            it.remove();
         }
      }
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent e) {
      if (e.phase == Phase.END) {
         runScripts();
         holdFrozen(e.getServer());

         for (ServerPlayer p : e.getServer().getPlayerList().getPlayers()) {
            if (Sorcerers.get(p) == Sorcerer.NAOYA && p.tickCount % 10 == 0) {
               Projection.Accel a = ACCEL.get(p.getUUID());
               if (a != null && a.stacks > 0 && p.serverLevel().getGameTime() - a.last > 60L) {
                  a.stacks = 0;
               }

               sync(p);
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onAttack(LivingAttackEvent e) {
      if (e.getSource().getEntity() instanceof LivingEntity attacker && frozen(attacker)) {
         e.setCanceled(true);
         return;
      }

      if (e.getEntity() instanceof ServerPlayer p) {
         for (Projection.Script s : SCRIPTS) {
            if (s.p == p && s.slot == 7) {
               e.setCanceled(true);
               return;
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent e) {
      SCRIPTS.removeIf(s -> s.p == e.getEntity());
   }

   @SubscribeEvent
   public static void onStop(ServerStoppingEvent e) {
      for (Projection.Frozen f : FROZEN.values()) {
         if (f.e instanceof Mob m) {
            m.setNoAi(f.hadNoAi);
         }
      }

      FROZEN.clear();
      SCRIPTS.clear();
      READY.clear();
      ACCEL.clear();
   }

   private static final class Accel {
      int stacks;
      long last;
   }

   private static record Frozen(LivingEntity e, Vec3 at, float yaw, float pitch, long until, boolean hadNoAi, int by) {
   }

   private static final class Script {
      final ServerPlayer p;
      final int slot;
      final LivingEntity target;
      final long start;
      final Vec3 origin;
      final Vec3 dir;
      int last = -1;

      Script(ServerPlayer p, int slot, LivingEntity target, Vec3 dir) {
         this.p = p;
         this.slot = slot;
         this.target = target;
         this.start = p.serverLevel().getGameTime();
         this.origin = p.position();
         this.dir = dir;
      }
   }
}
