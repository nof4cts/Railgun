package com.avatar.mod;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Bending {
   public static final int SLOTS = 10;
   public static final int TAP = 5;
   public static final int FULL = 40;
   public static final int MAX_HOLD = 200;
   public static final int CUT = 38;
   public static final int FIRE_AT = 22;
   private static final int PUNCH_COST = 5;
   private static final Map<UUID, Long> CHARGING = new HashMap<>();
   private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
   static final int MOVES = 8;
   public static final int OCTO_STRIKE = 15;
   private static final Map<UUID, long[]> COMBO = new HashMap<>();
   static final Map<UUID, Bending.Hold> HOLDS = new HashMap<>();
   private static final List<Bending.Timed> TIMED = new ArrayList<>();
   private static final List<Bending.Blast> BLASTS = new ArrayList<>();
   private static int serial;
   static long now;
   static final Map<UUID, double[]> SHIELDS = new HashMap<>();
   static final int MIN_COOLDOWN = 40;

   private Bending() {
   }

   public static boolean isFire(Player p) {
      return Sorcerers.is(p, Sorcerer.FIRE);
   }

   static boolean isBender(Player p) {
      return Sorcerers.get(p).isAvatar();
   }

   public static void useKey(ServerPlayer p, int slot, boolean down) {
      UUID id = p.getUUID();
      if (!down && slot == 4) {
         Lightning.release(p);
      } else {
         Sorcerer el = Sorcerers.get(p);
         if (slot == 15) {
            if (down && el == Sorcerer.WATER && p.isAlive()) {
               Water.octoStrike(p);
            }
         } else if (el != Sorcerer.FIRE) {
            if (down && el.isAvatar() && p.isAlive() && !p.isSpectator() && slot < el.moves.length && !busy(p) && cooldown(p, slot) <= 0) {
               switch (el) {
                  case WATER:
                     Water.key(p, slot);
                     break;
                  case EARTH:
                     Earth.key(p, slot);
                     break;
                  case AIR:
                     Air.key(p, slot);
               }
            }
         } else if (down || slot == 0) {
            if (!down) {
               Long start = CHARGING.remove(id);
               if (start != null && isFire(p) && p.isAlive()) {
                  int held = (int)(now - start);
                  if (held < 5) {
                     punch(p);
                  } else {
                     stopCharge(p);
                     release(p, held);
                  }
               }
            } else if (isFire(p) && p.isAlive() && !p.isSpectator() && slot < 8) {
               if (!busy(p) && cooldown(p, slot) <= 0) {
                  switch (slot) {
                     case 0:
                        CHARGING.put(id, now);
                        break;
                     case 1:
                        FireArts.whip(p);
                        break;
                     case 2:
                        FireArts.daggers(p);
                        break;
                     case 3:
                        FireArts.breath(p);
                        break;
                     case 4:
                        Lightning.press(p);
                        break;
                     case 5:
                        Lightning.stance(p);
                        break;
                     case 6:
                        FireArts.blue(p);
                  }
               }
            }
         }
      }
   }

   static boolean busy(ServerPlayer p) {
      UUID id = p.getUUID();
      return HOLDS.containsKey(id) || CHARGING.containsKey(id) || Lightning.charging(p) || FireArts.breathing(p);
   }

   static void hold(ServerPlayer p, int ticks) {
      HOLDS.put(p.getUUID(), new Bending.Hold(p.position(), now + (long)ticks));
   }

   public static void leave(ServerPlayer p) {
      if (CHARGING.remove(p.getUUID()) != null) {
         stopCharge(p);
      }

      HOLDS.remove(p.getUUID());
      Lightning.leave(p);
      FireArts.leave(p);
      SHIELDS.remove(p.getUUID());
      Water.leave(p);
      Earth.leave(p);
      Air.leave(p);
   }

   private static void punch(ServerPlayer p) {
      if (Energy.has(p, 5)) {
         Elem.spend(p, 5);
         long[] c = COMBO.computeIfAbsent(p.getUUID(), k -> new long[]{-1L, 0L});
         c[0] = now - c[1] < 26L ? (c[0] + 1L) % 3L : 0L;
         c[1] = now;
         int step = (int)c[0];
         boolean kick = step == 2;
         if (kick) {
            setCooldown(p, 0, 60);
         } else {
            setCooldownRaw(p, 0, 6);
         }

         pose(p, step == 0 ? 1 : (step == 1 ? 2 : 3), kick ? 12 : 8);
         ServerLevel l = p.serverLevel();
         play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.35F, 1.6F + l.random.nextFloat() * 0.2F);
         Vec3 target = aim(p, 48.0);
         later(kick ? 3 : 2, () -> {
            if (p.isAlive() && !p.isRemoved()) {
               Vec3 from = kick ? foot(p) : fist(p, step == 0 ? 1 : -1);
               launch(p, from, dir(p, from, target), kick ? 0.62F : 0.42F, kick ? 1.6F : 1.8F, kick ? 9.0F : 6.0F, 24, 0.0F, kick ? 5 : 3, false);
            }
         });
      }
   }

   private static void release(ServerPlayer p, int held) {
      float k = Mth.clamp((float)(held - 5) / 35.0F, 0.0F, 1.0F);
      if (k >= 1.0F) {
         fullBlast(p);
      } else {
         int cost = 10 + Math.round(20.0F * k);
         if (Energy.has(p, cost)) {
            Elem.spend(p, cost);
            setCooldown(p, 0, 12);
            pose(p, 5, 13);
            Vec3 target = aim(p, 56.0);
            play(p.serverLevel(), p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.2F);
            later(
               3,
               () -> {
                  if (p.isAlive() && !p.isRemoved()) {
                     Vec3 from = fist(p, 0);
                     launch(
                        p,
                        from,
                        dir(p, from, target),
                        0.55F + 0.6F * k,
                        1.6F - 0.2F * k,
                        8.0F + 12.0F * k,
                        32,
                        k > 0.5F ? 2.2F * k : 0.0F,
                        4 + Math.round(3.0F * k),
                        false
                     );
                  }
               }
            );
         }
      }
   }

   private static void fullBlast(ServerPlayer p) {
      if (Energy.has(p, 40)) {
         Elem.spend(p, 40);
         setCooldown(p, 0, 70);
         ServerLevel l = p.serverLevel();
         Vec3 target = aim(p, 64.0);
         Vec3 look = p.getLookAngle();
         hold(p, 28);
         Net.fx(l, new Net.Fx((byte)7, p.getEyePosition(), look, 0.0F, p.getId(), 0, 38));
         pose(p, 4, 19);
         play(l, p.position(), SoundEvents.BLAZE_AMBIENT, 1.0F, 0.55F);
         play(l, p.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.8F, 0.7F);
         later(19, () -> {
            pose(p, 5, 18);
            play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.7F);
         });
         later(22, () -> {
            if (p.isAlive() && !p.isRemoved()) {
               Vec3 from = fist(p, 0);
               Vec3 d = target.distanceTo(from) < 2.0 ? look : target.subtract(from).normalize();
               launch(p, from, d, 1.5F, 1.25F, 26.0F, 46, 4.5F, 8, true);
               play(l, p.position(), (SoundEvent)AvatarMod.FIRE_ROAR.get(), 1.4F, 1.0F);
            }
         });
      }
   }

   static void launch(ServerPlayer p, Vec3 from, Vec3 dir, float size, float speed, float dmg, int life, float radius, int fireSecs, boolean big) {
      ServerLevel l = p.serverLevel();
      boolean blue = FireArts.isBlue(p);
      if (blue) {
         dmg *= 1.35F;
         speed *= 1.15F;
      }

      Bending.Blast b = new Bending.Blast(++serial, p, l, from, dir.scale((double)speed), size, dmg, life, radius, fireSecs, big, blue);
      BLASTS.add(b);
      Net.fx(l, new Net.Fx((byte)3, from, b.vel, size, p.getId(), b.serial, life, blue ? 1 : 0));
      if (big) {
         play(l, from, (SoundEvent)AvatarMod.FIREBALL.get(), 2.0F, 0.75F);
         play(l, from, (SoundEvent)AvatarMod.FIRE_BURST.get(), 1.6F, 0.7F);
      } else if (size < 0.3F) {
         play(l, from, (SoundEvent)AvatarMod.FIRE_PUNCH.get(), 0.7F, 1.25F + l.random.nextFloat() * 0.2F);
      } else {
         play(l, from, (SoundEvent)AvatarMod.FIRE_PUNCH.get(), 0.9F, 0.9F + l.random.nextFloat() * 0.3F);
         if (size > 0.5F) {
            play(l, from, (SoundEvent)AvatarMod.FIRE_BURST.get(), 0.7F, 1.1F);
         }
      }
   }

   static void launchElem(ServerPlayer p, Vec3 from, Vec3 dir, float size, float speed, float dmg, int life, float radius, int elem, double grav) {
      ServerLevel l = p.serverLevel();
      Bending.Blast b = new Bending.Blast(++serial, p, l, from, dir.scale((double)speed), size, dmg, life, radius, 0, false, false);
      b.elem = elem;
      b.grav = grav;
      BLASTS.add(b);
      Net.fx(l, new Net.Fx((byte)3, from, b.vel, size, p.getId(), b.serial, life, elem << 4));
   }

   private static void tickBlasts() {
      Iterator<Bending.Blast> it = BLASTS.iterator();

      while (it.hasNext()) {
         Bending.Blast b = it.next();
         if (b.level.getServer() != null && b.age++ <= b.life && !shielded(b)) {
            Vec3 from = b.pos;
            Vec3 to = b.pos.add(b.vel);
            BlockHitResult bh = b.level.clip(new ClipContext(from, to, Block.COLLIDER, Fluid.WATER, b.owner));
            double blockT = bh.getType() == Type.MISS ? 2.0 : from.distanceTo(bh.getLocation()) / b.vel.length();
            LivingEntity best = null;
            double bestT = 2.0;
            AABB sweep = new AABB(from, to).inflate((double)(b.size + 1.0F));

            for (Entity e : b.level.getEntities(b.owner, sweep, ex -> ex instanceof LivingEntity && ex.isAlive() && !ex.isSpectator())) {
               AABB box = e.getBoundingBox().inflate((double)b.size * 0.6);
               double t;
               if (box.contains(from)) {
                  t = 0.0;
               } else {
                  Optional<Vec3> hit = box.clip(from, to);
                  if (hit.isEmpty()) {
                     continue;
                  }

                  t = from.distanceTo(hit.get()) / b.vel.length();
               }

               if (t < bestT) {
                  bestT = t;
                  best = (LivingEntity)e;
               }
            }

            if (best != null && bestT <= blockT) {
               impact(b, from.add(b.vel.scale(bestT)), best);
               it.remove();
            } else if (blockT <= 1.0) {
               Vec3 at = bh.getLocation();
               FluidState fluid = b.level.getFluidState(bh.getBlockPos());
               if (!fluid.isEmpty()) {
                  Net.fx(b.level, new Net.Fx((byte)6, at, b.vel, b.size, 0, b.serial, 0, b.blue ? 1 : 0));
                  play(b.level, at, SoundEvents.FIRE_EXTINGUISH, 0.8F, 1.0F);
               } else {
                  impact(b, at.subtract(b.vel.normalize().scale(0.2)), null);
               }

               it.remove();
            } else {
               b.pos = to;
               if (b.grav != 0.0) {
                  b.vel = b.vel.add(0.0, -b.grav, 0.0);
               }
            }
         } else {
            Net.fx(b.level, new Net.Fx((byte)5, b.pos, b.vel, b.size, 0, b.serial, 0, b.flags()));
            it.remove();
         }
      }
   }

   private static boolean shielded(Bending.Blast b) {
      for (Entry<UUID, double[]> s : SHIELDS.entrySet()) {
         if (!(s.getValue()[1] < (double)now) && (b.owner == null || !s.getKey().equals(b.owner.getUUID()))) {
            Player o = b.level.getPlayerByUUID(s.getKey());
            if (o != null && o.position().add(0.0, 1.0, 0.0).distanceTo(b.pos) < s.getValue()[0]) {
               return true;
            }
         }
      }

      return false;
   }

   private static void impact(Bending.Blast b, Vec3 at, LivingEntity hit) {
      ServerLevel l = b.level;
      Vec3 dir = b.vel.normalize();
      if (b.elem != 0) {
         elemImpact(b, at, hit, dir);
      } else {
         if (hit != null) {
            hurt(b, hit, b.dmg);
            knock(hit, dir, b.big ? 1.4 : 0.45, b.big ? 0.5 : 0.18);
         }

         if (b.radius > 0.0F) {
            for (LivingEntity e : l.getEntitiesOfClass(
               LivingEntity.class, new AABB(at, at).inflate((double)b.radius), ex -> ex != b.owner && ex != hit && ex.isAlive() && !ex.isSpectator()
            )) {
               double d = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).distanceTo(at);
               if (d < (double)b.radius) {
                  float fall = (float)(1.0 - d / (double)b.radius);
                  hurt(b, e, b.dmg * 0.65F * fall);
                  Vec3 away = e.position().subtract(at).normalize();
                  knock(e, away, 1.1 * (double)fall, 0.35 * (double)fall);
               }
            }
         }

         if (b.big && l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            for (int i = 0; i < 14; i++) {
               BlockPos pos = BlockPos.containing(at.add(l.random.nextGaussian() * 2.2, l.random.nextGaussian() * 1.2, l.random.nextGaussian() * 2.2));
               if (l.isEmptyBlock(pos) && BaseFireBlock.canBePlacedAt(l, pos, Direction.UP)) {
                  l.setBlockAndUpdate(pos, BaseFireBlock.getState(l, pos));
               }
            }
         }

         Net.fx(l, new Net.Fx((byte)4, at, dir, b.big ? b.size * 2.6F : b.size, 0, b.serial, 0, b.blue ? 1 : 0));
         if (b.big) {
            play(l, at, (SoundEvent)AvatarMod.FIRE_RUMBLE.get(), 2.5F, 0.8F);
            play(l, at, (SoundEvent)AvatarMod.CRACK.get(), 1.8F, 0.8F);
            play(l, at, SoundEvents.GENERIC_EXPLODE, 1.2F, 0.7F);
         } else {
            play(l, at, (SoundEvent)AvatarMod.FIRE_BURST.get(), 0.6F, 1.2F + l.random.nextFloat() * 0.3F);
            play(l, at, SoundEvents.FIRECHARGE_USE, 0.4F, 1.3F);
         }
      }
   }

   private static void elemImpact(Bending.Blast b, Vec3 at, LivingEntity hit, Vec3 dir) {
      ServerLevel l = b.level;
      List<LivingEntity> victims = new ArrayList<>();
      if (hit != null) {
         victims.add(hit);
      }

      if (b.radius > 0.0F) {
         victims.addAll(
            l.getEntitiesOfClass(
               LivingEntity.class, new AABB(at, at).inflate((double)b.radius), ex -> ex != b.owner && ex != hit && ex.isAlive() && !ex.isSpectator()
            )
         );
      }

      for (LivingEntity e : victims) {
         float k = e == hit ? 1.0F : 0.6F;
         if (b.owner != null) {
            hit(b.owner, e, b.dmg * k);
         }

         switch (b.elem) {
            case 1:
               e.clearFire();
               knock(e, dir, 0.9 * (double)k, 0.25);
               break;
            case 2:
               knock(e, e == hit ? dir : e.position().subtract(at).normalize(), 1.3 * (double)k, 0.45 * (double)k);
               break;
            case 3:
               knock(e, dir, 1.6 * (double)k, 0.35);
               break;
            case 4:
               e.setTicksFrozen(Math.max(e.getTicksFrozen(), 200));
               e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 2, false, false));
               knock(e, dir, 0.25, 0.05);
         }
      }

      Net.fx(l, new Net.Fx((byte)4, at, dir, b.size, 0, b.serial, 0, b.flags()));
      switch (b.elem) {
         case 1:
            play(l, at, SoundEvents.GENERIC_SPLASH, 1.0F, 1.1F);
            break;
         case 2:
            play(l, at, SoundEvents.STONE_BREAK, 1.5F, 0.6F);
            play(l, at, (SoundEvent)AvatarMod.FIRE_RUMBLE.get(), 1.2F, 1.3F);
            break;
         case 3:
            play(l, at, (SoundEvent)AvatarMod.FIRE_WHOOSH.get(), 0.8F, 1.6F);
            break;
         case 4:
            play(l, at, SoundEvents.GLASS_BREAK, 0.9F, 1.3F);
      }
   }

   static void hit(ServerPlayer p, LivingEntity e, float dmg) {
      e.invulnerableTime = 0;
      e.hurt(p.damageSources().playerAttack(p), dmg);
   }

   private static void hurt(Bending.Blast b, LivingEntity e, float dmg) {
      e.invulnerableTime = 0;
      e.hurt(b.owner != null && b.owner.isAlive() ? b.level.damageSources().playerAttack(b.owner) : b.level.damageSources().onFire(), dmg);
      e.setSecondsOnFire(b.fireSecs);
   }

   static void burn(ServerPlayer p, LivingEntity e, float dmg, int fireSecs) {
      e.invulnerableTime = 0;
      e.hurt(p.damageSources().playerAttack(p), dmg);
      if (fireSecs > 0) {
         e.setSecondsOnFire(fireSecs);
      }
   }

   static void knock(LivingEntity e, Vec3 dir, double h, double up) {
      e.setDeltaMovement(e.getDeltaMovement().add(dir.x * h, up, dir.z * h));
      e.hurtMarked = true;
   }

   static Vec3 flatRight(Player p) {
      Vec3 l = p.getLookAngle();
      Vec3 r = new Vec3(-l.z, 0.0, l.x);
      return r.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : r.normalize();
   }

   static Vec3 fist(Player p, int side) {
      return p.getEyePosition().add(p.getLookAngle().scale(0.6)).add(flatRight(p).scale(0.3 * (double)side)).add(0.0, -0.32, 0.0);
   }

   static Vec3 foot(Player p) {
      Vec3 l = p.getLookAngle();
      Vec3 flat = new Vec3(l.x, 0.0, l.z).normalize();
      return p.position().add(flat.scale(0.8)).add(flatRight(p).scale(0.15)).add(0.0, 0.95, 0.0);
   }

   static Vec3 aim(ServerPlayer p, double range) {
      Vec3 eye = p.getEyePosition();
      Vec3 end = eye.add(p.getLookAngle().scale(range));
      BlockHitResult bh = p.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, p));
      if (bh.getType() != Type.MISS) {
         end = bh.getLocation();
      }

      EntityHitResult eh = ProjectileUtil.getEntityHitResult(
         p.level(), p, eye, end, new AABB(eye, end).inflate(1.0), e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator()
      );
      return eh != null ? eh.getEntity().getBoundingBox().getCenter() : end;
   }

   static Vec3 dir(ServerPlayer p, Vec3 from, Vec3 target) {
      return target.distanceTo(from) < 2.5 ? p.getLookAngle() : target.subtract(from).normalize();
   }

   static void pose(ServerPlayer p, int pose, int dur) {
      Net.fx(p.serverLevel(), new Net.Fx((byte)0, p.position(), Vec3.ZERO, (float)pose, p.getId(), 0, dur));
   }

   private static void stopCharge(ServerPlayer p) {
      Net.fx(p.serverLevel(), new Net.Fx((byte)1, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
   }

   static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
      l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
   }

   static void later(int ticks, Runnable r) {
      TIMED.add(new Bending.Timed(now + (long)ticks, r));
   }

   private static UUID cdKey(Player p) {
      return new UUID(p.getUUID().getMostSignificantBits(), p.getUUID().getLeastSignificantBits() ^ (long)Sorcerers.get(p).ordinal());
   }

   static int cooldown(Player p, int slot) {
      long[] c = COOLDOWNS.get(cdKey(p));
      return c == null ? 0 : (int)Math.max(0L, c[slot] - now);
   }

   static void setCooldown(Player p, int slot, int ticks) {
      setCooldownRaw(p, slot, Math.max(40, ticks));
   }

   private static void setCooldownRaw(Player p, int slot, int ticks) {
      COOLDOWNS.computeIfAbsent(cdKey(p), k -> new long[10])[slot] = now + (long)ticks;
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent e) {
      if (e.phase == Phase.START) {
         now = e.getServer().overworld().getGameTime();
      } else {
         for (Bending.Timed t : new ArrayList<>(TIMED)) {
            if (now >= t.at) {
               TIMED.remove(t);
               t.r.run();
            }
         }

         tickBlasts();
         TempBlocks.tick(now);
         SHIELDS.values().removeIf(s -> s[1] < (double)now);
         Water.tick(e.getServer());
         Air.tick(e.getServer());
         Earth.tick(e.getServer());
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent e) {
      if (e.phase == Phase.END && e.player instanceof ServerPlayer p) {
         UUID var7 = p.getUUID();
         Long start = CHARGING.get(var7);
         if (start != null) {
            if (isFire(p) && p.isAlive()) {
               int held = (int)(now - start);
               ServerLevel l = p.serverLevel();
               if (held == 5) {
                  Net.fx(l, new Net.Fx((byte)1, p.position(), Vec3.ZERO, 1.0F, p.getId(), 0, 35));
                  pose(p, 4, 200);
                  play(l, p.position(), SoundEvents.BLAZE_AMBIENT, 0.6F, 0.7F);
               }

               if (held >= 5) {
                  p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 2, false, false, false));
                  if (held % 14 == 0 && held < 40) {
                     play(l, p.position(), SoundEvents.FIRE_AMBIENT, 0.8F, 0.8F + (float)held / 40.0F * 0.6F);
                  }
               }

               if (held == 40) {
                  Net.fx(l, new Net.Fx((byte)2, p.position(), Vec3.ZERO, 1.0F, p.getId(), 0, 0));
                  play(l, p.position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.9F, 1.3F);
                  play(l, p.position(), SoundEvents.BLAZE_SHOOT, 0.5F, 0.5F);
               }

               if (held >= 200) {
                  CHARGING.remove(var7);
                  stopCharge(p);
                  release(p, held);
               }
            } else {
               leave(p);
            }
         }

         Lightning.tickPlayer(p);
         FireArts.tickPlayer(p);
         Water.tickPlayer(p);
         Earth.tickPlayer(p);
         Air.tickPlayer(p);
         Bending.Hold h = HOLDS.get(var7);
         if (h != null) {
            if (now <= h.until && p.isAlive()) {
               if (p.position().distanceToSqr(h.pos) > 0.01) {
                  p.teleportTo(h.pos.x, h.pos.y, h.pos.z);
               }

               p.setDeltaMovement(Vec3.ZERO);
            } else {
               HOLDS.remove(var7);
            }
         }

         if (p.tickCount % 2 == 0 && isBender(p)) {
            int[] cds = new int[10];

            for (int i = 0; i < 10; i++) {
               cds[i] = cooldown(p, i);
            }

            Long s = CHARGING.get(var7);
            Net.CHANNEL
               .send(
                  PacketDistributor.PLAYER.with(() -> p),
                  new Net.State(s == null ? -1 : (int)(now - s), Lightning.chargeTicks(p), FireArts.blueLeft(p), Lightning.stanceLeft(p), cds)
               );
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent e) {
      if (e.getEntity() instanceof ServerPlayer p) {
         leave(p);
         COOLDOWNS.keySet().removeIf(k -> k.getMostSignificantBits() == p.getUUID().getMostSignificantBits());
         COMBO.remove(p.getUUID());
      }
   }

   @SubscribeEvent
   public static void onStopping(ServerStoppingEvent e) {
      CHARGING.clear();
      COOLDOWNS.clear();
      COMBO.clear();
      HOLDS.clear();
      TIMED.clear();
      BLASTS.clear();
      Lightning.clear();
      FireArts.clear();
      SHIELDS.clear();
      TempBlocks.restoreAll();
      Water.clear();
      Earth.clear();
      Air.clear();
   }

   private static final class Blast {
      final int serial;
      final int life;
      final int fireSecs;
      final ServerPlayer owner;
      final ServerLevel level;
      Vec3 vel;
      int elem;
      double grav;
      final float size;
      final float dmg;
      final float radius;
      final boolean big;
      final boolean blue;
      Vec3 pos;
      int age;

      int flags() {
         return (this.blue ? 1 : 0) | this.elem << 4;
      }

      Blast(
         int serial,
         ServerPlayer owner,
         ServerLevel level,
         Vec3 pos,
         Vec3 vel,
         float size,
         float dmg,
         int life,
         float radius,
         int fireSecs,
         boolean big,
         boolean blue
      ) {
         this.blue = blue;
         this.serial = serial;
         this.owner = owner;
         this.level = level;
         this.pos = pos;
         this.vel = vel;
         this.size = size;
         this.dmg = dmg;
         this.life = life;
         this.radius = radius;
         this.fireSecs = fireSecs;
         this.big = big;
      }
   }

   static record Hold(Vec3 pos, long until) {
   }

   private static record Timed(long at, Runnable r) {
   }
}
