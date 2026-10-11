package com.kashimo.mod;

import com.jjk.mod.Sorcerer;
import com.jjk.mod.Sorcerers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.TickEvent.ServerTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Kashimo {
   public static final int MAX_ENERGY = 1000;
   public static final int AMBER_TICKS = 1200;
   public static final int AMBER_CUT = 70;
   public static final int LAST_CUT = 100;
   public static final int DOMAIN_CUT = 50;
   public static final int DOMAIN_TICKS = 240;
   private static final String TAG = "kashimo";
   private static final String CHARGE = "kashimo_charge";
   private static final int[] COST = new int[]{10, 10, 40, 40, 10, 50, 50, 40, 40};
   private static final int[] AMBER_COST = new int[]{10, 10, 40, 50, 10, 50, 50, 40, 50};
   private static final int[] COOLDOWN = new int[]{12, 20, 60, 80, 400, 1400, 2400, 100, 500};
   private static final int[] AMBER_COOLDOWN = new int[]{25, 20, 80, 120, 400, 1400, 0, 80, 400};
   public static final int SLOTS = 9;
   public static final int FURY_TICKS = 70;
   private static final Map<UUID, long[]> COOLDOWNS = new HashMap<>();
   private static final List<Kashimo.Timed> TIMED = new ArrayList<>();
   private static long now;

   private Kashimo() {
   }

   private static CompoundTag data(Player p) {
      CompoundTag root = p.getPersistentData();
      if (!root.contains("kashimo")) {
         CompoundTag t = new CompoundTag();
         t.putInt("energy", 1000);
         root.put("kashimo", t);
      }

      return root.getCompound("kashimo");
   }

   public static int energy(Player p) {
      return data(p).getInt("energy");
   }

   private static void setEnergy(Player p, int e) {
      data(p).putInt("energy", Math.max(0, Math.min(1000, e)));
   }

   public static int amber(Player p) {
      return data(p).getInt("amber");
   }

   private static void setAmber(Player p, int t) {
      data(p).putInt("amber", t);
   }

   private static int basket(Player p) {
      return data(p).getInt("basket");
   }

   public static boolean isKashimo(Player p) {
      return Sorcerers.is(p, Sorcerer.KASHIMO);
   }

   public static int charge(Entity e) {
      return e.getPersistentData().getInt("kashimo_charge");
   }

   static void addCharge(ServerLevel level, LivingEntity e, int n) {
      int c = Math.min(5, charge(e) + n);
      e.getPersistentData().putInt("kashimo_charge", c);
      e.getPersistentData().putLong("kashimo_charge_t", level.getGameTime());
      Net.fx(level, new Net.Fx((byte)11, e.position(), Vec3.ZERO, 0.0F, -1, e.getId(), c));
   }

   private static void clearCharge(ServerLevel level, LivingEntity e) {
      e.getPersistentData().putInt("kashimo_charge", 0);
      Net.fx(level, new Net.Fx((byte)11, e.position(), Vec3.ZERO, 0.0F, -1, e.getId(), 0));
   }

   private static void play(ServerLevel l, Vec3 at, SoundEvent s, float vol, float pitch) {
      l.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
   }

   private static void say(ServerPlayer p, String msg, ChatFormatting c) {
      p.displayClientMessage(Component.literal(msg).withStyle(c), true);
   }

   private static DamageSource shock(ServerPlayer p) {
      return p.damageSources().indirectMagic(p, p);
   }

   private static Vec3 fist(ServerPlayer p) {
      Vec3 look = p.getLookAngle();
      Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
      return p.getEyePosition().add(look.scale(0.8)).add(right.scale(0.35)).add(0.0, -0.35, 0.0);
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

   private static Vec3 lookPoint(ServerPlayer p, double range) {
      Vec3 eye = p.getEyePosition();
      Vec3 end = eye.add(p.getLookAngle().scale(range));
      BlockHitResult bh = p.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, p));
      return bh.getType() == Type.MISS ? end : bh.getLocation();
   }

   private static List<LivingEntity> around(ServerLevel l, Vec3 c, double r, Entity except) {
      return l.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(r), e -> e != except && e.isAlive() && e.distanceToSqr(c) < r * r);
   }

   public static void useKey(ServerPlayer p, int slot) {
      if (slot >= 0 && slot < 9 && p.isAlive() && isKashimo(p) && !busy(p)) {
         boolean am = amber(p) > 0;
         long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), u -> new long[9]);
         if (cd[slot] <= now) {
            int cost = (am ? AMBER_COST : COST)[slot];
            if (!p.isCreative() && energy(p) < cost) {
               say(p, "Not enough cursed energy (" + cost + ")", ChatFormatting.RED);
            } else {
               ServerLevel l = p.serverLevel();

               boolean ok = switch (slot) {
                  case 0 -> am ? sonicWave(l, p) : lightningStrike(l, p);
                  case 1 -> plantStaff(l, p);
                  case 2 -> am ? atomicDischarge(l, p) : sureHit(l, p);
                  case 3 -> am ? singularity(l, p) : thunderclap(l, p);
                  case 4 -> wickerBasket(l, p);
                  case 5 -> domain(l, p);
                  default -> am ? lastThunder(l, p) : mythicalBeastAmber(l, p);
                  case 7 -> repulsion(l, p, am);
                  case 8 -> fury(l, p, am);
               };
               if (ok) {
                  if (!p.isCreative()) {
                     setEnergy(p, energy(p) - cost);
                  }

                  cd[slot] = now + (long)(slot == 6 && !am ? 110 : (am ? AMBER_COOLDOWN : COOLDOWN)[slot]);
               }
            }
         }
      }
   }

   static boolean lightningStrike(ServerLevel l, ServerPlayer p) {
      LivingEntity t = lookTarget(p, 7.0);
      Vec3 look = p.getLookAngle();
      if (t != null) {
         Vec3 to = t.position().subtract(look.multiply(1.0, 0.0, 1.0).normalize().scale(1.3));
         p.connection.teleport(to.x, Math.max(p.getY(), to.y), to.z, p.getYRot(), p.getXRot());
      } else {
         p.setDeltaMovement(look.x * 1.6, 0.25, look.z * 1.6);
         p.hurtMarked = true;
      }

      play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 1.6F);
      play(l, p.position(), SoundEvents.TRIDENT_THUNDER, 0.5F, 2.0F);
      if (t == null) {
         Net.fx(l, new Net.Fx((byte)0, fist(p).add(look.scale(1.5)), look, 0.6F, p.getId(), -1, 0));
         return true;
      } else {
         t.hurt(shock(p), 8.0F);
         t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 4, false, false, true));
         t.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 30, 2, false, false, true));
         t.knockback(0.5, -look.x, -look.z);
         addCharge(l, t, 1);
         Vec3 hit = t.position().add(0.0, (double)t.getBbHeight() * 0.6, 0.0);
         Net.fx(l, new Net.Fx((byte)0, hit, look, 1.0F, p.getId(), t.getId(), 0));
         Net.fx(l, new Net.Fx((byte)1, fist(p), hit, 0.25F, p.getId(), t.getId(), 6));
         play(l, hit, SoundEvents.PLAYER_ATTACK_CRIT, 1.4F, 0.8F);
         play(l, hit, (SoundEvent)KashimoMod.SND_CRACK.get(), 0.8F, 1.6F);
         return true;
      }
   }

   static boolean plantStaff(ServerLevel l, ServerPlayer p) {
      Vec3 at = lookPoint(p, 24.0);
      CompoundTag d = data(p);
      d.putDouble("sx", at.x);
      d.putDouble("sy", at.y);
      d.putDouble("sz", at.z);
      d.putString("sdim", l.dimension().location().toString());
      d.putBoolean("staff", true);
      Net.fx(l, new Net.Fx((byte)2, at, Vec3.ZERO, 0.0F, p.getId(), -1, 2400));
      Net.fx(l, new Net.Fx((byte)1, fist(p), at.add(0.0, 2.2, 0.0), 0.12F, p.getId(), -1, 5));
      play(l, at, SoundEvents.TRIDENT_HIT_GROUND, 1.5F, 0.7F);
      play(l, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.8F);
      say(p, "Nyoi Staff planted: charge point set", ChatFormatting.AQUA);
      return true;
   }

   private static Vec3 staffPoint(ServerPlayer p) {
      CompoundTag d = data(p);
      if (d.getBoolean("staff") && d.getString("sdim").equals(p.level().dimension().location().toString())) {
         Vec3 s = new Vec3(d.getDouble("sx"), d.getDouble("sy") + 2.2, d.getDouble("sz"));
         return s.distanceTo(p.position()) < 96.0 ? s : null;
      } else {
         return null;
      }
   }

   static boolean sureHit(final ServerLevel l, final ServerPlayer p) {
      LivingEntity t = lookTarget(p, 40.0);
      if (t == null || charge(t) < 3) {
         LivingEntity best = null;

         for (LivingEntity e : around(l, p.position(), 32.0, p)) {
            if (charge(e) >= 3 && (best == null || charge(e) > charge(best))) {
               best = e;
            }
         }

         if (best == null) {
            say(p, "Not enough charge: strike a target 3 times first (" + (t == null ? 0 : charge(t)) + "/3)", ChatFormatting.YELLOW);
            return false;
         }

         t = best;
      }

      int c = charge(t);
      Vec3 from = staffPoint(p);
      if (from == null) {
         from = p.getEyePosition().add(0.0, 1.2, 0.0);
      }

      Vec3 hit = t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0);
      float dmg = 12.0F + 10.0F * (float)c;
      t.invulnerableTime = 0;
      t.hurt(shock(p), dmg);
      t.setSecondsOnFire(4);
      clearCharge(l, t);
      Net.fx(l, new Net.Fx((byte)1, from, hit, 1.1F, p.getId(), t.getId(), 14));
      Net.fx(l, new Net.Fx((byte)0, hit, hit.subtract(from).normalize(), 2.2F, p.getId(), t.getId(), 1));
      play(l, hit, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.1F);
      play(l, hit, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0F, 0.8F);
      play(l, hit, (SoundEvent)KashimoMod.SND_CRACK.get(), 2.0F, 0.9F);
      if (t.isInWater()) {
         final Vec3 g = t.position();
         Net.fx(l, new Net.Fx((byte)12, g, Vec3.ZERO, 5.0F, p.getId(), -1, 160));
         TIMED.add(new Kashimo.Timed(l, p, 160) {
            @Override
            void tick(int age) {
               if (age % 10 == 0) {
                  for (LivingEntity e : Kashimo.around(l, g, 5.5, p)) {
                     e.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 1, false, false, true));
                     e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 80, 0, false, false, true));
                  }
               }
            }
         });
         say(p, "Electrolysis: chlorine gas!", ChatFormatting.GREEN);
      }

      return true;
   }

   static boolean thunderclap(final ServerLevel l, final ServerPlayer p) {
      Vec3 look = p.getLookAngle().multiply(1.0, 0.0, 1.0).normalize();
      Vec3 start = p.position();
      Vec3 end = start;

      for (int i = 1; i <= 10; i++) {
         Vec3 next = start.add(look.scale((double)i));
         if (!l.noCollision(p, p.getBoundingBox().move(next.subtract(p.position())))) {
            break;
         }

         end = next;
      }

      final List<LivingEntity> marked = new ArrayList<>();
      Vec3 seg = end.subtract(start);

      for (LivingEntity e : l.getEntitiesOfClass(LivingEntity.class, new AABB(start, end).inflate(2.0), x -> x != p && x.isAlive())) {
         Vec3 rel = e.position().subtract(start);
         double k = seg.lengthSqr() < 1.0E-4 ? 0.0 : Math.max(0.0, Math.min(1.0, rel.dot(seg) / seg.lengthSqr()));
         if (e.position().distanceTo(start.add(seg.scale(k))) < 2.2) {
            e.hurt(shock(p), 6.0F);
            addCharge(l, e, 1);
            marked.add(e);
            Net.fx(l, new Net.Fx((byte)3, e.position(), Vec3.ZERO, 0.0F, p.getId(), e.getId(), 20));
         }
      }

      p.connection.teleport(end.x, end.y, end.z, p.getYRot(), p.getXRot());
      Net.fx(l, new Net.Fx((byte)1, start.add(0.0, 1.0, 0.0), end.add(0.0, 1.0, 0.0), 0.5F, p.getId(), -1, 8));
      Net.fx(l, new Net.Fx((byte)0, end.add(0.0, 1.0, 0.0), look, 1.4F, p.getId(), marked.isEmpty() ? -1 : marked.get(0).getId(), 0));
      play(l, start, SoundEvents.TRIDENT_RIPTIDE_3, 1.6F, 1.4F);
      play(l, end, SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.7F);
      TIMED.add(new Kashimo.Timed(l, p, 21) {
         @Override
         void tick(int age) {
            if (age == 20) {
               for (LivingEntity e : marked) {
                  if (e.isAlive()) {
                     Vec3 at = e.position();
                     e.invulnerableTime = 0;
                     e.hurt(Kashimo.shock(p), 14.0F);
                     Net.fx(l, new Net.Fx((byte)16, at.add(0.0, 22.0, 0.0), at, 0.9F, p.getId(), e.getId(), 10));
                     Kashimo.play(l, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.6F, 1.3F);
                     Kashimo.play(l, at, (SoundEvent)KashimoMod.SND_CRACK.get(), 1.2F, 1.1F);
                  }
               }
            }
         }
      });
      return true;
   }

   static boolean wickerBasket(ServerLevel l, ServerPlayer p) {
      data(p).putInt("basket", 160);

      for (MobEffectInstance e : new ArrayList<MobEffectInstance>(p.getActiveEffects())) {
         if (e.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
            p.removeEffect(e.getEffect());
         }
      }

      Net.fx(l, new Net.Fx((byte)7, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 160));
      play(l, p.position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, 1.5F);
      play(l, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F);
      say(p, "Hollow Wicker Basket", ChatFormatting.AQUA);
      return true;
   }

   static boolean domain(final ServerLevel l, final ServerPlayer p) {
      final Vec3 c = p.position();
      final float r = 22.0F;
      Net.fx(l, new Net.Fx((byte)8, c, Vec3.ZERO, r, p.getId(), -1, 290));
      play(l, c, SoundEvents.BEACON_ACTIVATE, 2.0F, 0.6F);
      TIMED.add(
         new Kashimo.Timed(l, p, 290, true, 50) {
            @Override
            void tick(int age) {
               if (age == 50) {
                  Kashimo.play(l, c, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 2.0F, 1.1F);
                  Kashimo.play(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.6F);
               }

               if (age > 50 && age % 12 == 0) {
                  for (LivingEntity e : Kashimo.around(l, c, (double)r, p)) {
                     Vec3 at = e.position();
                     e.invulnerableTime = 0;
                     e.hurt(Kashimo.shock(p), 7.0F);
                     Kashimo.addCharge(l, e, 1);
                     Net.fx(
                        l, new Net.Fx((byte)16, at.add(l.random.nextGaussian() * 3.0, 24.0, l.random.nextGaussian() * 3.0), at, 0.7F, p.getId(), e.getId(), 8)
                     );
                     Kashimo.play(l, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.4F, 1.0F + l.random.nextFloat() * 0.4F);
                  }
               }
            }
         }
      );
      return true;
   }

   static boolean mythicalBeastAmber(final ServerLevel l, final ServerPlayer p) {
      Net.fx(l, new Net.Fx((byte)9, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 1200));
      play(l, p.position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 0.5F);
      play(l, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.5F, 1.4F);
      TIMED.add(new Kashimo.Timed(l, p, 71, true, 70) {
         @Override
         void tick(int age) {
            if (age == 40) {
               Kashimo.play(l, p.position(), SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 0.7F);
            }

            if (age == 70) {
               Kashimo.setAmber(p, 1200);
               Kashimo.play(l, p.position(), (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 2.0F, 1.2F);
               Kashimo.play(l, p.position(), (SoundEvent)KashimoMod.SND_CRACK.get(), 2.0F, 0.8F);

               for (LivingEntity e : Kashimo.around(l, p.position(), 8.0, p)) {
                  e.hurt(Kashimo.shock(p), 10.0F);
                  Vec3 out = e.position().subtract(p.position()).normalize();
                  e.setDeltaMovement(out.x * 1.6, 0.6, out.z * 1.6);
                  e.hurtMarked = true;
               }
            }
         }
      });
      return true;
   }

   static boolean repulsion(ServerLevel l, ServerPlayer p, boolean am) {
      double r = am ? 12.0 : 8.0;
      Vec3 c = p.position().add(0.0, 1.0, 0.0);
      Net.fx(l, new Net.Fx((byte)14, c, Vec3.ZERO, (float)r, p.getId(), -1, 24));
      play(l, c, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 2.0F, 1.3F);
      play(l, c, (SoundEvent)KashimoMod.SND_CRACK.get(), 2.0F, 1.1F);
      play(l, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.5F, 1.4F);

      for (LivingEntity e : around(l, c, r, p)) {
         Vec3 out = e.position().subtract(p.position()).multiply(1.0, 0.0, 1.0);
         out = out.lengthSqr() < 1.0E-4 ? p.getLookAngle().multiply(1.0, 0.0, 1.0).normalize() : out.normalize();
         double k = 1.0 - (double)e.distanceTo(p) / r * 0.5;
         e.hurt(shock(p), am ? 14.0F : 8.0F);
         e.setDeltaMovement(out.x * 3.0 * k, 0.75, out.z * 3.0 * k);
         e.hurtMarked = true;
         e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false, true));
         addCharge(l, e, 1);
         Vec3 at = e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0);
         Net.fx(l, new Net.Fx((byte)16, c, at, 0.3F, p.getId(), e.getId(), 6));
         Net.fx(l, new Net.Fx((byte)0, at, out, 1.2F, p.getId(), e.getId(), 0));
      }

      for (Entity e : l.getEntities(p, new AABB(c, c).inflate(r), x -> x instanceof Projectile)) {
         Vec3 out = e.position().subtract(c).normalize();
         e.setDeltaMovement(out.scale(Math.max(1.5, e.getDeltaMovement().length())));
         e.hurtMarked = true;
      }

      return true;
   }

   static boolean fury(final ServerLevel l, ServerPlayer p, final boolean am) {
      int len = am ? 100 : 70;
      final double r = am ? 18.0 : 13.0;
      Net.fx(l, new Net.Fx((byte)15, p.position(), Vec3.ZERO, (float)r, p.getId(), -1, len));
      play(l, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 1.5F, 1.8F);
      play(l, p.position(), (SoundEvent)KashimoMod.SND_RUMBLE.get(), 2.0F, 1.2F);
      TIMED.add(
         new Kashimo.Timed(l, p, len) {
            @Override
            void tick(int age) {
               Vec3 o = this.caster.position();
               if (age % 3 == 0) {
                  List<LivingEntity> near = Kashimo.around(l, o, r, this.caster);
                  LivingEntity t = !near.isEmpty() && l.random.nextBoolean() ? near.get(l.random.nextInt(near.size())) : null;
                  Vec3 at;
                  if (t != null) {
                     at = t.position();
                  } else {
                     double a = l.random.nextDouble() * Math.PI * 2.0;
                     double d = 3.0 + l.random.nextDouble() * (r - 3.0);
                     at = o.add(Math.cos(a) * d, 0.0, Math.sin(a) * d);
                     BlockPos top = l.getHeightmapPos(Types.MOTION_BLOCKING, BlockPos.containing(at));
                     at = new Vec3(at.x, (double)top.getY(), at.z);
                  }

                  Net.fx(
                     l,
                     new Net.Fx(
                        (byte)16,
                        at.add(l.random.nextGaussian() * 4.0, 28.0, l.random.nextGaussian() * 4.0),
                        at,
                        0.55F + l.random.nextFloat() * 0.4F,
                        this.caster.getId(),
                        t != null ? t.getId() : -1,
                        7
                     )
                  );

                  for (LivingEntity e : Kashimo.around(l, at, 2.8, this.caster)) {
                     e.invulnerableTime = 0;
                     e.hurt(Kashimo.shock(this.caster), am ? 9.0F : 6.0F);
                     e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 3, false, false, true));
                     Kashimo.addCharge(l, e, 1);
                  }

                  if (age % 6 == 0) {
                     Kashimo.play(l, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.6F, 0.8F + l.random.nextFloat() * 0.6F);
                  }

                  if (age % 12 == 0) {
                     Kashimo.play(l, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.4F, 1.0F + l.random.nextFloat() * 0.4F);
                  }
               }

               if (age % 5 == 2) {
                  int n = 0;

                  for (LivingEntity e : Kashimo.around(l, o, 7.0, this.caster)) {
                     if (n++ >= 3) {
                        break;
                     }

                     e.invulnerableTime = 0;
                     e.hurt(Kashimo.shock(this.caster), 3.0F);
                     Net.fx(
                        l,
                        new Net.Fx(
                           (byte)16, o.add(0.0, 1.2, 0.0), e.position().add(0.0, (double)e.getBbHeight() * 0.5, 0.0), 0.18F, this.caster.getId(), e.getId(), 4
                        )
                     );
                  }
               }
            }
         }
      );
      return true;
   }

   static boolean sonicWave(ServerLevel l, ServerPlayer p) {
      Vec3 look = p.getLookAngle();
      Vec3 o = p.getEyePosition();

      for (LivingEntity e : around(l, o, 12.0, p)) {
         Vec3 to = e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0).subtract(o);
         if (to.normalize().dot(look) > 0.55) {
            double k = 1.0 - to.length() / 12.0;
            e.hurt(shock(p), (float)(8.0 + 10.0 * k));
            e.setDeltaMovement(look.x * (1.0 + 2.0 * k), 0.4 + 0.4 * k, look.z * (1.0 + 2.0 * k));
            e.hurtMarked = true;
            e.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0, false, false, true));
            addCharge(l, e, 1);
         }
      }

      Net.fx(l, new Net.Fx((byte)6, o, look, 12.0F, p.getId(), -1, 14));
      play(l, o, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 1.3F);
      return true;
   }

   static boolean atomicDischarge(ServerLevel l, ServerPlayer p) {
      Vec3 start = p.getEyePosition();
      Vec3 dir = p.getLookAngle();
      int range = 64;
      MutableBlockPos cur = new MutableBlockPos();
      Set<Integer> hit = new HashSet<>();

      for (int s = 2; s <= range; s++) {
         Vec3 pt = start.add(dir.scale((double)s));
         BlockPos c = BlockPos.containing(pt);

         for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
               for (int dz = -1; dz <= 1; dz++) {
                  if (dx * dx + dy * dy + dz * dz <= 2) {
                     cur.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                     BlockState st = l.getBlockState(cur);
                     if (!st.isAir() && st.getDestroySpeed(l, cur) >= 0.0F) {
                        l.setBlock(cur, Blocks.AIR.defaultBlockState(), 2);
                     }
                  }
               }
            }
         }

         for (LivingEntity e : around(l, pt, 2.2, p)) {
            if (hit.add(e.getId())) {
               e.invulnerableTime = 0;
               e.hurt(shock(p), 45.0F);
               e.setSecondsOnFire(6);
            }
         }
      }

      Net.fx(l, new Net.Fx((byte)4, fist(p), start.add(dir.scale((double)range)), 1.2F, p.getId(), -1, 16));
      play(l, start, SoundEvents.WARDEN_SONIC_BOOM, 2.0F, 0.7F);
      play(l, start, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 2.0F, 1.4F);
      play(l, start, SoundEvents.BEACON_DEACTIVATE, 2.0F, 2.0F);
      return true;
   }

   static boolean singularity(final ServerLevel l, final ServerPlayer p) {
      final Vec3 c = lookPoint(p, 22.0).add(0.0, 1.5, 0.0);
      final int pull = 60;
      Net.fx(l, new Net.Fx((byte)5, c, Vec3.ZERO, 6.0F, p.getId(), -1, pull));
      play(l, c, SoundEvents.BEACON_AMBIENT, 2.0F, 0.6F);
      play(l, c, SoundEvents.PORTAL_TRIGGER, 1.5F, 1.6F);
      TIMED.add(new Kashimo.Timed(l, p, pull + 1) {
         @Override
         void tick(int age) {
            if (age < pull) {
               for (LivingEntity e : Kashimo.around(l, c, 14.0, p)) {
                  Vec3 to = c.subtract(e.position().add(0.0, (double)(e.getBbHeight() / 2.0F), 0.0));
                  e.setDeltaMovement(e.getDeltaMovement().scale(0.4).add(to.normalize().scale(Math.min(0.9, 0.25 + to.length() * 0.06))));
                  e.hurtMarked = true;
                  e.fallDistance = 0.0F;
               }
            } else {
               for (LivingEntity e : Kashimo.around(l, c, 7.0, p)) {
                  e.invulnerableTime = 0;
                  e.hurt(Kashimo.shock(p), 30.0F);
                  Kashimo.addCharge(l, e, 2);
               }

               l.explode(p, c.x, c.y, c.z, 3.5F, ExplosionInteraction.NONE);
               Kashimo.play(l, c, (SoundEvent)KashimoMod.SND_CRACK.get(), 2.0F, 0.7F);
               Kashimo.play(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.0F, 1.0F);
            }
         }
      });
      return true;
   }

   static boolean lastThunder(final ServerLevel l, final ServerPlayer p) {
      final Vec3 c = lookPoint(p, 40.0);
      Net.fx(l, new Net.Fx((byte)10, p.position(), c, 0.0F, p.getId(), -1, 100));
      play(l, p.position(), SoundEvents.WARDEN_SONIC_CHARGE, 2.0F, 0.6F);
      TIMED.add(
         new Kashimo.Timed(l, p, 130, true, 82) {
            @Override
            void tick(int age) {
               if (age == 30) {
                  Kashimo.play(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 3.0F, 0.5F);
               }

               if (age == 82) {
                  for (LivingEntity e : Kashimo.around(l, c, 20.0, p)) {
                     e.invulnerableTime = 0;
                     e.hurt(Kashimo.shock(p), 300.0F);
                  }

                  MutableBlockPos cur = new MutableBlockPos();
                  int r = 11;
                  BlockPos cc = BlockPos.containing(c);

                  for (int dx = -r; dx <= r; dx++) {
                     for (int dy = -r; dy <= 4; dy++) {
                        for (int dz = -r; dz <= r; dz++) {
                           if (dx * dx + dy * dy * 2 + dz * dz <= r * r) {
                              cur.set(cc.getX() + dx, cc.getY() + dy, cc.getZ() + dz);
                              BlockState st = l.getBlockState(cur);
                              if (!st.isAir() && st.getDestroySpeed(l, cur) >= 0.0F) {
                                 l.setBlock(
                                    cur,
                                    dy < -r + 3 && l.random.nextFloat() < 0.4F ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.AIR.defaultBlockState(),
                                    2
                                 );
                              }
                           }
                        }
                     }
                  }

                  Kashimo.play(l, c, (SoundEvent)KashimoMod.SND_SHOCKWAVE.get(), 3.0F, 0.6F);
                  Kashimo.play(l, c, (SoundEvent)KashimoMod.SND_RUMBLE.get(), 3.0F, 0.8F);
                  Kashimo.play(l, c, (SoundEvent)KashimoMod.SND_CRACK.get(), 3.0F, 0.5F);
               }

               if (age == 129 && Kashimo.amber(p) > 0) {
                  Kashimo.endAmber(l, p);
               }
            }
         }
      );
      return true;
   }

   private static void endAmber(ServerLevel l, ServerPlayer p) {
      setAmber(p, 0);
      COOLDOWNS.computeIfAbsent(p.getUUID(), u -> new long[9])[6] = now + (long)COOLDOWN[6];
      if (!p.isCreative()) {
         p.setHealth(Math.min(p.getHealth(), 2.0F));
         p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 800, 1));
         p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 800, 1));
      }

      Net.fx(l, new Net.Fx((byte)13, p.position(), Vec3.ZERO, 0.0F, p.getId(), -1, 0));
      say(p, "Mythical Beast Amber has burned out your body...", ChatFormatting.GOLD);
   }

   private static boolean busy(ServerPlayer p) {
      for (Kashimo.Timed t : TIMED) {
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
         Iterator<Kashimo.Timed> it = TIMED.iterator();

         while (it.hasNext()) {
            Kashimo.Timed t = it.next();
            t.age++;
            if (!t.caster.isAlive()) {
               it.remove();
            } else {
               if (t.holds && t.age < t.holdUntil) {
                  t.caster.setDeltaMovement(0.0, Math.min(0.0, t.caster.getDeltaMovement().y), 0.0);
                  if (t.caster.position().distanceToSqr(t.at) > 0.01) {
                     t.caster.connection.teleport(t.at.x, Math.min(t.at.y, t.caster.getY()), t.at.z, t.caster.getYRot(), t.caster.getXRot());
                  }
               }

               t.tick(t.age);
               if (t.age >= t.length) {
                  it.remove();
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onPlayerTick(PlayerTickEvent e) {
      if (e.phase == Phase.END && e.player instanceof ServerPlayer p) {
         ServerLevel var9 = p.serverLevel();
         boolean kashimo = isKashimo(p);
         if (kashimo && p.tickCount % 2 == 0) {
            setEnergy(p, energy(p) + 1);
         }

         int am = amber(p);
         if (am > 0) {
            setAmber(p, am - 1);
            if (p.tickCount % 20 == 0) {
               p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 2, false, false));
               p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 40, 2, false, false));
               p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 1, false, false));
               p.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 2, false, false));
            }

            if (am == 1) {
               endAmber(var9, p);
            }
         }

         int b = basket(p);
         if (b > 0) {
            data(p).putInt("basket", b - 1);
            if (p.tickCount % 5 == 0) {
               for (MobEffectInstance ef : new ArrayList<MobEffectInstance>(p.getActiveEffects())) {
                  if (ef.getEffect().getCategory() == MobEffectCategory.HARMFUL) {
                     p.removeEffect(ef.getEffect());
                  }
               }
            }
         }

         if (p.tickCount % 5 == 0 && (kashimo || am > 0)) {
            long[] cd = COOLDOWNS.computeIfAbsent(p.getUUID(), u -> new long[9]);
            int[] left = new int[9];

            for (int i = 0; i < 9; i++) {
               left[i] = (int)Math.max(0L, cd[i] - now);
            }

            Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new Net.State(energy(p), amber(p), basket(p), left));
         }
      }
   }

   @SubscribeEvent
   public static void onHurt(LivingHurtEvent e) {
      if (e.getEntity() instanceof ServerPlayer victim && basket(victim) > 0) {
         e.setAmount(e.getAmount() * 0.5F);
      }

      if (!(e.getSource().getDirectEntity() instanceof ServerPlayer p) || e.getSource().getEntity() != p || !isKashimo(p)) {
         return;
      }

      if (!e.getSource().is(DamageTypeTags.IS_PROJECTILE) && !e.getSource().getMsgId().equals("indirectMagic")) {
         ServerLevel l = p.serverLevel();
         LivingEntity t = e.getEntity();
         addCharge(l, t, 1);
         t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 2, false, false, true));
         Vec3 hit = t.position().add(0.0, (double)t.getBbHeight() * 0.6, 0.0);
         Net.fx(l, new Net.Fx((byte)0, hit, p.getLookAngle(), 0.7F, p.getId(), t.getId(), 0));
         if (amber(p) > 0) {
            e.setAmount(e.getAmount() + 6.0F);
            int n = 0;

            for (LivingEntity o : around(l, t.position(), 7.0, p)) {
               if (o != t && n++ < 3) {
                  o.hurt(shock(p), 6.0F);
                  addCharge(l, o, 1);
                  Net.fx(l, new Net.Fx((byte)16, hit, o.position().add(0.0, (double)(o.getBbHeight() / 2.0F), 0.0), 0.25F, p.getId(), -1, 5));
               }
            }
         }
      }
   }

   @SubscribeEvent
   public static void onStop(ServerStoppingEvent e) {
      TIMED.clear();
      COOLDOWNS.clear();
   }

   private abstract static class Timed {
      final ServerLevel level;
      final ServerPlayer caster;
      final int length;
      final boolean holds;
      final int holdUntil;
      final Vec3 at;
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
