package com.avatar.mod;

import com.jjk.mod.Energy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.event.entity.EntityStruckByLightningEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber(
   modid = "jjk"
)
public final class Lightning {
   public static final int GEN = 30;
   public static final int BACKFIRE = 160;
   public static final int CUT = 30;
   public static final int STRIKE_AT = 16;
   public static final int STANCE = 60;
   public static final int REDIRECT = 26;
   public static final int REDIRECT_FIRE = 20;
   private static final Map<UUID, Long> CHARGING = new HashMap<>();
   private static final Map<UUID, Long> STANCES = new HashMap<>();

   private Lightning() {
   }

   static void press(ServerPlayer p) {
      if (Energy.has(p, 50)) {
         CHARGING.put(p.getUUID(), Bending.now);
         ServerLevel l = p.serverLevel();
         Net.fx(l, new Net.Fx((byte)10, p.position(), Vec3.ZERO, 1.0F, p.getId(), 0, 30));
         Bending.pose(p, 9, 170);
         Bending.play(l, p.position(), (SoundEvent)AvatarMod.ZAP.get(), 0.6F, 1.4F);
      }
   }

   static void release(ServerPlayer p) {
      Long s = CHARGING.remove(p.getUUID());
      if (s != null) {
         ServerLevel l = p.serverLevel();
         Net.fx(l, new Net.Fx((byte)10, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
         if (Bending.now - s < 30L) {
            Bending.pose(p, 9, 0);
            Bending.play(l, p.position(), (SoundEvent)AvatarMod.ZAP.get(), 0.5F, 0.7F);
            Bending.setCooldown(p, 4, 20);
         } else {
            strike(p);
         }
      }
   }

   private static void strike(ServerPlayer p) {
      if (Energy.has(p, 50)) {
         Elem.spend(p, 50);
         Bending.setCooldown(p, 4, 300);
         ServerLevel l = p.serverLevel();
         Vec3 look = p.getLookAngle();
         Bending.hold(p, 24);
         Net.fx(l, new Net.Fx((byte)13, p.getEyePosition(), look, 0.0F, p.getId(), 0, 30));
         Bending.pose(p, 10, 26);
         Bending.play(l, p.position(), (SoundEvent)AvatarMod.ZAP.get(), 1.0F, 1.0F);
         Bending.later(16, () -> {
            if (p.isAlive() && !p.isRemoved()) {
               fire(p, fingertip(p, look), look, 30.0F, true);
            }
         });
      }
   }

   static Vec3 fingertip(ServerPlayer p, Vec3 dir) {
      return Bending.fist(p, 1).add(dir.scale(0.25));
   }

   static void fire(ServerPlayer p, Vec3 from, Vec3 dir, float dmg, boolean impactFrames) {
      ServerLevel l = p.serverLevel();
      Vec3 end = from.add(dir.scale(80.0));
      BlockHitResult bh = l.clip(new ClipContext(from, end, Block.COLLIDER, Fluid.NONE, p));
      if (bh.getType() != Type.MISS) {
         end = bh.getLocation();
      }

      EntityHitResult eh = ProjectileUtil.getEntityHitResult(
         l, p, from, end, new AABB(from, end).inflate(1.0), ex -> ex instanceof LivingEntity && ex.isAlive() && !ex.isSpectator() && ex != p
      );
      Vec3 at = eh != null ? eh.getLocation() : end;
      Entity hit = eh != null ? eh.getEntity() : null;
      int flags = impactFrames ? 2 : 0;
      Net.fx(l, new Net.Fx((byte)12, from, at, impactFrames ? 1.0F : 0.8F, p.getId(), hit != null ? hit.getId() : -1, 10, flags));
      Bending.play(l, from, (SoundEvent)AvatarMod.CRACK.get(), 2.0F, 1.2F);
      Bending.play(l, at, (SoundEvent)AvatarMod.THUNDER.get(), 4.0F, 1.0F);
      Bending.play(l, at, (SoundEvent)AvatarMod.ZAP.get(), 1.5F, 0.8F);
      if (hit instanceof ServerPlayer sp && inStance(sp)) {
         redirect(sp, p);
         return;
      }

      if (hit instanceof LivingEntity le) {
         Bending.burn(p, le, dmg, 4);
         Bending.knock(le, dir, 1.2, 0.35);
         le.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 3, false, false));
      }

      for (LivingEntity e : l.getEntitiesOfClass(
         LivingEntity.class, new AABB(at, at).inflate(2.5), ex -> ex != p && ex != hit && ex.isAlive() && !ex.isSpectator()
      )) {
         if (!(e instanceof ServerPlayer sp) || !inStance(sp)) {
            Bending.burn(p, e, dmg * 0.35F, 2);
         }
      }

      if (hit == null && bh.getType() != Type.MISS && l.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
         BlockPos pos = bh.getBlockPos().relative(bh.getDirection());
         if (l.isEmptyBlock(pos) && BaseFireBlock.canBePlacedAt(l, pos, Direction.UP)) {
            l.setBlockAndUpdate(pos, BaseFireBlock.getState(l, pos));
         }
      }
   }

