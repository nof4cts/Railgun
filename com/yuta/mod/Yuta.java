package com.yuta.mod;

import com.jjk.mod.Energy;
import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
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
public final class Yuta {
   public static final int SLOTS = 9;
   public static final int CALL_CUT = 44;
   public static final int CALL_HIT = 20;
   public static final int FULL_TICKS = 6000;
   public static final int FULL_CUT = 60;
   public static final int FULL_AT = 40;
   private static final int[] COST = new int[]{10, 40, 40, 40, 40, 40, 40, 40, 50};
   private static final int[] COOLDOWN = new int[]{7, 300, 400, 600, 800, 300, 900, 1200, 8400};
   private static final Map<UUID, Long> FULL = new HashMap<>();
   private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
   private static final Map<UUID, long[]> COMBO = new HashMap<>();
   private static final Map<UUID, RikaEntity> RIKA = new HashMap<>();
   private static final Map<UUID, Long> RIKA_BACK = new HashMap<>();
   private static final Map<UUID, Long> HOLD = new HashMap<>();
   private static final List<Yuta.Timed> TIMED = new ArrayList<>();
   private static long now;

   private Yuta() {
   }

   public static boolean isYuta(Player p) {
      return Sorcerers.is(p, Sorcerer.YUTA);
   }

   public static void useKey(ServerPlayer p, int slot) {
      if (slot >= 0 && slot < 9 && p.isAlive() && !p.isSpectator() && isYuta(p)) {
         long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), k -> new long[9]);
         if (cd[slot] <= now && HOLD.getOrDefault(p.getUUID(), 0L) <= now) {
            if (slot == 0 && !hasKatana(p)) {
               say(p, "Hold your katana to cut with cursed energy.", ChatFormatting.GRAY);
            } else if (Energy.has(p, COST[slot])) {
               ServerLevel l = p.serverLevel();

               boolean ok = switch (slot) {
                  case 0 -> slash(l, p);
                  case 1 -> callRika(l, p);
                  case 2 -> Copied.speech(l, p);
                  case 3 -> Copied.dismantle(l, p);
                  case 4 -> Copied.cleave(l, p);
                  case 5 -> Copied.iceBreaker(l, p);
                  case 6 -> Copied.clairvoyance(l, p);
                  case 7 -> Copied.dhruv(l, p);
                  default -> fullManifestation(l, p);
               };
               if (ok) {
                  if (!fullActive(p)) {
                     Energy.set(p, Energy.get(p) - COST[slot]);
                  }

                  cd[slot] = now + (long)COOLDOWN[slot];
               }
            }
         }
      }
   }

   public static boolean hasKatana(Player p) {
      return p.getMainHandItem().is((Item)YutaMod.KATANA.get());
   }

   static boolean slash(ServerLevel l, ServerPlayer p) {
      long[] c = COMBO.computeIfAbsent(p.getUUID(), k -> new long[]{-1L, 0L});
      c[0] = now - c[1] < 24L ? (c[0] + 1L) % 3L : 0L;
      c[1] = now;
      int step = (int)c[0];
      boolean heavy = step == 2;
      double reach = heavy ? 5.2 : 4.4;
      float dmg = heavy ? 11.0F : 7.0F;
      Vec3 look = p.getLookAngle();
      p.swing(InteractionHand.MAIN_HAND, true);
      Vec3 lunge = flat(look).scale(heavy ? 0.85 : 0.35);
      p.setDeltaMovement(p.getDeltaMovement().add(lunge.x, p.onGround() ? 0.04 : 0.0, lunge.z));
      p.hurtMarked = true;
      pose(l, p, step == 0 ? 1 : (step == 1 ? 2 : 3), heavy ? 10 : 7);
      Net.fx(l, new Net.Fx((byte)0, p.position(), look, heavy ? 1.4F : 1.0F, p.getId(), step, heavy ? 9 : 7));
      play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, heavy ? 0.7F : 1.15F - (float)step * 0.1F);
      play(l, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 0.35F, 1.9F);
      Vec3 eye = p.getEyePosition();
      boolean any = false;

      for (LivingEntity e : l.getEntitiesOfClass(
         LivingEntity.class, p.getBoundingBox().inflate(reach + 1.0), ex -> ex != p && ex.isAlive() && !ex.isSpectator() && !(ex instanceof RikaEntity)
      )) {
         Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
         if (!(to.length() > reach + (double)(e.getBbWidth() / 2.0F)) && !(to.normalize().dot(look) < (heavy ? 0.45 : 0.3))) {
            any = true;
            hurt(p, e, dmg);
            knock(e, flat(to), heavy ? 1.2 : 0.45, heavy ? 0.35 : 0.15);
            Vec3 cutDir = step == 0
               ? flat(look).cross(new Vec3(0.0, 1.0, 0.0))
               : (step == 1 ? flat(look).cross(new Vec3(0.0, -1.0, 0.0)) : new Vec3(0.0, -1.0, 0.0));
            Net.fx(l, new Net.Fx((byte)1, e.getBoundingBox().getCenter(), cutDir, heavy ? 1.6F : 1.0F, p.getId(), e.getId(), 0));
            play(l, e.position(), SoundEvents.PLAYER_ATTACK_CRIT, 0.9F, heavy ? 0.7F : 1.2F);
         }
      }

      if (heavy && any) {
         play(l, p.position(), SoundEvents.WARDEN_ATTACK_IMPACT, 0.7F, 1.4F);
      }

      return true;
   }

   static boolean callRika(ServerLevel l, ServerPlayer p) {
      RikaEntity r = rika(p, true);
      if (r == null) {
         return false;
      } else {
         Vec3 look = p.getLookAngle();
         LivingEntity target = target(p, 28.0);
         Vec3 at;
         if (target != null) {
            at = target.position();
         } else {
            Vec3 eye = p.getEyePosition();
            BlockHitResult bh = l.clip(new ClipContext(eye, eye.add(look.scale(14.0)), Block.COLLIDER, Fluid.NONE, p));
            at = bh.getType() != Type.MISS ? bh.getLocation() : eye.add(look.scale(10.0));
            BlockHitResult down = l.clip(new ClipContext(at.add(0.0, 1.0, 0.0), at.add(0.0, -8.0, 0.0), Block.COLLIDER, Fluid.NONE, p));
            if (down.getType() != Type.MISS) {
               at = down.getLocation();
            }
         }

         Vec3 smashAt = at;
         int tid = target == null ? -1 : target.getId();
         Vec3 dir = flat(smashAt.subtract(p.position()));
         Vec3 hover = smashAt.subtract(dir.scale(2.6)).add(0.0, 0.4, 0.0);
         hold(p, 24);
         pose(l, p, 4, 22);
         Net.fx(l, new Net.Fx((byte)3, smashAt, dir, (float)tid, p.getId(), r.getId(), 44));
         Net.fx(l, new Net.Fx((byte)7, p.position(), Vec3.ZERO, 1.0F, p.getId(), -1, 30));
         play(l, p.position(), SoundEvents.SOUL_ESCAPE, 2.0F, 0.6F);
         play(l, r.position(), SoundEvents.WARDEN_ROAR, 1.6F, 1.35F);
         play(l, r.position(), SoundEvents.GHAST_SCREAM, 0.9F, 0.45F);
         r.anim((byte)112);
         TIMED.add(new Yuta.Timed(4, () -> {
            r.order(hover, smashAt.subtract(hover), 40);
            r.anim((byte)110);
            play(l, r.position(), SoundEvents.PHANTOM_FLAP, 2.0F, 0.4F);
         }));
         TIMED.add(new Yuta.Timed(14, () -> r.anim((byte)111)));
         TIMED.add(
            new Yuta.Timed(
               20,
               () -> {
                  float radius = 4.5F;
                  Net.fx(l, new Net.Fx((byte)4, smashAt, dir, radius, p.getId(), tid, 26));
                  play(l, smashAt, SoundEvents.GENERIC_EXPLODE, 1.6F, 0.6F);
                  play(l, smashAt, SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 0.6F);
                  play(l, smashAt, SoundEvents.ANVIL_LAND, 1.2F, 0.45F);

                  for (LivingEntity e : l.getEntitiesOfClass(
                     LivingEntity.class,
                     new AABB(smashAt, smashAt).inflate((double)radius, 3.0, (double)radius),
                     ex -> ex != p && ex.isAlive() && !ex.isSpectator() && !(ex instanceof RikaEntity)
                  )) {
                     double d = e.position().distanceTo(smashAt);
                     if (!(d > (double)radius + 0.6)) {
                        float dmg = e.getId() != tid && !(d < 1.6) ? 14.0F * (float)(1.0 - d / (double)(radius + 1.0F)) + 4.0F : 36.0F;
                        hurt(p, e, dmg);
                        e.setDeltaMovement(e.getDeltaMovement().add(0.0, d < 1.6 ? -0.3 : 0.75, 0.0));
                        e.hurtMarked = true;
                        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
                     }
                  }
               }
            )
         );
         return true;
      }
   }

   public static boolean fullActive(Player p) {
      Long t = FULL.get(p.getUUID());
      return t != null && t > now;
   }

   static boolean fullManifestation(ServerLevel l, ServerPlayer p) {
      RikaEntity r = rika(p, true);
      if (r == null) {
         return false;
      } else {
         hold(p, 46);
         pose(l, p, 8, 40);
         Net.fx(l, new Net.Fx((byte)16, p.position(), p.getLookAngle(), 0.0F, p.getId(), r.getId(), 60));
         play(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 2.0F, 0.6F);
         play(l, p.position(), SoundEvents.SOUL_ESCAPE, 2.0F, 0.4F);
         later(40, () -> {
            if (p.isAlive() && r.isAlive()) {
               FULL.put(p.getUUID(), now + 6000L);
               r.setFull(true);
               r.anim((byte)112);
               Energy.set(p, 1000);
               play(l, r.position(), SoundEvents.WARDEN_ROAR, 2.5F, 1.1F);
               play(l, r.position(), SoundEvents.ENDER_DRAGON_GROWL, 1.2F, 0.7F);
               say(p, "Full Manifestation: Rika (5:00)", ChatFormatting.LIGHT_PURPLE);
            }
         });
         return true;
      }
   }

   private static void endFull(ServerPlayer p) {
      FULL.remove(p.getUUID());
      RikaEntity r = RIKA.get(p.getUUID());
      if (r != null && r.isAlive()) {
         r.setFull(false);
         Net.fx(p.serverLevel(), new Net.Fx((byte)17, r.position(), Vec3.ZERO, 0.0F, p.getId(), r.getId(), 0));
      }

      say(p, "Rika's full manifestation has ended.", ChatFormatting.GRAY);
   }

   public static RikaEntity rika(ServerPlayer p, boolean force) {
      RikaEntity r = RIKA.get(p.getUUID());
      if (r != null && r.isAlive() && r.level() == p.level()) {
         return r;
      } else {
         Long back = RIKA_BACK.get(p.getUUID());
         if (!force && back != null && back > now) {
            return null;
         } else {
            for (ServerLevel sl : p.server.getAllLevels()) {
               for (Entity e : sl.getAllEntities()) {
                  if (e instanceof RikaEntity) {
                     RikaEntity old = (RikaEntity)e;
                     if (old.ownedBy(p.getUUID())) {
                        old.discard();
                     }
                  }
               }
            }

            ServerLevel l = p.serverLevel();
            r = (RikaEntity)((EntityType)YutaMod.RIKA.get()).create(l);
            if (r == null) {
               return null;
            } else {
               Vec3 behind = p.position().subtract(flat(p.getLookAngle()).scale(4.4)).add(0.0, 0.6, 0.0);
               r.moveTo(behind.x, behind.y, behind.z, p.getYRot(), 0.0F);
               r.setOwner(p);
               l.addFreshEntity(r);
               RIKA.put(p.getUUID(), r);
               RIKA_BACK.remove(p.getUUID());
               Net.fx(l, new Net.Fx((byte)7, p.position(), Vec3.ZERO, 0.6F, p.getId(), r.getId(), 30));
               play(l, behind, SoundEvents.SOUL_ESCAPE, 1.5F, 0.5F);
               return r;
            }
         }
      }
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent e) {
      if (e.phase == Phase.START) {
         now = e.getServer().overworld().getGameTime();
      } else {
         for (Yuta.Timed t : new ArrayList<>(TIMED)) {
            if (--t.left <= 0) {
               TIMED.remove(t);
               t.r.run();
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent e) {
      if (e.phase == Phase.END && e.player instanceof ServerPlayer p) {
         UUID var8 = p.getUUID();
         if (isYuta(p)) {
            if (p.tickCount % 20 == 0 && p.isAlive() && !p.isSpectator()) {
               RikaEntity r = RIKA.get(var8);
               if (r != null && !r.isAlive() && !r.isRemoved()) {
                  RIKA.remove(var8);
                  RIKA_BACK.put(var8, now + 1200L);
               }

               rika(p, false);
            }

            Long full = FULL.get(var8);
            if (full != null) {
               if (full <= now) {
                  endFull(p);
               } else if (p.tickCount % 20 == 0) {
                  Energy.set(p, 1000);
               }
            }

            Long hold = HOLD.get(var8);
            if (hold != null) {
               if (hold < now) {
                  HOLD.remove(var8);
               } else {
                  p.setDeltaMovement(0.0, Math.min(0.0, p.getDeltaMovement().y), 0.0);
                  p.hurtMarked = true;
               }
            }

            if (p.tickCount % 2 == 0) {
               long[] cd = COOLDOWNS.getOrDefault(var8, new long[9]);
               int[] left = new int[10];

               for (int i = 0; i < 9; i++) {
                  left[i] = (int)Math.max(0L, cd[i] - now);
               }

               Long fl = FULL.get(var8);
               left[9] = fl == null ? 0 : (int)Math.max(0L, fl - now);
               Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.State(left));
            }
         }
      }
   }

   @SubscribeEvent
   public static void onLogout(PlayerLoggedOutEvent e) {
      UUID id = e.getEntity().getUUID();
      RikaEntity r = RIKA.remove(id);
      if (r != null && r.isAlive()) {
         r.discard();
      }

      COOLDOWNS.remove(id);
      COMBO.remove(id);
      HOLD.remove(id);
   }

   @SubscribeEvent
   public static void onStop(ServerStoppingEvent e) {
      COOLDOWNS.clear();
      COMBO.clear();
      FULL.clear();
      RIKA.clear();
      RIKA_BACK.clear();
      HOLD.clear();
      TIMED.clear();
   }

   public static void leave(ServerPlayer p) {
      FULL.remove(p.getUUID());
      RikaEntity r = RIKA.remove(p.getUUID());
      if (r != null && r.isAlive()) {
         r.dismiss();
      }
   }

   static void later(int ticks, Runnable r) {
      TIMED.add(new Yuta.Timed(ticks, r));
   }

   static void hold(ServerPlayer p, int ticks) {
      HOLD.put(p.getUUID(), now + (long)ticks);
   }

   static Vec3 flat(Vec3 v) {
      Vec3 f = new Vec3(v.x, 0.0, v.z);
      return f.lengthSqr() < 1.0E-6 ? new Vec3(0.0, 0.0, 1.0) : f.normalize();
   }

   static LivingEntity target(ServerPlayer p, double range) {
      Vec3 eye = p.getEyePosition();
      Vec3 end = eye.add(p.getLookAngle().scale(range));
      BlockHitResult bh = p.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, p));
      if (bh.getType() != Type.MISS) {
         end = bh.getLocation();
      }

      EntityHitResult eh = ProjectileUtil.getEntityHitResult(
         p.level(),
         p,
         eye,
         end,
         new AABB(eye, end).inflate(2.0),
         e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && !(e instanceof RikaEntity)
      );
      return eh != null ? (LivingEntity)eh.getEntity() : null;
   }

   static void hurt(ServerPlayer p, LivingEntity e, float dmg) {
      e.invulnerableTime = 0;
      e.hurt(p.damageSources().playerAttack(p), dmg);
   }

   static void knock(LivingEntity e, Vec3 dir, double h, double up) {
      e.setDeltaMovement(e.getDeltaMovement().add(dir.x * h, up, dir.z * h));
      e.hurtMarked = true;
   }

   static void pose(ServerLevel l, ServerPlayer p, int pose, int dur) {
      Net.fx(l, new Net.Fx((byte)2, p.position(), Vec3.ZERO, (float)pose, p.getId(), -1, dur));
   }

   static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
      l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
   }

   static void say(ServerPlayer p, String msg, ChatFormatting c) {
      p.displayClientMessage(Component.literal(msg).withStyle(c), true);
   }

   private static final class Timed {
      int left;
      final Runnable r;

      Timed(int left, Runnable r) {
         this.left = left;
         this.r = r;
      }
   }
}
