package com.yuki.mod;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import com.kashimo.mod.KashimoMod;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.LivingKnockBackEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Yuki {
   public static final int MAX_ENERGY = 1000;
   public static final int SLOTS = 10;
   public static final int MASS_TICKS = 1200;
   public static final int GARUDA_TICKS = 6000;
   public static final int SIMPLE_TICKS = 240;
   public static final int DOMAIN_CUT = 60;
   public static final int DOMAIN_TICKS = 300;
   public static final int DOMAIN_RADIUS = 22;
   public static final int HOLE_CUT = 96;
   public static final int HOLE_TICKS = 360;
   public static final int HOLE_REACH = 48;
   private static final String TAG = "yuki";
   private static final int[] COST = new int[]{10, 10, 40, 40, 10, 50, 40, 40, 50, 40};
   private static final int[] COOLDOWN = new int[]{14, 22, 70, 90, 300, 1600, 400, 400, 6000, 300};
   private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
   private static final List<Yuki.Timed> TIMED = new ArrayList<>();
   private static final List<Yuki.Ball> BALLS = new ArrayList<>();
   private static int serial;
   private static long now;

   private Yuki() {
   }

   private static CompoundTag data(Player p) {
      CompoundTag root = p.getPersistentData();
      if (!root.contains("yuki")) {
         CompoundTag t = new CompoundTag();
         t.putInt("energy", 1000);
         root.put("yuki", t);
      }

      return root.getCompound("yuki");
   }

   private static int get(Player p, String k) {
      return data(p).getInt(k);
   }

   private static void set(Player p, String k, int v) {
      data(p).putInt(k, v);
   }

   public static int energy(Player p) {
      return get(p, "energy");
   }

   private static void setEnergy(Player p, int e) {
      set(p, "energy", Math.max(0, Math.min(1000, e)));
   }

   public static boolean isYuki(Player p) {
      return Sorcerers.is(p, Sorcerer.YUKI);
   }

   public static float mass(Player p) {
      return get(p, "mass") > 0 ? 2.5F : 1.0F;
   }

   private static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
      l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
   }

   private static void say(ServerPlayer p, String msg, ChatFormatting c) {
      p.displayClientMessage(Component.literal(msg).withStyle(c), true);
   }

   private static DamageSource src(ServerPlayer p) {
      return p.damageSources().playerAttack(p);
   }

   private static void hurt(ServerPlayer p, LivingEntity e, float dmg) {
      e.invulnerableTime = 0;
      e.hurt(src(p), dmg);
   }

   private static void knock(LivingEntity e, Vec3 dir, double h, double up) {
      e.setDeltaMovement(e.getDeltaMovement().add(dir.x * h, up, dir.z * h));
      e.hurtMarked = true;
   }

   private static Vec3 hands(ServerPlayer p) {
      return p.getEyePosition().add(p.getLookAngle().scale(0.9)).add(0.0, -0.35, 0.0);
   }

   private static Vec3 flat(Vec3 v) {
      Vec3 f = new Vec3(v.x, 0.0, v.z);
      return f.lengthSqr() < 1.0E-4 ? new Vec3(0.0, 0.0, 1.0) : f.normalize();
   }

   private static LivingEntity lookTarget(ServerPlayer p, double range) {
      Vec3 eye = p.getEyePosition();
      Vec3 end = eye.add(p.getLookAngle().scale(range));
      BlockHitResult bh = p.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, p));
      if (bh.getType() != Type.MISS) {
         end = bh.getLocation();
      }

      EntityHitResult eh = ProjectileUtil.getEntityHitResult(
         p.level(), p, eye, end, new AABB(eye, end).inflate(1.5), e -> e instanceof LivingEntity && e.isAlive() && e != p, 0.0F
      );
      return eh != null ? (LivingEntity)eh.getEntity() : null;
   }

   private static List<LivingEntity> around(ServerLevel l, Vec3 c, double r, Entity except) {
      return l.getEntitiesOfClass(
         LivingEntity.class,
         new AABB(c, c).inflate(r),
         e -> e != except && e.isAlive() && !e.isSpectator() && !(e instanceof GarudaEntity) && e.distanceToSqr(c) < r * r
      );
   }

   private static void pose(ServerLevel l, ServerPlayer p, int pose, int dur) {
      Net.fx(l, new Net.Fx((byte)15, p.position(), Vec3.ZERO, (float)pose, p.getId(), -1, dur));
   }

   private static void crater(ServerLevel l, Vec3 c, int r) {
      BlockPos cc = BlockPos.containing(c);
      MutableBlockPos m = new MutableBlockPos();

      for (int dx = -r; dx <= r; dx++) {
         for (int dy = -r; dy <= 1; dy++) {
            for (int dz = -r; dz <= r; dz++) {
               if (dx * dx + dy * dy * 2 + dz * dz <= r * r) {
                  m.set(cc.getX() + dx, cc.getY() + dy, cc.getZ() + dz);
                  BlockState st = l.getBlockState(m);
                  float h = st.getDestroySpeed(l, m);
                  if (!st.isAir() && h >= 0.0F && h < 30.0F) {
                     l.setBlock(m, Blocks.AIR.defaultBlockState(), 2);
                  }
               }
            }
         }
      }
   }

   public static void useKey(ServerPlayer p, int slot) {
      if (slot >= 0 && slot < 10 && p.isAlive() && isYuki(p) && !busy(p)) {
         long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), u -> new long[10]);
         if (cd[slot] <= now) {
            int cost = COST[slot];
            if (!p.isCreative() && energy(p) < cost) {
               say(p, "Not enough cursed energy (" + cost + ")", ChatFormatting.RED);
            } else {
               ServerLevel l = p.serverLevel();

               boolean ok = switch (slot) {
                  case 0 -> strike(l, p);
                  case 1 -> whip(l, p);
                  case 2 -> ball(l, p);
                  case 3 -> bind(l, p);
                  case 4 -> simpleDomain(l, p);
                  case 5 -> domain(l, p);
                  case 6 -> massUp(l, p);
                  case 7 -> rct(l, p);
                  case 8 -> blackHole(l, p);
                  default -> garuda(l, p);
               };
               if (ok) {
                  if (!p.isCreative()) {
                     setEnergy(p, energy(p) - cost);
                  }

                  cd[slot] = now + (long)COOLDOWN[slot];
               }
            }
         }
      }
   }

   static boolean strike(ServerLevel l, ServerPlayer p) {
      float m = mass(p);
      LivingEntity t = lookTarget(p, 8.0);
      Vec3 look = p.getLookAngle();
      Vec3 fl = flat(look);
      pose(l, p, 2, 8);
      if (t != null) {
         Vec3 to = t.position().subtract(fl.scale(1.3));
         p.connection.teleport(to.x, Math.max(p.getY(), to.y), to.z, p.getYRot(), p.getXRot());
      } else {
         p.setDeltaMovement(fl.x * 1.5, 0.15, fl.z * 1.5);
         p.hurtMarked = true;
      }

      Vec3 at = t != null ? t.position().add(0.0, (double)t.getBbHeight() * 0.55, 0.0) : p.getEyePosition().add(look.scale(2.5));
      Net.fx(l, new Net.Fx((byte)1, at, fl, m, p.getId(), t != null ? t.getId() : -1, 12));
      play(l, at, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.4F, 0.6F);
      play(l, at, (SoundEvent)KashimoMod.SND_CRACK.get(), 0.8F * m, 1.6F / m);

      for (LivingEntity e : around(l, at, 2.2 + (double)m, p)) {
         hurt(p, e, (e == t ? 14.0F : 7.0F) * m);
         knock(e, flat(e.position().subtract(p.position())), 1.4 * (double)m, 0.35 * (double)m);
         Net.fx(l, new Net.Fx((byte)0, e.position().add(0.0, (double)e.getBbHeight() * 0.55, 0.0), fl, 0.8F * m, p.getId(), e.getId(), 0));
      }

      if (m > 1.0F) {
         crater(l, at.add(0.0, -1.2, 0.0), 2);
      }

      return true;
   }

   static GarudaEntity ensureGaruda(ServerLevel l, ServerPlayer p) {
      GarudaEntity g = garudaOf(l, p);
      if (g == null) {
         garuda(l, p);
         g = garudaOf(l, p);
      }

      return g;
   }

   static boolean whip(final ServerLevel l, ServerPlayer p) {
      GarudaEntity g = ensureGaruda(l, p);
      if (g != null) {
         g.becomeMove(false, 4, 16);
      }

      TIMED.add(new Yuki.Timed(l, p, 5) {
         @Override
         void tick(int age) {
            if (age == 4) {
               Yuki.doWhip(l, this.caster);
            }
         }
      });
      return true;
   }

   static boolean doWhip(final ServerLevel l, ServerPlayer p) {
      final float m = mass(p);
      final Vec3 fl = flat(p.getLookAngle());
      pose(l, p, 3, 10);
      Net.fx(l, new Net.Fx((byte)2, p.position(), fl, m, p.getId(), -1, 12));
      play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.6F);
      play(l, p.position(), SoundEvents.TRIDENT_RIPTIDE_1, 1.0F, 1.4F);
      TIMED.add(new Yuki.Timed(l, p, 4) {
         @Override
         void tick(int age) {
            if (age == 4) {
               for (LivingEntity e : Yuki.around(l, this.caster.position(), 11.0, this.caster)) {
                  Vec3 to = e.position().subtract(this.caster.position());
                  if (!(Yuki.flat(to).dot(fl) < 0.2)) {
                     Yuki.hurt(this.caster, e, 10.0F * m);
                     Vec3 side = fl.cross(new Vec3(0.0, 1.0, 0.0));
                     Yuki.knock(e, side.add(fl.scale(0.5)).normalize(), 1.1 * (double)m, 0.4);
                     Net.fx(l, new Net.Fx((byte)0, e.position().add(0.0, (double)e.getBbHeight() * 0.55, 0.0), fl, 0.9F, this.caster.getId(), e.getId(), 0));
                  }
               }

               Yuki.play(l, this.caster.position(), (SoundEvent)KashimoMod.SND_CRACK.get(), 0.7F, 1.8F);
            }
         }
      });
      return true;
   }

   static boolean ball(final ServerLevel l, ServerPlayer p) {
      final float m = mass(p);
      GarudaEntity g = ensureGaruda(l, p);
      if (g != null) {
         g.becomeMove(true, 2, -1);
      }

      TIMED.add(new Yuki.Timed(l, p, 14) {
         @Override
         void tick(int age) {
            if (age == 2) {
               Yuki.pose(l, this.caster, 5, 7);
               Vec3 hand = Yuki.throwHand(this.caster);
               Net.fx(l, new Net.Fx((byte)3, hand, Vec3.ZERO, m, this.caster.getId(), ++Yuki.serial, 6));
               Yuki.play(l, hand, SoundEvents.BONE_BLOCK_PLACE, 1.4F, 0.6F);
               Yuki.play(l, hand, SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.6F);
            }

            if (age == 7) {
               Yuki.pose(l, this.caster, 2, 10);
            }

            if (age == 8) {
               Vec3 from = Yuki.throwHand(this.caster);
               LivingEntity t = Yuki.lookTarget(this.caster, 40.0);
               Vec3 dir;
               if (t != null) {
                  dir = t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0).subtract(from).normalize();
               } else {
                  Vec3 look = this.caster.getLookAngle();
                  dir = new Vec3(look.x, Mth.clamp(look.y, -0.6, 0.12), look.z).normalize();
               }

               Yuki.Ball b = new Yuki.Ball(l, this.caster, from, dir.scale(1.3), ++Yuki.serial, m);
               Yuki.BALLS.add(b);
               Net.fx(l, new Net.Fx((byte)3, b.pos, b.vel, m, this.caster.getId(), b.serial, 60));
               Yuki.play(l, from, SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6F, 0.6F);
               Yuki.play(l, from, SoundEvents.TRIDENT_THROW, 1.4F, 0.5F);
            }
         }
      });
      return true;
   }

   static Vec3 throwHand(ServerPlayer p) {
      Vec3 look = p.getLookAngle();
      Vec3 f = flat(look);
      Vec3 right = new Vec3(-f.z, 0.0, f.x);
      return p.getEyePosition().add(f.scale(0.5)).add(right.scale(0.45)).add(0.0, 0.25, 0.0);
   }

   static boolean bind(final ServerLevel l, ServerPlayer p) {
      final LivingEntity t0 = lookTarget(p, 22.0);
      if (t0 == null) {
         say(p, "No target", ChatFormatting.GRAY);
         return false;
      } else {
         GarudaEntity g = ensureGaruda(l, p);
         if (g != null) {
            g.becomeMove(false, 4, 104);
         }

         TIMED.add(new Yuki.Timed(l, p, 5) {
            @Override
            void tick(int age) {
               if (age == 4 && t0.isAlive()) {
                  Yuki.doBind(l, this.caster, t0);
               }
            }
         });
         return true;
      }
   }

   static boolean doBind(final ServerLevel l, ServerPlayer p, final LivingEntity t) {
      int dur = 100;
      pose(l, p, 2, 10);
      Net.fx(l, new Net.Fx((byte)5, t.position(), Vec3.ZERO, 0.0F, p.getId(), t.getId(), dur));
      play(l, t.position(), SoundEvents.CHAIN_PLACE, 1.4F, 0.5F);
      play(l, t.position(), SoundEvents.BONE_BLOCK_PLACE, 1.6F, 0.6F);
      final Vec3 hold = t.position();
      TIMED.add(new Yuki.Timed(l, p, dur) {
         boolean freed;

         @Override
         void tick(int age) {
            if (!t.isAlive()) {
               GarudaEntity g = Yuki.garudaOf(l, this.caster);
               if (!this.freed && g != null && g.hidden()) {
                  g.release(t.position().add(0.0, 1.0, 0.0));
               }

               this.freed = true;
            } else {
               t.setDeltaMovement(0.0, Math.min(0.0, t.getDeltaMovement().y), 0.0);
               if (t.position().distanceToSqr(hold) > 0.04) {
                  if (t instanceof ServerPlayer sp) {
                     sp.connection.teleport(hold.x, hold.y, hold.z, sp.getYRot(), sp.getXRot());
                  } else {
                     t.teleportTo(hold.x, hold.y, hold.z);
                  }
               }

               if (t instanceof Mob mob) {
                  mob.getNavigation().stop();
               }

               t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 5, 9, false, false, false));
               t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 5, 2, false, false, false));
               if (age % 20 == 10) {
                  Yuki.hurt(this.caster, t, 4.0F * Yuki.mass(this.caster));
                  Yuki.play(l, t.position(), SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.6F);
               }
            }
         }
      });
      return true;
   }

   static boolean simpleDomain(ServerLevel l, ServerPlayer p) {
      set(p, "simple", 240);
      Net.fx(l, new Net.Fx((byte)6, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 240));
      play(l, p.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.6F, 0.7F);
      play(l, p.position(), SoundEvents.BEACON_ACTIVATE, 0.8F, 1.6F);
      return true;
   }

   static boolean domain(final ServerLevel l, ServerPlayer p) {
      final Vec3 c = p.position();
      final int r = 22;
      Net.fx(l, new Net.Fx((byte)7, c, Vec3.ZERO, (float)r, p.getId(), -1, 360));
      pose(l, p, 4, 60);
      play(l, c, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
      play(l, c, SoundEvents.WARDEN_SONIC_CHARGE, 1.5F, 0.5F);
      TIMED.add(new Yuki.Timed(l, p, 360, true, 60) {
         @Override
         void tick(int age) {
            if (age == 60) {
               Yuki.play(l, c, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 2.5F, 0.5F);
               Yuki.play(l, c, (SoundEvent)KashimoMod.SND_RUMBLE.get(), 2.5F, 0.6F);
            }

            if (age >= 60) {
               if (this.caster.position().distanceTo(c) < (double)r) {
                  Yuki.set(this.caster, "mass", Math.max(Yuki.get(this.caster, "mass"), 20));
               }

               for (LivingEntity e : Yuki.around(l, c, (double)r, this.caster)) {
                  Vec3 v = e.getDeltaMovement();
                  e.setDeltaMovement(v.x * 0.3, Math.min(v.y, -0.6), v.z * 0.3);
                  e.hurtMarked = true;
                  e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 10, 4, false, false, true));
                  if ((age - 60) % 20 == 0) {
                     Yuki.hurt(this.caster, e, 7.0F);
                     Net.fx(l, new Net.Fx((byte)16, e.position(), Vec3.ZERO, 1.8F, this.caster.getId(), e.getId(), 0));
                  }
               }

               if ((age - 60) % 20 == 0) {
                  Yuki.play(l, c, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.5F);
               }
            }
         }
      });
      return true;
   }

   static boolean massUp(ServerLevel l, ServerPlayer p) {
      set(p, "mass", 1200);
      Net.fx(l, new Net.Fx((byte)8, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 1200));
      pose(l, p, 1, 14);
      play(l, p.position(), (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 1.6F, 0.6F);
      play(l, p.position(), SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.6F);

      for (LivingEntity e : around(l, p.position(), 5.0, p)) {
         knock(e, flat(e.position().subtract(p.position())), 1.4, 0.4);
      }

      crater(l, p.position().add(0.0, -1.0, 0.0), 2);
      return true;
   }

   static boolean rct(ServerLevel l, ServerPlayer p) {
      Net.fx(l, new Net.Fx((byte)10, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 40));
      play(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.4F, 1.5F);
      TIMED.add(new Yuki.Timed(l, p, 40) {
         @Override
         void tick(int age) {
            this.caster.heal(this.caster.getMaxHealth() / 40.0F + 0.2F);
            if (age % 5 == 0) {
               for (MobEffectInstance ef : new ArrayList(this.caster.getActiveEffects())) {
                  if (ef.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                     this.caster.removeEffect(ef.getEffect());
                  }
               }
            }

            this.caster.clearFire();
         }
      });
      return true;
   }

   static boolean garuda(ServerLevel l, ServerPlayer p) {
      GarudaEntity old = garudaOf(l, p);
      if (old != null) {
         old.dismiss();
         return false;
      } else {
         GarudaEntity g = (GarudaEntity)((EntityType)YukiMod.GARUDA.get()).create(l);
         if (g == null) {
            return false;
         } else {
            for (ServerLevel sl : l.getServer().getAllLevels()) {
               for (Entity e : sl.getAllEntities()) {
                  if (e instanceof GarudaEntity) {
                     GarudaEntity old2 = (GarudaEntity)e;
                     if (old2.ownedBy(p.getUUID())) {
                        old2.discard();
                     }
                  }
               }
            }

            g.moveTo(p.getX(), p.getY() + 2.5, p.getZ(), p.getYRot(), 0.0F);
            g.setOwner(p);
            l.addFreshEntity(g);
            data(p).putUUID("garudaId", g.getUUID());
            set(p, "garuda", 6000);
            Net.fx(l, new Net.Fx((byte)12, g.position(), Vec3.ZERO, 0.0F, p.getId(), g.getId(), 16));
            play(l, p.position(), SoundEvents.BONE_BLOCK_PLACE, 1.6F, 0.5F);
            play(l, p.position(), SoundEvents.SKELETON_AMBIENT, 1.4F, 0.5F);
            return true;
         }
      }
   }

   static GarudaEntity garudaOf(ServerLevel l, ServerPlayer p) {
      if (!data(p).hasUUID("garudaId")) {
         return null;
      } else {
         if (l.getEntity(data(p).getUUID("garudaId")) instanceof GarudaEntity g && g.isAlive()) {
            return g;
         }

         return null;
      }
   }

   static void garudaGone(ServerPlayer p) {
      set(p, "garuda", 0);
   }

   static boolean blackHole(ServerLevel l, ServerPlayer p) {
      long armed = data(p).getLong("armed");
      if (armed > 0L && now - armed <= 60L) {
         data(p).putLong("armed", -1L);
         Vec3 c = p.position().add(0.0, 2.5, 0.0);
         Net.fx(l, new Net.Fx((byte)11, c, Vec3.ZERO, 4.5F, p.getId(), -1, 360));
         pose(l, p, 5, 96);
         play(l, c, SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.4F);
         play(l, c, SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.5F);
         Yuki.Holder holder = new Yuki.Holder(l, p, c);
         TIMED.add(holder);
         return true;
      } else {
         data(p).putLong("armed", now);
         say(p, "BLACK HOLE: press again within 3 seconds. It will cost your life.", ChatFormatting.DARK_PURPLE);
         return false;
      }
   }

   private static void stepBalls() {
      Iterator<Yuki.Ball> it = BALLS.iterator();

      while (it.hasNext()) {
         Yuki.Ball b = it.next();
         b.age++;
         Vec3 next = b.pos.add(b.vel);
         Vec3 hit = null;
         BlockHitResult bh = b.level.clip(new ClipContext(b.pos, next, Block.COLLIDER, Fluid.NONE, b.owner));
         if (bh.getType() != Type.MISS) {
            hit = bh.getLocation();
         }

         Iterator at = b.level
            .getEntitiesOfClass(LivingEntity.class, new AABB(b.pos, next).inflate(1.2), ex -> ex != b.owner && ex.isAlive() && !(ex instanceof GarudaEntity))
            .iterator();
         if (at.hasNext()) {
            LivingEntity e = (LivingEntity)at.next();
            hit = e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0);
         }

         if (hit == null && b.age < 60) {
            b.pos = next;
            b.vel = b.vel.add(0.0, -0.015, 0.0);
         } else {
            Vec3 atx = hit != null ? hit : b.pos;
            float r = 4.5F * (b.mass > 1.0F ? 1.6F : 1.0F);
            Net.fx(b.level, new Net.Fx((byte)4, atx, b.vel.normalize(), r, b.owner.getId(), b.serial, 0));
            play(b.level, atx, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 2.5F, 0.7F);
            play(b.level, atx, (SoundEvent)KashimoMod.SND_CRACK.get(), 2.5F, 0.6F);

            for (LivingEntity e : around(b.level, atx, (double)(r + 1.0F), b.owner)) {
               hurt(b.owner, e, 24.0F * b.mass);
               knock(e, flat(e.position().subtract(atx)), 1.6 * (double)b.mass, 0.7);
               Net.fx(
                  b.level,
                  new Net.Fx((byte)0, e.position().add(0.0, (double)e.getBbHeight() * 0.55, 0.0), b.vel.normalize(), 2.4F, b.owner.getId(), e.getId(), 0)
               );
            }

            crater(b.level, atx, (int)(r * 0.7F));
            GarudaEntity g = garudaOf(b.level, b.owner);
            if (g != null && g.hidden()) {
               g.release(atx.add(0.0, 1.0, 0.0));
            }

            it.remove();
         }
      }
   }

   private static boolean busy(ServerPlayer p) {
      for (Yuki.Timed t : TIMED) {
         if (t.caster == p && t.holds && t.age < t.holdUntil) {
            return true;
         }
      }

      return false;
   }

   @SubscribeEvent
   public static void onServerTick(ServerTickEvent e) {
      if (e.phase == Phase.END) {
         now++;

         for (Yuki.Timed t : new ArrayList<>(TIMED)) {
            t.age++;
            if (!t.caster.isAlive() && !t.survivesCaster) {
               TIMED.remove(t);
            } else {
               if (t.holds && t.age < t.holdUntil && t.caster.isAlive()) {
                  t.caster.setDeltaMovement(0.0, Math.min(0.0, t.caster.getDeltaMovement().y), 0.0);
                  if (t.caster.position().distanceToSqr(t.at) > 0.01) {
                     t.caster.connection.teleport(t.at.x, Math.min(t.at.y, t.caster.getY()), t.at.z, t.caster.getYRot(), t.caster.getXRot());
                  }
               }

               t.tick(t.age);
               if (t.age >= t.length) {
                  TIMED.remove(t);
               }
            }
         }

         stepBalls();
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent e) {
      if (e.phase == Phase.END && e.player instanceof ServerPlayer p) {
         boolean var12 = isYuki(p);
         ServerLevel l = p.serverLevel();
         if (var12 && p.tickCount % 8 == 0) {
            setEnergy(p, energy(p) + 1);
         }

         int mass = get(p, "mass");
         if (mass > 0) {
            set(p, "mass", mass - 1);
            if (p.tickCount % 20 == 0) {
               p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 2, false, false));
               p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false));
            }

            if (mass == 1) {
               Net.fx(l, new Net.Fx((byte)9, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 0));
            }
         }

         int simple = get(p, "simple");
         if (simple > 0) {
            set(p, "simple", simple - 1);
            if (p.tickCount % 8 == 0) {
               for (LivingEntity o : around(l, p.position(), 3.0, p)) {
                  hurt(p, o, 6.0F * mass(p));
                  knock(o, flat(o.position().subtract(p.position())), 0.9, 0.3);
                  Net.fx(l, new Net.Fx((byte)17, o.position().add(0.0, (double)o.getBbHeight() * 0.5, 0.0), Vec3.ZERO, 0.0F, p.getId(), o.getId(), 0));
               }
            }
         }

         int g = get(p, "garuda");
         if (g > 0) {
            set(p, "garuda", g - 1);
         }

         if (p.tickCount % 5 == 0 && (var12 || mass > 0)) {
            long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), u -> new long[10]);
            int[] left = new int[10];

            for (int i = 0; i < 10; i++) {
               left[i] = (int)Math.max(0L, cd[i] - now);
            }

            long at = data(p).getLong("armed");
            int armed = at > 0L && now - at <= 60L ? 1 : 0;
            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.State(energy(p), get(p, "mass"), get(p, "garuda"), get(p, "simple"), armed, left));
         }
      }
   }

   @SubscribeEvent
   public static void onHurt(LivingHurtEvent e) {
      if (e.getEntity() instanceof ServerPlayer victim && isYuki(victim) && get(victim, "simple") > 0) {
         e.setAmount(e.getAmount() * 0.5F);
      }

      if (!(e.getSource().getDirectEntity() instanceof ServerPlayer p) || e.getSource().getEntity() != p || !isYuki(p)) {
         return;
      }

      if (get(p, "mass") > 0 && !e.getSource().getMsgId().equals("indirectMagic")) {
         LivingEntity t = e.getEntity();
         if (!e.getSource().is(DamageTypeTags.IS_PROJECTILE)) {
            if (p.getMainHandItem().isEmpty() && t.invulnerableTime <= 10) {
               e.setAmount(e.getAmount() * 2.5F);
               knock(t, flat(t.position().subtract(p.position())), 1.6, 0.4);
               Net.fx(
                  p.serverLevel(),
                  new Net.Fx((byte)1, t.position().add(0.0, (double)t.getBbHeight() * 0.55, 0.0), flat(p.getLookAngle()), 1.5F, p.getId(), t.getId(), 8)
               );
            }
         }
      }
   }

   @SubscribeEvent
   public static void onKnockback(LivingKnockBackEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && isYuki(p) && (get(p, "mass") > 0 || get(p, "simple") > 0)) {
         e.setCanceled(true);
      }
   }

   @SubscribeEvent
   public static void onFall(LivingFallEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && isYuki(p) && get(p, "mass") > 0) {
         if (e.getDistance() > 2.5F) {
            ServerLevel l = p.serverLevel();
            float r = Math.min(8.0F, 2.5F + e.getDistance() * 0.3F);
            Net.fx(l, new Net.Fx((byte)16, p.position(), Vec3.ZERO, r, p.getId(), -1, 0));
            play(l, p.position(), (SoundEvent)KashimoMod.SND_CRACK.get(), 1.6F, 0.6F);

            for (LivingEntity o : around(l, p.position(), (double)r, p)) {
               hurt(p, o, Math.min(30.0F, 4.0F + e.getDistance()));
               knock(o, flat(o.position().subtract(p.position())), 1.0, 0.6);
            }

            crater(l, p.position().add(0.0, -1.0, 0.0), (int)Math.min(3.0F, r / 2.5F));
         }

         e.setDamageMultiplier(0.0F);
         return;
      }
   }

   @SubscribeEvent
   public static void onJump(LivingJumpEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && isYuki(p) && get(p, "mass") > 0) {
         p.setDeltaMovement(p.getDeltaMovement().add(0.0, 0.12, 0.0));
         p.hurtMarked = true;
      }
   }

   @SubscribeEvent
   public static void onStop(ServerStoppingEvent e) {
      TIMED.clear();
      BALLS.clear();
      COOLDOWNS.clear();
   }

   public static void leave(ServerPlayer p) {
      set(p, "mass", 0);
      set(p, "simple", 0);
      Net.fx(p.serverLevel(), new Net.Fx((byte)9, p.position(), Vec3.ZERO, 1.0F, p.getId(), -1, 0));
      GarudaEntity g = garudaOf(p.serverLevel(), p);
      if (g != null) {
         g.dismiss();
      }
   }

   private static final class Ball {
      final ServerLevel level;
      final ServerPlayer owner;
      final int serial;
      final float mass;
      Vec3 pos;
      Vec3 vel;
      int age;

      Ball(ServerLevel level, ServerPlayer owner, Vec3 pos, Vec3 vel, int serial, float mass) {
         this.level = level;
         this.owner = owner;
         this.pos = pos;
         this.vel = vel;
         this.serial = serial;
         this.mass = mass;
      }
   }

   private static final class Holder extends Yuki.Timed {
      final Vec3 c;

      Holder(ServerLevel l, ServerPlayer p, Vec3 c) {
         super(l, p, 456, true, 96);
         this.c = c;
         this.survivesCaster = true;
      }

      @Override
      void tick(int age) {
         ServerLevel l = this.level;
         if (age == 30 || age == 60) {
            Yuki.play(l, this.c, SoundEvents.WARDEN_HEARTBEAT, 3.0F, 0.4F + (float)age * 0.004F);
            Yuki.play(l, this.c, (SoundEvent)KashimoMod.SND_RUMBLE.get(), 2.0F, 0.5F);
         }

         if (age < 96) {
            for (LivingEntity e : Yuki.around(l, this.c, 10.0, this.caster)) {
               e.setDeltaMovement(e.getDeltaMovement().add(0.0, -0.08, 0.0));
               e.hurtMarked = true;
            }
         } else {
            int t = age - 96;
            if (t == 0) {
               Yuki.play(l, this.c, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 4.0F, 0.4F);
               Yuki.play(l, this.c, (SoundEvent)KashimoMod.SND_CRACK.get(), 4.0F, 0.4F);
               Yuki.play(l, this.c, SoundEvents.END_PORTAL_SPAWN, 3.0F, 0.6F);
               if (this.caster.isAlive() && !this.caster.isCreative()) {
                  this.caster.hurt(this.caster.damageSources().fellOutOfWorld(), Float.MAX_VALUE);
               }
            }

            if (t % 40 == 0) {
               Yuki.play(l, this.c, (SoundEvent)SoundEvents.AMBIENT_BASALT_DELTAS_LOOP.get(), 3.0F, 0.4F);
               Yuki.play(l, this.c, SoundEvents.WARDEN_HEARTBEAT, 3.0F, 0.3F);
            }

            float core = Math.min(4.5F, 1.2F + (float)t / 30.0F);
            AABB box = new AABB(this.c, this.c).inflate(48.0);

            for (Entity e : l.getEntities((Entity)null, box, ex -> ex.isAlive() && !ex.isSpectator())) {
               if (e instanceof Player pl && pl.isCreative() && pl.getAbilities().flying) {
                  continue;
               }

               Vec3 to = this.c.subtract(e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0));
               double d = to.length();
               if (!(d > 48.0) && !(d < 0.001)) {
                  double k = 1.0 - d / 48.0;
                  double pull = 0.006 + 0.075 * k * k * k + (d < (double)(core * 3.0F) ? 0.12 : 0.0);
                  e.setDeltaMovement(e.getDeltaMovement().scale(d < 16.0 ? 0.9 : 0.97).add(to.normalize().scale(pull)));
                  e.hurtMarked = true;
                  e.fallDistance = 0.0F;
                  if (d < (double)core + 0.8 && e instanceof LivingEntity le) {
                     le.invulnerableTime = 0;
                     le.hurt(l.damageSources().magic(), 60.0F);
                  } else if (d < (double)core + 0.8 && !(e instanceof Player)) {
                     e.discard();
                  }
               }
            }

            if (t % 6 == 0) {
               Yuki.crater(l, this.c, (int)Math.min(9.0F, core * 1.6F + 1.0F));
            }

            if (t == 359) {
               Yuki.play(l, this.c, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 4.0F, 0.7F);

               for (LivingEntity e : Yuki.around(l, this.c, 16.0, null)) {
                  Vec3 out = e.position().subtract(this.c).normalize();
                  e.setDeltaMovement(out.scale(1.4).add(0.0, 0.5, 0.0));
                  e.hurtMarked = true;
               }
            }
         }
      }
   }

   private abstract static class Timed {
      final ServerLevel level;
      final ServerPlayer caster;
      final int length;
      final boolean holds;
      final int holdUntil;
      final Vec3 at;
      boolean survivesCaster;
      int age;

      Timed(ServerLevel level, ServerPlayer caster, int length) {
         this(level, caster, length, false, 0);
      }

      Timed(ServerLevel level, ServerPlayer caster, int length, boolean holds, int holdUntil) {
         this.level = level;
         this.caster = caster;
         this.length = length;
         this.holds = holds;
         this.holdUntil = holdUntil;
         this.at = caster.position();
      }

      abstract void tick(int var1);
   }
}
