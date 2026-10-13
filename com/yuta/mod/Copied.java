package com.yuta.mod;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Copied {
   public static final int FREEZE_TICKS = 50;
   public static final int CLAIR_TICKS = 200;
   public static final int CLAIR_DODGES = 3;
   private static final Map<Integer, Copied.Frozen> FROZEN = new HashMap<>();
   private static final Map<UUID, long[]> CLAIR = new HashMap<>();
   private static final List<Copied.Shiki> SHIKI = new ArrayList<>();
   private static long now;

   private Copied() {
   }

   static boolean speech(ServerLevel l, ServerPlayer p) {
      Vec3 eye = p.getEyePosition();
      Vec3 look = p.getLookAngle();
      double range = 14.0;
      Yuta.pose(l, p, 6, 18);
      Net.fx(l, new Net.Fx((byte)8, eye.subtract(0.0, 0.25, 0.0).add(look.scale(0.3)), look, (float)range, p.getId(), -1, 26));
      Yuta.play(l, p.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 1.6F);
      Yuta.play(l, p.position(), SoundEvents.WARDEN_SONIC_BOOM, 0.5F, 1.9F);
      int caught = 0;

      for (LivingEntity e : l.getEntitiesOfClass(
         LivingEntity.class, p.getBoundingBox().inflate(range), ex -> ex != p && ex.isAlive() && !ex.isSpectator() && !(ex instanceof RikaEntity)
      )) {
         Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
         if (!(to.length() > range) && !(to.normalize().dot(look) < Math.cos(Math.toRadians(35.0))) && p.hasLineOfSight(e)) {
            freeze(l, e, 50);
            caught++;
         }
      }

      if (caught == 0) {
         Yuta.say(p, "Nobody heard you.", ChatFormatting.GRAY);
      }

      return true;
   }

   static void freeze(ServerLevel l, LivingEntity e, int ticks) {
      FROZEN.put(e.getId(), new Copied.Frozen(e, e.position(), now + (long)ticks));
      e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks, 9, false, false, false));
      e.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 128, false, false, false));
      if (e instanceof Mob m) {
         m.getNavigation().stop();
      }

      Net.fx(l, new Net.Fx((byte)9, e.position(), Vec3.ZERO, e.getBbWidth(), -1, e.getId(), ticks));
      if (e instanceof ServerPlayer sp) {
         sp.displayClientMessage(Component.literal("動くな  -  You can't move.").withStyle(ChatFormatting.DARK_PURPLE), true);
      }
   }

   public static boolean frozen(Entity e) {
      Copied.Frozen f = FROZEN.get(e.getId());
      return f != null && f.until > now;
   }

   static boolean dismantle(ServerLevel l, ServerPlayer p) {
      Yuta.pose(l, p, 1, 10);

      for (int k = 0; k < 3; k++) {
         int idx = k;
         Yuta.later(
            k * 3,
            () -> {
               if (p.isAlive()) {
                  Vec3 eye = p.getEyePosition();
                  Vec3 dir = p.getLookAngle();
                  double range = 24.0;
                  BlockHitResult bh = l.clip(new ClipContext(eye, eye.add(dir.scale(range)), Block.COLLIDER, Fluid.NONE, p));
                  double len = bh.getType() == Type.MISS ? range : bh.getLocation().distanceTo(eye);
                  Vec3 end = eye.add(dir.scale(len));
                  Net.fx(l, new Net.Fx((byte)10, eye.subtract(0.0, 0.2, 0.0), dir, (float)len, p.getId(), idx, 8));
                  Yuta.play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.6F + (float)idx * 0.1F);

                  for (LivingEntity e : l.getEntitiesOfClass(
                     LivingEntity.class, new AABB(eye, end).inflate(1.2), ex -> ex != p && ex.isAlive() && !ex.isSpectator() && !(ex instanceof RikaEntity)
                  )) {
                     if (!e.getBoundingBox().inflate(0.6).clip(eye, end).isEmpty()) {
                        Yuta.hurt(p, e, 7.0F);
                        Net.fx(l, new Net.Fx((byte)1, e.getBoundingBox().getCenter(), dir.cross(new Vec3(0.0, 1.0, 0.0)), 1.0F, p.getId(), e.getId(), 0));
                        Yuta.play(l, e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 1.5F);
                     }
                  }
               }
            }
         );
      }

      return true;
   }

   static boolean cleave(ServerLevel l, ServerPlayer p) {
      LivingEntity t = Yuta.target(p, 6.0);
      if (t == null) {
         Yuta.say(p, "Cleave needs someone within reach.", ChatFormatting.GRAY);
         return false;
      } else {
         Yuta.pose(l, p, 7, 14);
         Net.fx(l, new Net.Fx((byte)11, t.getBoundingBox().getCenter(), p.getLookAngle(), Math.max(t.getBbWidth(), t.getBbHeight()), p.getId(), t.getId(), 14));
         float dmg = 8.0F + t.getMaxHealth() * 0.1F;

         for (int k = 0; k < 4; k++) {
            int idx = k;
            Yuta.later(2 + k * 2, () -> {
               if (t.isAlive()) {
                  Yuta.hurt(p, t, dmg / 4.0F);
                  Yuta.play(l, t.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 1.2F + (float)idx * 0.15F);
               }
            });
         }

         Yuta.play(l, t.position(), SoundEvents.WARDEN_ATTACK_IMPACT, 0.6F, 1.6F);
         return true;
      }
   }

   static boolean iceBreaker(ServerLevel l, ServerPlayer p) {
      Vec3 look = p.getLookAngle();
      Vec3 point = p.getEyePosition().add(look.scale(2.5));
      Yuta.hold(p, 12);
      Yuta.pose(l, p, 4, 10);
      Net.fx(l, new Net.Fx((byte)12, point, look, 0.0F, p.getId(), -1, 12));
      Yuta.play(l, point, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.5F);
      Yuta.play(l, point, SoundEvents.GLASS_BREAK, 0.6F, 0.4F);
      Yuta.later(
         10,
         () -> {
            if (p.isAlive()) {
               Vec3 lk = p.getLookAngle();
               Vec3 at = p.getEyePosition().add(lk.scale(2.5));
               Yuta.pose(l, p, 7, 12);
               Net.fx(l, new Net.Fx((byte)12, at, lk, 1.0F, p.getId(), -1, 14));
               Yuta.play(l, at, SoundEvents.GLASS_BREAK, 1.6F, 0.6F);
               Yuta.play(l, at, SoundEvents.GENERIC_EXPLODE, 0.8F, 1.4F);

               for (LivingEntity e : l.getEntitiesOfClass(
                  LivingEntity.class, new AABB(at, at).inflate(4.0), ex -> ex != p && ex.isAlive() && !ex.isSpectator() && !(ex instanceof RikaEntity)
               )) {
                  Vec3 to = e.getBoundingBox().getCenter().subtract(p.getEyePosition());
                  if (!(to.length() > 6.5) && !(to.normalize().dot(lk) < 0.35)) {
                     Yuta.hurt(p, e, 10.0F);
                     Vec3 f = Yuta.flat(lk);
                     e.setDeltaMovement(f.x * 3.2, 0.9, f.z * 3.2);
                     e.hurtMarked = true;
                     Net.fx(l, new Net.Fx((byte)1, e.getBoundingBox().getCenter(), lk, 1.6F, p.getId(), e.getId(), 0));
                  }
               }

               for (Projectile pr : l.getEntitiesOfClass(Projectile.class, new AABB(at, at).inflate(4.0), prx -> prx.getOwner() != p)) {
                  pr.setDeltaMovement(lk.scale(2.0));
                  pr.hurtMarked = true;
               }
            }
         }
      );
      return true;
   }

   static boolean clairvoyance(ServerLevel l, ServerPlayer p) {
      CLAIR.put(p.getUUID(), new long[]{now + 200L, 3L});
      Yuta.pose(l, p, 9, 20);
      Net.fx(l, new Net.Fx((byte)13, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 200));
      Yuta.play(l, p.position(), SoundEvents.BOOK_PAGE_TURN, 2.0F, 0.8F);
      Yuta.play(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 1.2F);
      return true;
   }

   @SubscribeEvent(
      priority = EventPriority.HIGH
   )
   public static void onAttack(LivingAttackEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && e.getSource().getEntity() != null) {
         long[] c = CLAIR.get(p.getUUID());
         if (c != null && c[0] >= now && c[1] > 0L) {
            Entity src = e.getSource().getDirectEntity() != null ? e.getSource().getDirectEntity() : e.getSource().getEntity();
            Vec3 from = p.position();
            Vec3 away = Yuta.flat(from.subtract(src.position()));
            Vec3 side = away.cross(new Vec3(0.0, 1.0, 0.0)).scale((p.tickCount & 1) == 0 ? 1.0 : -1.0);
            Vec3 to = from.add(side.scale(2.2)).add(away.scale(0.8));
            ServerLevel l = p.serverLevel();
            if (l.noCollision(p, p.getBoundingBox().move(to.subtract(from)))) {
               p.connection.teleport(to.x, to.y, to.z, p.getYRot(), p.getXRot());
            }

            e.setCanceled(true);
            c[1]--;
            if (src instanceof Projectile pr) {
               pr.discard();
            }

            Net.fx(l, new Net.Fx((byte)14, from, to, (float)c[1], p.getId(), -1, 10));
            Yuta.play(l, from, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 1.8F);
            if (c[1] <= 0L) {
               CLAIR.remove(p.getUUID());
               Net.fx(l, new Net.Fx((byte)13, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 0));
            }

            return;
         }

         return;
      }
   }

   static boolean dhruv(ServerLevel l, ServerPlayer p) {
      LivingEntity t = Yuta.target(p, 30.0);
      Vec3 c;
      if (t != null) {
         c = t.position();
      } else {
         Vec3 eye = p.getEyePosition();
         BlockHitResult bh = l.clip(new ClipContext(eye, eye.add(p.getLookAngle().scale(14.0)), Block.COLLIDER, Fluid.NONE, p));
         c = bh.getType() != Type.MISS ? bh.getLocation() : eye.add(p.getLookAngle().scale(10.0)).subtract(0.0, 1.6, 0.0);
      }

      int seed = l.random.nextInt(1000);
      float r = 6.0F;
      Yuta.pose(l, p, 4, 14);
      Net.fx(l, new Net.Fx((byte)15, c, Vec3.ZERO, r, p.getId(), seed, 200));
      Yuta.play(l, c, SoundEvents.PHANTOM_FLAP, 2.0F, 1.4F);
      Yuta.play(l, p.position(), SoundEvents.EVOKER_PREPARE_SUMMON, 1.0F, 1.3F);
      SHIKI.add(new Copied.Shiki(l, p, c, r, seed, now));
      return true;
   }

   private static void tickShiki(Copied.Shiki s, Iterator<Copied.Shiki> it) {
      float t = (float)(now - s.start);
      if (!(t > 200.0F) && s.owner.isAlive() && s.owner.level() == s.l) {
         if ((int)t % 3 == 0) {
            for (int i = 0; i < 2; i++) {
               Vec3 head = DhruvPath.pos(s.c, s.r, s.seed, i, t);
               AABB box = new AABB(head, head).inflate((double)s.r * 0.6);

               for (LivingEntity e : s.l
                  .getEntitiesOfClass(
                     LivingEntity.class, box.inflate(1.0), ex -> ex != s.owner && ex.isAlive() && !ex.isSpectator() && !(ex instanceof RikaEntity)
                  )) {
                  Long last = s.lastHit.get(e.getId());
                  if (last == null || now - last >= 10L) {
                     for (int k = 0; k < 26; k += 2) {
                        Vec3 a = DhruvPath.pos(s.c, s.r, s.seed, i, Math.max(0.0F, t - (float)k));
                        Vec3 b = DhruvPath.pos(s.c, s.r, s.seed, i, Math.max(0.0F, t - (float)k - 2.0F));
                        if (!e.getBoundingBox().inflate(0.5).clip(a, b).isEmpty() || e.getBoundingBox().inflate(0.5).contains(a)) {
                           s.lastHit.put(e.getId(), now);
                           Yuta.hurt(s.owner, e, 5.0F);
                           Net.fx(s.l, new Net.Fx((byte)1, e.getBoundingBox().getCenter(), b.subtract(a).normalize(), 0.8F, s.owner.getId(), e.getId(), 0));
                           Yuta.play(s.l, e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.7F, 1.7F);
                           break;
                        }
                     }
                  }
               }
            }
         }
      } else {
         it.remove();
      }
   }

   @SubscribeEvent
   public static void onDeath(LivingDeathEvent e) {
      if (e.getEntity() instanceof ServerPlayer victim && e.getSource().getEntity() instanceof RikaEntity r) {
         ServerPlayer var6 = r.owner();
         Sorcerer s = Sorcerers.get(victim);
         if (var6 != null && s != Sorcerer.NONE && s != Sorcerer.YUTA) {
            var6.sendSystemMessage(
               Component.literal("Rika consumed " + victim.getName().getString() + ". Copy: " + s.title + " (" + s.displayName + ").")
                  .withStyle(ChatFormatting.LIGHT_PURPLE)
            );
            victim.sendSystemMessage(Component.literal("Rika consumed you. Yuta has copied your technique.").withStyle(ChatFormatting.DARK_PURPLE));
            return;
         }

         return;
      }
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent e) {
      if (e.phase == Phase.END) {
         now = e.getServer().overworld().getGameTime();
         Iterator<Entry<Integer, Copied.Frozen>> it = FROZEN.entrySet().iterator();

         while (it.hasNext()) {
            Copied.Frozen f = it.next().getValue();
            LivingEntity en = f.e;
            if (f.until >= now && en.isAlive()) {
               if (en.position().distanceToSqr(f.at) > 0.0025) {
                  if (en instanceof ServerPlayer sp) {
                     sp.connection.teleport(f.at.x, f.at.y, f.at.z, sp.getYRot(), sp.getXRot());
                  } else {
                     en.teleportTo(f.at.x, f.at.y, f.at.z);
                  }
               }

               en.setDeltaMovement(0.0, Math.min(0.0, en.getDeltaMovement().y), 0.0);
               if (en instanceof Mob m) {
                  m.getNavigation().stop();
               }
            } else {
               it.remove();
            }
         }

         it = SHIKI.iterator();

         while (it.hasNext()) {
            tickShiki((Copied.Shiki)it.next(), it);
         }

         CLAIR.values().removeIf(c -> c[0] < now);
      }
   }

   @SubscribeEvent
   public static void onStop(ServerStoppingEvent e) {
      FROZEN.clear();
      CLAIR.clear();
      SHIKI.clear();
   }

   private static final class Frozen {
      final LivingEntity e;
      final Vec3 at;
      final long until;

      Frozen(LivingEntity e, Vec3 at, long until) {
         this.e = e;
         this.at = at;
         this.until = until;
      }
   }

   private static final class Shiki {
      final ServerLevel l;
      final ServerPlayer owner;
      final Vec3 c;
      final float r;
      final int seed;
      final long start;
      final Map<Integer, Long> lastHit = new HashMap<>();

      Shiki(ServerLevel l, ServerPlayer owner, Vec3 c, float r, int seed, long start) {
         this.l = l;
         this.owner = owner;
         this.c = c;
         this.r = r;
         this.seed = seed;
         this.start = start;
      }
   }
}