   public static boolean charging(ServerPlayer p) {
      return CHARGING.containsKey(p.getUUID());
   }

   static int chargeTicks(ServerPlayer p) {
      Long s = CHARGING.get(p.getUUID());
      return s == null ? -1 : (int)(Bending.now - s);
   }

   static void stance(ServerPlayer p) {
      if (Energy.has(p, 10)) {
         Elem.spend(p, 10);
         Bending.setCooldown(p, 5, 100);
         ServerLevel l = p.serverLevel();
         STANCES.put(p.getUUID(), Bending.now + 60L);
         Net.fx(l, new Net.Fx((byte)17, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 60));
         Bending.pose(p, 11, 60);
         Bending.play(l, p.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.5F, 0.6F);
         if (l.isThundering() && l.canSeeSky(p.blockPosition().above())) {
            Bending.later(14, () -> {
               if (inStance(p)) {
                  Vec3 hand = Bending.fist(p, -1);
                  Vec3 sky = hand.add(l.random.nextGaussian() * 6.0, 45.0, l.random.nextGaussian() * 6.0);
                  Net.fx(l, new Net.Fx((byte)12, sky, hand, 1.2F, p.getId(), p.getId(), 8, 0));
                  Bending.play(l, hand, (SoundEvent)AvatarMod.THUNDER.get(), 5.0F, 0.9F);
                  redirect(p, null);
               }
            });
         }
      }
   }

   public static boolean inStance(ServerPlayer p) {
      Long t = STANCES.get(p.getUUID());
      return t != null && t > Bending.now && Bending.isFire(p);
   }

   static int stanceLeft(ServerPlayer p) {
      Long t = STANCES.get(p.getUUID());
      return t == null ? 0 : (int)Math.max(0L, t - Bending.now);
   }

   static void redirect(ServerPlayer p, Entity sender) {
      STANCES.remove(p.getUUID());
      ServerLevel l = p.serverLevel();
      Vec3 look = p.getLookAngle();
      Vec3 target = sender != null && sender.isAlive() ? sender.getBoundingBox().getCenter() : p.getEyePosition().add(look.scale(30.0));
      Bending.hold(p, 26);
      Bending.setCooldown(p, 5, 200);
      Net.fx(l, new Net.Fx((byte)16, Bending.fist(p, -1), target, 0.0F, p.getId(), sender != null ? sender.getId() : -1, 26));
      Bending.pose(p, 11, 26);
      Bending.play(l, p.position(), (SoundEvent)AvatarMod.ZAP.get(), 1.5F, 0.6F);
      Bending.later(20, () -> {
         if (p.isAlive() && !p.isRemoved()) {
            Vec3 from = Bending.fist(p, 1);
            Vec3 aimAt = sender != null && sender.isAlive() ? sender.getBoundingBox().getCenter() : p.getEyePosition().add(p.getLookAngle().scale(30.0));
            fire(p, from, aimAt.subtract(from).normalize(), 34.0F, false);
         }
      });
   }

   @SubscribeEvent
   public static void onStruck(EntityStruckByLightningEvent e) {
      if (e.getEntity() instanceof ServerPlayer p && inStance(p)) {
         e.setCanceled(true);
         redirect(p, e.getLightning().getCause());
      }
   }

   static void tickPlayer(ServerPlayer p) {
      Long s = CHARGING.get(p.getUUID());
      if (s != null) {
         ServerLevel l = p.serverLevel();
         long held = Bending.now - s;
         if (Bending.isFire(p) && p.isAlive()) {
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4, 3, false, false, false));
            if (held == 30L) {
               Net.fx(l, new Net.Fx((byte)11, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
               Bending.play(l, p.position(), (SoundEvent)AvatarMod.ZAP.get(), 1.0F, 1.6F);
            } else if (held % 7L == 0L) {
               Bending.play(l, p.position(), (SoundEvent)AvatarMod.ZAP.get(), 0.35F, 1.2F + l.random.nextFloat() * 0.6F);
            }

            if (held >= 160L) {
               CHARGING.remove(p.getUUID());
               Net.fx(l, new Net.Fx((byte)10, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
               Bending.pose(p, 9, 0);
               Vec3 hand = Bending.fist(p, 1);
               Net.fx(l, new Net.Fx((byte)12, hand, hand.add(l.random.nextGaussian(), -1.2, l.random.nextGaussian()), 0.6F, p.getId(), p.getId(), 6, 0));
               Bending.play(l, hand, (SoundEvent)AvatarMod.CRACK.get(), 1.5F, 1.4F);
               p.hurt(l.damageSources().lightningBolt(), 5.0F);
               Bending.setCooldown(p, 4, 60);
            }
         } else {
            leave(p);
         }
      }
   }

   static void leave(ServerPlayer p) {
      if (CHARGING.remove(p.getUUID()) != null) {
         Net.fx(p.serverLevel(), new Net.Fx((byte)10, p.position(), Vec3.ZERO, 0.0F, p.getId(), 0, 0));
      }

      STANCES.remove(p.getUUID());
   }

   static void clear() {
      CHARGING.clear();
      STANCES.clear();
   }
}
