package com.yuta.mod;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class RikaEntity extends PathfinderMob {
   public static final double ORBIT = 5.5;
   public static final double HOVER = 0.6;
   public static final byte ANIM_RAISE = 110;
   public static final byte ANIM_SLAM = 111;
   public static final byte ANIM_SCREAM = 112;
   public static final byte ANIM_SWIPE = 113;
   private static final EntityDataAccessor<Boolean> FULL = SynchedEntityData.defineId(RikaEntity.class, EntityDataSerializers.BOOLEAN);
   private UUID owner;
   private int guardCd;
   private LivingEntity prey;
   private Vec3 orderAt;
   private Vec3 orderFace;
   private int orderLeft;
   public byte anim;
   public long animAt;
   public static final int GUARD_EVERY = 80;

   public RikaEntity(EntityType<? extends RikaEntity> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.xpReward = 0;
      this.noCulling = true;
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 400.0)
         .add(Attributes.ARMOR, 12.0)
         .add(Attributes.ATTACK_DAMAGE, 10.0)
         .add(Attributes.MOVEMENT_SPEED, 0.5)
         .add(Attributes.FLYING_SPEED, 0.6)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.FOLLOW_RANGE, 32.0);
   }

   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(FULL, false);
   }

   public boolean full() {
      return (Boolean)this.entityData.get(FULL);
   }

   public void setFull(boolean on) {
      this.entityData.set(FULL, on);
   }

   public void setOwner(ServerPlayer p) {
      this.owner = p.getUUID();
   }

   public boolean ownedBy(UUID u) {
      return u.equals(this.owner);
   }

   public ServerPlayer owner() {
      return this.owner != null && this.level() instanceof ServerLevel sl ? sl.getServer().getPlayerList().getPlayer(this.owner) : null;
   }

   public void order(Vec3 at, Vec3 face, int ticks) {
      this.orderAt = at;
      this.orderFace = face;
      this.orderLeft = ticks;
      this.prey = null;
   }

   public void anim(byte id) {
      this.level().broadcastEntityEvent(this, id);
   }

   public void tick() {
      this.noPhysics = true;
      super.tick();
      this.setNoGravity(true);
      if (!this.level().isClientSide) {
         ServerPlayer o = this.owner();
         if (o != null && o.isAlive() && Yuta.isYuta(o) && o.level() == this.level()) {
            if (this.tickCount % 20 == 0 && this.getHealth() < this.getMaxHealth()) {
               this.heal(2.0F);
            }

            if (this.guardCd > 0) {
               this.guardCd--;
            }

            if (this.distanceTo(o) > 48.0F) {
               this.teleportTo(o.getX(), o.getY() + 2.0, o.getZ());
            }

            Vec3 target;
            Vec3 face;
            double speed;
            if (this.orderLeft > 0) {
               this.orderLeft--;
               target = this.orderAt;
               face = this.orderFace;
               speed = 0.95;
            } else {
               if (this.prey != null && (!this.prey.isAlive() || this.prey.distanceToSqr(o) > 400.0)) {
                  this.prey = null;
               }

               if (this.prey == null && this.tickCount % (this.full() ? 5 : 10) == 0) {
                  this.prey = this.pickPrey(o);
               }

               if (this.prey != null && this.guardCd == 0) {
                  Vec3 side = this.prey.position().subtract(o.position()).multiply(1.0, 0.0, 1.0);
                  side = side.lengthSqr() < 1.0E-4 ? new Vec3(1.0, 0.0, 0.0) : side.normalize();
                  target = this.prey.position().subtract(side.scale(2.2)).add(0.0, 0.2, 0.0);
                  face = this.prey.position().subtract(this.position());
                  speed = 0.7;
                  if ((double)this.distanceTo(this.prey) < 4.2 + (double)this.prey.getBbWidth()) {
                     this.swipe(o);
                  }
               } else {
                  float a = (float)this.tickCount * 0.022F;
                  float bob = Mth.sin((float)this.tickCount * 0.05F);
                  target = o.position().add((double)Mth.cos(a) * 5.5, 0.6 + (double)bob * 0.35, (double)Mth.sin(a) * 5.5);
                  Vec3 look = o.getLookAngle();
                  face = o.getEyePosition().add(look.scale(12.0)).subtract(this.position());
                  speed = 0.35;
               }
            }

            Vec3 to = target.subtract(this.position());
            double d = to.length();
            Vec3 v = this.getDeltaMovement().scale(0.78);
            if (d > 0.15) {
               v = v.add(to.scale(1.0 / d).scale(Math.min(speed, 0.04 + d * 0.12) * 0.32));
            }

            this.setDeltaMovement(v);
            if (face.lengthSqr() > 1.0E-4) {
               float yaw = (float)(Mth.atan2(face.z, face.x) * 180.0F / (float)Math.PI) - 90.0F;
               float turned = Mth.approachDegrees(this.getYRot(), yaw, 9.0F);
               this.setYRot(turned);
               this.yBodyRot = turned;
               this.yHeadRot = turned;
            }
         } else {
            this.dismiss();
         }
      }
   }

   private LivingEntity pickPrey(ServerPlayer o) {
      LivingEntity c = o.getLastHurtByMob();
      if (this.valid(c, o) && o.tickCount - o.getLastHurtByMobTimestamp() < 200) {
         return c;
      } else {
         c = o.getLastHurtMob();
         if (this.valid(c, o) && o.tickCount - o.getLastHurtMobTimestamp() < 100) {
            return c;
         } else {
            LivingEntity best = null;

            for (LivingEntity e : this.level()
               .getEntitiesOfClass(
                  LivingEntity.class, new AABB(o.blockPosition()).inflate(this.full() ? 18.0 : 10.0), ex -> ex instanceof Enemy && this.valid(ex, o)
               )) {
               if (best == null || e.distanceToSqr(o) < best.distanceToSqr(o)) {
                  best = e;
               }
            }

            return best;
         }
      }
   }

   private boolean valid(LivingEntity e, ServerPlayer o) {
      return e != null
         && e.isAlive()
         && e != o
         && e != this
         && !(e instanceof RikaEntity)
         && e.distanceToSqr(o) < 256.0
         && (!(e instanceof Player pl) || !pl.isCreative() && !pl.isSpectator());
   }

   private void swipe(ServerPlayer o) {
      this.guardCd = this.full() ? 20 : 80;
      this.anim((byte)113);
      this.prey.invulnerableTime = 0;
      this.prey.hurt(this.damageSources().mobAttack(this), (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE) * (this.full() ? 1.6F : 1.0F));
      Vec3 out = this.prey.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
      if (out.lengthSqr() > 1.0E-4) {
         out = out.normalize();
         this.prey.setDeltaMovement(this.prey.getDeltaMovement().add(out.x * 1.1, 0.4, out.z * 1.1));
         this.prey.hurtMarked = true;
      }

      this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.4F, 0.5F);
      this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_ATTACK_IMPACT, SoundSource.NEUTRAL, 1.0F, 0.9F);
   }

   public void dismiss() {
      if (this.level() instanceof ServerLevel sl) {
         Net.fx(sl, new Net.Fx((byte)6, this.position().add(0.0, 2.0, 0.0), Vec3.ZERO, 0.0F, -1, this.getId(), 0));
         sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SOUL_ESCAPE, SoundSource.NEUTRAL, 2.0F, 0.5F);
      }

      this.discard();
   }

   public void handleEntityEvent(byte id) {
      if (id >= 110 && id <= 113) {
         this.anim = id;
         this.animAt = this.level().getGameTime();
      } else {
         super.handleEntityEvent(id);
      }
   }

   public boolean hurt(DamageSource source, float amount) {
      if (this.owner != null && source.getEntity() != null && this.owner.equals(source.getEntity().getUUID())) {
         return false;
      } else {
         return !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_DROWNING) && !source.is(DamageTypes.IN_WALL)
            ? super.hurt(source, amount)
            : false;
      }
   }

   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   public boolean isPushable() {
      return false;
   }

   public boolean canBeLeashed(Player player) {
      return false;
   }

   protected SoundEvent getAmbientSound() {
      return null;
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.WARDEN_HURT;
   }

   protected SoundEvent getDeathSound() {
      return SoundEvents.WARDEN_DEATH;
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.owner != null) {
         tag.putUUID("Owner", this.owner);
      }
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.hasUUID("Owner")) {
         this.owner = tag.getUUID("Owner");
      }
   }
}
