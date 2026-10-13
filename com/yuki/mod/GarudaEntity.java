package com.yuki.mod;

import java.util.ArrayDeque;
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

public class GarudaEntity extends PathfinderMob {
   private UUID owner;
   private int life = 6000;
   private int attackCd;
   private LivingEntity prey;
   public int attackAnim;
   public final ArrayDeque<Vec3> hist = new ArrayDeque<>();
   private static final EntityDataAccessor<Boolean> HIDDEN = SynchedEntityData.defineId(GarudaEntity.class, EntityDataSerializers.BOOLEAN);
   private int approach;
   private int mergeLeft;
   private int hiddenFor;
   private boolean toFeet;
   public static final int BITE_EVERY = 600;

   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(HIDDEN, false);
   }

   public boolean hidden() {
      return (Boolean)this.entityData.get(HIDDEN);
   }

   public void becomeMove(boolean feet, int ticks, int merge) {
      this.life = Math.max(this.life, 6000);
      this.toFeet = feet;
      this.approach = ticks;
      this.mergeLeft = merge;
      this.prey = null;
   }

   public void release(Vec3 at) {
      this.approach = 0;
      this.mergeLeft = 0;
      this.entityData.set(HIDDEN, false);
      this.hiddenFor = 0;
      this.teleportTo(at.x, at.y, at.z);
      this.setDeltaMovement(0.0, 0.25, 0.0);
   }

   private Vec3 grip(ServerPlayer o) {
      Vec3 look = o.getLookAngle();
      Vec3 flat = new Vec3(look.x, 0.0, look.z).normalize();
      return this.toFeet
         ? o.position().add(flat.scale(0.9)).add(0.0, 0.4, 0.0)
         : o.getEyePosition().add(look.scale(0.7)).add(new Vec3(-flat.z, 0.0, flat.x).scale(0.35)).add(0.0, -0.45, 0.0);
   }

   public GarudaEntity(EntityType<? extends GarudaEntity> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
      this.xpReward = 0;
      this.noCulling = true;
   }

   public static Builder createAttributes() {
      return Mob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, 10.0)
         .add(Attributes.ARMOR, 6.0)
         .add(Attributes.ATTACK_DAMAGE, 9.0)
         .add(Attributes.MOVEMENT_SPEED, 0.5)
         .add(Attributes.FLYING_SPEED, 0.6)
         .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
         .add(Attributes.FOLLOW_RANGE, 32.0);
   }

   public boolean ownedBy(UUID u) {
      return u.equals(this.owner);
   }

   public void setOwner(ServerPlayer p) {
      this.owner = p.getUUID();
   }

   public ServerPlayer owner() {
      return this.owner != null && this.level() instanceof ServerLevel sl ? sl.getServer().getPlayerList().getPlayer(this.owner) : null;
   }

   public void tick() {
      this.noPhysics = true;
      super.tick();
      this.setNoGravity(true);
      if (this.level().isClientSide) {
         this.hist.addFirst(this.position());

         while (this.hist.size() > 80) {
            this.hist.removeLast();
         }

         if (this.attackAnim > 0) {
            this.attackAnim--;
         }

         if (this.hidden()) {
            this.hist.clear();
         }
      } else {
         ServerPlayer o = this.owner();
         if (o != null && o.isAlive() && --this.life > 0 && Yuki.isYuki(o) && o.level() == this.level()) {
            if (this.attackCd > 0) {
               this.attackCd--;
            }

            if (this.approach > 0) {
               Vec3 g = this.grip(o);
               this.setPos(this.position().lerp(g, 0.55));
               this.setDeltaMovement(Vec3.ZERO);
               if (--this.approach == 0) {
                  this.entityData.set(HIDDEN, true);
               }
            } else if (this.hidden()) {
               this.setPos(this.grip(o));
               this.setDeltaMovement(Vec3.ZERO);
               if (this.mergeLeft > 0 && --this.mergeLeft == 0 || ++this.hiddenFor > 120) {
                  this.hiddenFor = 0;
                  this.entityData.set(HIDDEN, false);
               }
            } else {
               this.hiddenFor = 0;
               if (this.prey != null && (!this.prey.isAlive() || this.prey.distanceToSqr(o) > 576.0 || this.prey == o)) {
                  this.prey = null;
               }

               if (this.prey == null && this.tickCount % 10 == 0) {
                  this.prey = this.pickPrey(o);
               }

               Vec3 target;
               if (this.prey != null) {
                  target = this.prey.position().add(0.0, (double)this.prey.getBbHeight() * 0.6, 0.0);
               } else {
                  float a = (float)this.tickCount * 0.07F;
                  float beat = Mth.sin((float)this.tickCount * 0.21F);
                  target = o.position()
                     .add((double)Mth.cos(a) * (2.8 + (double)beat * 0.3), 1.9 + (double)beat * 0.45, (double)Mth.sin(a) * (2.8 + (double)beat * 0.3));
               }

               if (this.distanceTo(o) > 40.0F) {
                  this.teleportTo(o.getX(), o.getY() + 3.0, o.getZ());
               }

               Vec3 to = target.subtract(this.position());
               double d = to.length();
               double speed = this.prey != null ? 0.6 : Math.min(0.5, 0.05 + d * 0.09);
               Vec3 v = this.getDeltaMovement().scale(0.8);
               if (d > 0.3) {
                  v = v.add(to.scale(1.0 / d).scale(speed * 0.3));
               }

               this.setDeltaMovement(v);
               Vec3 look = this.prey != null ? to : (v.lengthSqr() > 0.003 ? v : this.getLookAngle());
               float yawDeg = (float)(Mth.atan2(look.z, look.x) * 180.0F / (float)Math.PI) - 90.0F;
               float pitch = (float)(-(Mth.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z)) * 180.0F / (float)Math.PI));
               this.setYRot(yawDeg);
               this.yBodyRot = yawDeg;
               this.yHeadRot = yawDeg;
               this.setXRot(pitch);
               if (this.prey != null && this.attackCd == 0 && (double)this.distanceTo(this.prey) < 2.6 + (double)this.prey.getBbWidth()) {
                  this.bite(o);
               }
            }
         } else {
            this.dismiss();
         }
      }
   }

   private LivingEntity pickPrey(ServerPlayer o) {
      LivingEntity c = this.getLastHurtByMob();
      if (this.valid(c, o)) {
         return c;
      } else {
         c = o.getLastHurtByMob();
         if (this.valid(c, o)) {
            return c;
         } else {
            c = o.getLastHurtMob();
            if (this.valid(c, o)) {
               return c;
            } else {
               LivingEntity best = null;

               for (LivingEntity e : this.level()
                  .getEntitiesOfClass(LivingEntity.class, new AABB(o.blockPosition()).inflate(16.0), ex -> ex instanceof Enemy && this.valid(ex, o))) {
                  if (best == null || e.distanceToSqr(o) < best.distanceToSqr(o)) {
                     best = e;
                  }
               }

               return best;
            }
         }
      }
   }

   private boolean valid(LivingEntity e, ServerPlayer o) {
      return e != null
         && e.isAlive()
         && e != o
         && e != this
         && !(e instanceof GarudaEntity)
         && e.distanceToSqr(o) < 400.0
         && (!(e instanceof Player pl) || !pl.isCreative() && !pl.isSpectator());
   }

   private void bite(ServerPlayer o) {
      this.attackCd = 600;
      float dmg = (float)this.getAttributeValue(Attributes.ATTACK_DAMAGE) * Yuki.mass(o);
      this.prey.invulnerableTime = 0;
      this.prey.hurt(this.damageSources().mobAttack(this), dmg);
      Vec3 out = this.prey.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
      if (out.lengthSqr() > 1.0E-4) {
         out = out.normalize();
         this.prey.setDeltaMovement(this.prey.getDeltaMovement().add(out.x * 0.9, 0.35, out.z * 0.9));
         this.prey.hurtMarked = true;
      }

      this.level().broadcastEntityEvent(this, (byte)4);
      this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.NEUTRAL, 1.2F, 0.6F);
      this.level().playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.SKELETON_HURT, SoundSource.NEUTRAL, 1.0F, 0.5F);
   }

   public void dismiss() {
      if (this.level() instanceof ServerLevel sl) {
         Net.fx(sl, new Net.Fx((byte)14, this.position(), Vec3.ZERO, 0.0F, -1, this.getId(), 0));
         sl.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BONE_BLOCK_BREAK, SoundSource.NEUTRAL, 1.2F, 0.6F);
      }

      ServerPlayer o = this.owner();
      if (o != null) {
         Yuki.garudaGone(o);
      }

      this.discard();
   }

   public void handleEntityEvent(byte id) {
      if (id == 4) {
         this.attackAnim = 10;
      } else {
         super.handleEntityEvent(id);
      }
   }

   public boolean hurt(DamageSource source, float amount) {
      if (this.owner != null && source.getEntity() != null && this.owner.equals(source.getEntity().getUUID())) {
         return false;
      } else {
         return !source.is(DamageTypeTags.IS_FALL) && !source.is(DamageTypeTags.IS_DROWNING) ? super.hurt(source, amount) : false;
      }
   }

   public void die(DamageSource source) {
      super.die(source);
      ServerPlayer o = this.owner();
      if (o != null) {
         Yuki.garudaGone(o);
      }
   }

   public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
      return false;
   }

   public boolean removeWhenFarAway(double distance) {
      return false;
   }

   public boolean requiresCustomPersistence() {
      return true;
   }

   public boolean isPushable() {
      return false;
   }

   public boolean canBeLeashed(Player player) {
      return false;
   }

   protected SoundEvent getAmbientSound() {
      return SoundEvents.SKELETON_AMBIENT;
   }

   protected SoundEvent getHurtSound(DamageSource source) {
      return SoundEvents.SKELETON_HURT;
   }

   protected SoundEvent getDeathSound() {
      return SoundEvents.SKELETON_DEATH;
   }

   public float getVoicePitch() {
      return 0.55F;
   }

   public void addAdditionalSaveData(CompoundTag tag) {
      super.addAdditionalSaveData(tag);
      if (this.owner != null) {
         tag.putUUID("Owner", this.owner);
      }

      tag.putInt("Life", this.life);
   }

   public void readAdditionalSaveData(CompoundTag tag) {
      super.readAdditionalSaveData(tag);
      if (tag.hasUUID("Owner")) {
         this.owner = tag.getUUID("Owner");
      }

      this.life = tag.getInt("Life");
   }
}
