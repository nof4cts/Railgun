package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier.Builder;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public abstract class CurseEntity extends PathfinderMob {
   private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(CurseEntity.class, EntityDataSerializers.INT);
   protected UUID ownerUuid;
   protected int life = 1200;
   protected int attackCd;
   protected int specialCd = 60;
   protected int signatureCd = 160;
   public int vowTicks;

   protected CurseEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.xpReward = 0;
   }

   public static Builder attributes(double hp, double speed, double damage) {
      return PathfinderMob.createMobAttributes()
         .add(Attributes.MAX_HEALTH, hp)
         .add(Attributes.MOVEMENT_SPEED, speed)
         .add(Attributes.ATTACK_DAMAGE, damage)
         .add(Attributes.FOLLOW_RANGE, 40.0)
         .add(Attributes.KNOCKBACK_RESISTANCE, 0.8)
         .add(Attributes.FLYING_SPEED, 0.6);
   }

   public abstract CurseKind kind();

   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(OWNER_ID, -1);
   }

   public void bind(ServerPlayer owner) {
      this.ownerUuid = owner.getUUID();
      this.entityData.set(OWNER_ID, owner.getId());
      this.life = this.kind().lifetime;
   }

   public int ownerId() {
      return (Integer)this.entityData.get(OWNER_ID);
   }

   public ServerPlayer owner() {
      if (this.ownerUuid != null && this.level() instanceof ServerLevel sl) {
         ServerPlayer var10000;
         if (sl.getPlayerByUUID(this.ownerUuid) instanceof ServerPlayer sp) {
            var10000 = sp;
         } else {
            var10000 = null;
         }

         return var10000;
      } else {
         return null;
      }
   }

   public boolean isFriend(Entity e) {
      if (e == null) {
         return false;
      } else if (e != this && e.getId() != this.ownerId()) {
         if (e instanceof CurseEntity c && c.ownerId() == this.ownerId()) {
            return true;
         }

         return false;
      } else {
         return true;
      }
   }

   protected void registerGoals() {
   }

   public boolean shouldBeSaved() {
      return false;
   }

   public boolean removeWhenFarAway(double d) {
      return false;
   }

   public boolean causeFallDamage(float dist, float mult, DamageSource src) {
      return false;
   }

   public boolean isAlliedTo(Entity e) {
      return this.isFriend(e) || super.isAlliedTo(e);
   }

   public boolean hurt(DamageSource src, float amount) {
      if (this.vowTicks > 0) {
         return false;
      } else {
         return this.isFriend(src.getEntity()) ? false : super.hurt(src, amount * this.damageTaken());
      }
   }

   protected float damageTaken() {
      return 1.0F;
   }

   protected boolean hurtDirect(DamageSource src, float amount) {
      return super.hurt(src, amount * this.damageTaken());
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         ServerPlayer o = this.owner();
         if (this.isVehicle()) {
            this.life = Math.max(this.life, 200);

            for (Entity p : this.getPassengers()) {
               p.fallDistance = 0.0F;
            }
         }

         if (o != null && o.isAlive() && o.level() == this.level() && --this.life > 0) {
            if (this.attackCd > 0) {
               this.attackCd--;
            }

            if (this.specialCd > 0) {
               this.specialCd--;
            }

            if (this.signatureCd > 0) {
               this.signatureCd--;
            }

            if (this.vowTicks > 0) {
               this.vowTicks--;
               this.setDeltaMovement(Vec3.ZERO);
            } else if (this.holdStill()) {
               this.getNavigation().stop();
               this.setDeltaMovement(this.getDeltaMovement().multiply(0.0, 1.0, 0.0));
            } else if (!this.isVehicle() || this.getControllingPassenger() == null) {
               this.serverAi(o);
            }
         } else {
            this.vanish();
         }
      }
   }

   protected abstract void serverAi(ServerPlayer var1);

   protected boolean holdStill() {
      return false;
   }

   public void vanish() {
      CsmNetwork.fx(
         this, FxType.VANISH, this, (Entity)null, this.position().add(0.0, (double)(this.getBbHeight() / 2.0F), 0.0), (float)this.kind().ordinal(), 0.0F
      );
      this.discard();
   }

   protected LivingEntity findTarget(ServerPlayer o, double range) {
      LivingEntity t = o.getLastHurtMob();
      if (this.valid(t, o, range)) {
         return t;
      } else {
         t = o.getLastHurtByMob();
         if (this.valid(t, o, range)) {
            return t;
         } else {
            t = this.getTarget();
            if (this.valid(t, o, range)) {
               return t;
            } else {
               AABB box = o.getBoundingBox().inflate(16.0);
               List<LivingEntity> list = this.level()
                  .getEntitiesOfClass(LivingEntity.class, box, e -> e instanceof Enemy && !this.isFriend(e) && e.isAlive() && !(e instanceof CurseEntity));
               t = list.stream().min(Comparator.comparingDouble(e -> e.distanceToSqr(this))).orElse(null);
               this.setTarget(t);
               return t;
            }
         }
      }
   }

   private boolean valid(LivingEntity t, ServerPlayer o, double range) {
      return t != null && t.isAlive() && !this.isFriend(t) && t.level() == this.level() && (double)t.distanceTo(o) < range && !t.isSpectator();
   }

   protected List<LivingEntity> enemiesAround(Vec3 c, double r) {
      return this.level()
         .getEntitiesOfClass(
            LivingEntity.class,
            new AABB(c, c).inflate(r),
            e -> e.isAlive() && !this.isFriend(e) && !e.isSpectator() && e.getBoundingBox().getCenter().distanceTo(c) <= r + (double)(e.getBbWidth() / 2.0F)
         );
   }

   public void strike(LivingEntity t, float amount) {
      if (t != null && t.isAlive() && !this.isFriend(t)) {
         t.invulnerableTime = 0;
         t.hurt(this.damageSources().mobAttack(this), amount);
      }
   }

   public static void launch(LivingEntity e, Vec3 v) {
      e.setDeltaMovement(v);
      e.hurtMarked = true;
      e.hasImpulse = true;
   }

   protected void sound(SoundEvent s, float vol, float pitch) {
      this.level().playSound((Player)null, this.getX(), this.getY(), this.getZ(), s, SoundSource.HOSTILE, vol, pitch);
   }

   protected void action(int id) {
      CsmNetwork.fx(this, FxType.ACTION, this, (Entity)null, this.position(), (float)id, 0.0F);
   }

   protected void faceToward(Vec3 p) {
      Vec3 d = p.subtract(this.position());
      if (!(d.horizontalDistanceSqr() < 1.0E-4)) {
         float yaw = (float)(Mth.atan2(d.z, d.x) * 180.0F / (float)Math.PI) - 90.0F;
         float cur = this.getYRot();
         float next = cur + Mth.clamp(Mth.wrapDegrees(yaw - cur), -12.0F, 12.0F);
         this.setYRot(next);
         this.yBodyRot = next;
         this.yHeadRot = next;
      }
   }

   protected void flyToward(Vec3 target, double speed) {
      Vec3 d = target.subtract(this.position());
      double len = d.length();
      Vec3 want = len < 0.001 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
      this.setDeltaMovement(this.getDeltaMovement().lerp(want, 0.12));
      if (want.lengthSqr() > 1.0E-4) {
         this.faceToward(this.position().add(want.scale(10.0)));
      }
   }

   protected void walkToward(LivingEntity t, double speed) {
      this.getNavigation().moveTo(t, speed);
      this.faceToward(t.position());
   }

   protected void walkTo(Vec3 p, double speed) {
      this.getNavigation().moveTo(p.x, p.y, p.z, speed);
   }

   protected void followOwner(ServerPlayer o, double dist, double speed, boolean fly) {
      double d = (double)this.distanceTo(o);
      if (fly) {
         double a = (double)this.tickCount * 0.02 + (double)this.getId();
         Vec3 orbit = o.position().add(Math.cos(a) * dist, 3.5, Math.sin(a) * dist);
         this.flyToward(orbit, speed);
      } else if (d > dist) {
         this.walkTo(o.position(), speed);
      } else {
         this.getNavigation().stop();
      }

      if (d > 40.0) {
         this.teleportTo(o.getX(), o.getY() + (double)(fly ? 3 : 0), o.getZ());
      }
   }

   public boolean rideable() {
      return false;
   }

   protected int maxRiders() {
      return 1;
   }

   protected double rideSpeed() {
      return 0.8;
   }

   public LivingEntity getControllingPassenger() {
      if (!this.rideable()) {
         return null;
      } else {
         Player var10000;
         if (this.getFirstPassenger() instanceof Player pl) {
            var10000 = pl;
         } else {
            var10000 = null;
         }

         return var10000;
      }
   }

   protected boolean canAddPassenger(Entity e) {
      return this.rideable() && this.getPassengers().size() < this.maxRiders();
   }

   protected InteractionResult mobInteract(Player player, InteractionHand hand) {
      if (this.rideable() && !player.isShiftKeyDown()) {
         if (!this.level().isClientSide) {
            boolean ownerOrPassengerSlot = player.getId() == this.ownerId() || this.maxRiders() > 1;
            if (ownerOrPassengerSlot) {
               player.startRiding(this);
            }
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else {
         return super.mobInteract(player, hand);
      }
   }

   protected void travelGround(Vec3 input) {
      super.travel(input);
   }

   public void travel(Vec3 input) {
      if (this.rideable() && this.isVehicle() && this.getControllingPassenger() instanceof Player p) {
         this.setYRot(p.getYRot());
         this.yRotO = this.getYRot();
         this.setXRot(p.getXRot() * 0.5F);
         this.yBodyRot = this.getYRot();
         this.yHeadRot = this.getYRot();
         float fwd = p.zza;
         float side = p.xxa;
         Vec3 look = p.getLookAngle();
         Vec3 right = new Vec3(-look.z, 0.0, look.x);
         if (right.lengthSqr() > 1.0E-4) {
            right = right.normalize();
         }

         double sp = this.rideSpeed();
         Vec3 want = look.scale(fwd >= 0.0F ? (double)fwd * sp : (double)fwd * sp * 0.4).add(right.scale((double)(-side) * sp * 0.6));
         Vec3 v = this.getDeltaMovement().lerp(want, 0.12);
         this.setDeltaMovement(v);
         this.move(MoverType.SELF, v);
      } else {
         super.travel(input);
      }
   }
}
