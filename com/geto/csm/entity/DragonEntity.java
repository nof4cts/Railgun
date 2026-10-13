package com.geto.csm.entity;

import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Entity.MoveFunction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.animation.AnimatableManager.ControllerRegistrar;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

public abstract class DragonEntity extends CurseEntity implements GeoEntity {
   private static final EntityDataAccessor<Boolean> SADDLED = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Boolean> SITTING = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.BOOLEAN);
   private static final EntityDataAccessor<Integer> FADE_OUT = SynchedEntityData.defineId(DragonEntity.class, EntityDataSerializers.INT);
   private static final String SADDLE_TAG = "csm_dragon_saddles";
   public static final int MATERIALIZE = 45;
   public static final int DISSOLVE = 35;
   private static final Vector3f MIST_BLACK = new Vector3f(0.06F, 0.02F, 0.09F);
   private static final Vector3f MIST_VIOLET = new Vector3f(0.42F, 0.16F, 0.62F);
   private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
   public Vec3 clientSeat;
   public long clientSeatTime;
   public Matrix4f clientSeatMat;
   public long clientSeatNanos;
   public Vec3 clientHead;

   protected DragonEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(SADDLED, false);
      this.entityData.define(SITTING, false);
      this.entityData.define(FADE_OUT, 0);
   }

   public boolean isSaddled() {
      return (Boolean)this.entityData.get(SADDLED);
   }

   public boolean isSitting() {
      return (Boolean)this.entityData.get(SITTING);
   }

   public boolean fading() {
      return (Integer)this.entityData.get(FADE_OUT) > 0;
   }

   public float fade(float partial) {
      float in = Mth.clamp(((float)this.tickCount + partial) / 45.0F, 0.0F, 1.0F);
      int f = (Integer)this.entityData.get(FADE_OUT);
      float out = f > 0 ? Mth.clamp(((float)f - partial) / 35.0F, 0.0F, 1.0F) : 1.0F;
      return Math.min(in * in * (3.0F - 2.0F * in), out * out * (3.0F - 2.0F * out));
   }

   protected abstract double seatHeight();

   protected abstract double seatForward();

   protected abstract double mouthForward();

   protected abstract double mouthLow();

   protected abstract double mouthHigh();

   protected double mouthUp() {
      return (this.mouthLow() + this.mouthHigh()) * 0.5;
   }

   protected double biteReach() {
      return 2.2;
   }

   protected abstract boolean flyer();

   protected abstract String chompAnim();

   protected abstract SoundEvent chompSound();

   public abstract String[] riderMoves();

   protected abstract void doRiderMove(ServerPlayer var1, int var2);

   @Override
   protected boolean holdStill() {
      return this.isSitting() || this.fading() || this.tickCount < 30;
   }

   protected Vec3 forward() {
      return Vec3.directionFromRotation(0.0F, this.getYRot());
   }

   public Vec3 mouth() {
      Vec3 f = this.forward();
      return this.position().add(f.x * this.mouthForward(), this.mouthUp(), f.z * this.mouthForward());
   }

   protected double mouthDist(LivingEntity t) {
      Vec3 f = this.forward();
      AABB b = t.getBoundingBox();
      double best = Double.MAX_VALUE;

      for (int i = 0; i <= 4; i++) {
         double up = Mth.lerp((double)i / 4.0, this.mouthLow(), this.mouthHigh());
         Vec3 m = this.position().add(f.x * this.mouthForward(), up, f.z * this.mouthForward());
         double dx = Math.max(Math.max(b.minX - m.x, 0.0), m.x - b.maxX);
         double dy = Math.max(Math.max(b.minY - m.y, 0.0), m.y - b.maxY);
         double dz = Math.max(Math.max(b.minZ - m.z, 0.0), m.z - b.maxZ);
         best = Math.min(best, dx * dx + dy * dy + dz * dz);
      }

      return Math.sqrt(best);
   }

   protected Vec3 biteApproach(LivingEntity t) {
      Vec3 c = t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0);
      Vec3 d = c.subtract(this.position()).multiply(1.0, 0.0, 1.0);
      d = d.lengthSqr() < 1.0E-4 ? this.forward() : d.normalize();
      return c.subtract(d.scale(this.mouthForward() * 0.9)).subtract(0.0, this.mouthUp(), 0.0);
   }

   protected List<LivingEntity> inJaws(double extra) {
      Vec3 m = this.mouth();
      return this.level()
         .getEntitiesOfClass(
            LivingEntity.class,
            new AABB(m, m).inflate(this.biteReach() + extra + 2.0 + (this.mouthHigh() - this.mouthLow()) * 0.5),
            e -> e.isAlive() && !this.isFriend(e) && !e.isSpectator() && !this.hasPassenger(e) && this.mouthDist(e) <= this.biteReach() + extra
         );
   }

   protected void jawBite(String anim, SoundEvent snd, int hitTick, float damage, double knock, boolean big, Consumer<LivingEntity> onHit) {
      if (anim != null) {
         this.act(anim);
      }

      if (snd != null) {
         this.sound(snd, 1.8F, 0.9F + this.random.nextFloat() * 0.2F);
      }

      Scheduler.after(
         hitTick,
         () -> {
            if (this.isAlive() && !this.fading()) {
               List<LivingEntity> hit = this.inJaws(0.3);
               Vec3 m = this.mouth();
               this.crescent(hit.isEmpty() ? m : hit.get(0).getBoundingBox().getCenter());
               if (hit.isEmpty()) {
                  CsmNetwork.fx(this, FxType.SMALL_HIT, this, (Entity)null, m, this.getYRot(), 0.0F);
               } else {
                  for (LivingEntity e : hit) {
                     this.strike(e, damage);
                     Vec3 away = e.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
                     away = away.lengthSqr() < 1.0E-4 ? this.forward() : away.normalize();
                     launch(e, away.scale(knock).add(0.0, 0.35 + knock * 0.25, 0.0));
                     if (onHit != null) {
                        onHit.accept(e);
                     }
                  }

                  LivingEntity first = hit.get(0);
                  CsmNetwork.fx(
                     this, FxType.BITE, this, first, first.position().add(0.0, (double)(first.getBbHeight() / 2.0F), 0.0), this.getYRot(), big ? 1.0F : 0.0F
                  );
                  this.sound(SoundEvents.PLAYER_ATTACK_CRIT, 1.6F, 0.6F);
               }
            }
         }
      );
   }

   public void riderMove(ServerPlayer rider, int slot) {
      if (this.hasPassenger(rider) && rider.getId() == this.ownerId() && !this.fading() && slot >= 0 && slot < this.riderMoves().length) {
         if (this.isSitting()) {
            this.setSitting(false);
         }

         this.doRiderMove(rider, slot);
      }
   }

   protected boolean ready(int ticksLeft, ServerPlayer rider, String name) {
      if (ticksLeft <= 0) {
         return true;
      } else {
         rider.displayClientMessage(Component.literal(name + " - " + (ticksLeft + 19) / 20 + "s").withStyle(ChatFormatting.GRAY), true);
         return false;
      }
   }

   protected LivingEntity riderTarget(Player rider, double range) {
      Vec3 eye = rider.getEyePosition();
      Vec3 look = rider.getLookAngle();
      LivingEntity best = null;
      double bestDot = Math.cos(Math.toRadians(14.0));

      for (LivingEntity e : this.enemiesAround(eye.add(look.scale(range * 0.5)), range * 0.5 + 2.0)) {
         if (e != rider && !this.hasPassenger(e)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double len = to.length();
            if (len < range && len > 0.5) {
               double dot = to.scale(1.0 / len).dot(look);
               if (dot > bestDot) {
                  bestDot = dot;
                  best = e;
               }
            }
         }
      }

      return best;
   }

   protected Vec3 riderAim(Player rider, double range) {
      Vec3 eye = rider.getEyePosition();
      Vec3 end = eye.add(rider.getLookAngle().scale(range));
      return this.level().clip(new ClipContext(eye, end, Block.COLLIDER, Fluid.NONE, this)).getLocation();
   }

   @Override
   public void tick() {
      super.tick();
      if (this.level().isClientSide) {
         float f = this.fade(0.0F);
         if (f < 0.999F) {
            this.mist(1.0F - f);
         }
      } else {
         int out = (Integer)this.entityData.get(FADE_OUT);
         if (out > 0) {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.6).add(0.0, this.flyer() ? 0.01 : 0.0, 0.0));
            if (out == 1) {
               super.vanish();
            } else {
               this.entityData.set(FADE_OUT, out - 1);
            }
         }
      }
   }

   private void mist(float amount) {
      int n = 3 + (int)(amount * this.getBbWidth() * this.getBbHeight() * 3.0F);

      for (int i = 0; i < n; i++) {
         double x = this.getRandomX(1.1);
         double y = this.getRandomY();
         double z = this.getRandomZ(1.1);
         double vx = (this.random.nextDouble() - 0.5) * 0.06;
         double vy = 0.02 + this.random.nextDouble() * 0.05;
         double vz = (this.random.nextDouble() - 0.5) * 0.06;
         int pick = this.random.nextInt(5);
         if (pick < 2) {
            this.level().addParticle(new DustParticleOptions(MIST_BLACK, 2.4F), x, y, z, vx, vy, vz);
         } else if (pick < 4) {
            this.level().addParticle(new DustParticleOptions(MIST_VIOLET, 1.8F), x, y, z, vx, vy, vz);
         } else {
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, x, y, z, vx * 0.5, vy * 0.5, vz * 0.5);
         }
      }

      if (this.random.nextFloat() < amount * 0.6F) {
         this.level().addParticle(ParticleTypes.REVERSE_PORTAL, this.getRandomX(0.8), this.getRandomY(), this.getRandomZ(0.8), 0.0, 0.05, 0.0);
      }
   }

   @Override
   public void vanish() {
      if (!this.level().isClientSide && !this.fading()) {
         if (!this.isAlive()) {
            super.vanish();
         } else {
            this.ejectPassengers();
            this.entityData.set(FADE_OUT, 35);
            this.sound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.8F, 0.5F);
            this.sound(SoundEvents.ENDERMAN_TELEPORT, 1.0F, 0.4F);
            this.act("hurt");
         }
      }
   }

   @Override
   public void bind(ServerPlayer owner) {
      super.bind(owner);
      CompoundTag t = owner.getPersistentData().getCompound("csm_dragon_saddles");
      this.entityData.set(SADDLED, t.getBoolean(this.kind().name()));
      this.sound(SoundEvents.SCULK_SHRIEKER_SHRIEK, 1.0F, 0.6F);
   }

   protected void positionRider(Entity passenger, MoveFunction move) {
      if (this.hasPassenger(passenger)) {
         if (this.level().isClientSide && this.clientSeat != null && this.level().getGameTime() - this.clientSeatTime < 4L) {
            move.accept(passenger, this.getX() + this.clientSeat.x, this.getY() + this.clientSeat.y, this.getZ() + this.clientSeat.z);
         } else {
            float yaw = this.getYRot() * (float) (Math.PI / 180.0);
            double f = this.seatForward();
            move.accept(
               passenger,
               this.getX() - (double)Mth.sin(yaw) * f,
               this.getY() + this.seatHeight() + passenger.getMyRidingOffset(),
               this.getZ() + (double)Mth.cos(yaw) * f
            );
         }
      }
   }

   public double getPassengersRidingOffset() {
      return this.seatHeight();
   }

   private void setSitting(boolean sit) {
      this.entityData.set(SITTING, sit);
      this.setNoGravity(this.flyer() && !sit);
   }

   private void chomp() {
      this.jawBite(this.chompAnim(), this.chompSound(), 6, 6.0F, 0.3, false, null);
   }

   @Override
   public boolean rideable() {
      return this.isSaddled() && !this.fading();
   }

   @Override
   protected InteractionResult mobInteract(Player player, InteractionHand hand) {
      ItemStack held = player.getItemInHand(hand);
      boolean owner = player.getId() == this.ownerId();
      if (this.fading()) {
         return InteractionResult.PASS;
      } else if (owner && player.isShiftKeyDown() && !held.is(Items.SADDLE)) {
         if (!this.level().isClientSide) {
            this.setSitting(!this.isSitting());
            player.displayClientMessage(
               Component.literal(this.kind().title + (this.isSitting() ? " sits and waits." : " is ready.")).withStyle(ChatFormatting.GRAY), true
            );
            this.act(this.chompAnim());
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else if (!this.isSaddled() && held.is(Items.SADDLE)) {
         if (!this.level().isClientSide) {
            this.entityData.set(SADDLED, true);
            if (!player.getAbilities().instabuild) {
               held.shrink(1);
            }

            if (owner) {
               CompoundTag t = player.getPersistentData().getCompound("csm_dragon_saddles");
               t.putBoolean(this.kind().name(), true);
               player.getPersistentData().put("csm_dragon_saddles", t);
            }

            this.playSound(SoundEvents.HORSE_SADDLE, 1.2F, 0.8F);
            player.displayClientMessage(Component.literal(this.kind().title + " is saddled. Right-click to ride.").withStyle(ChatFormatting.GREEN), true);
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else if (!this.isSaddled()) {
         if (!this.level().isClientSide) {
            this.chomp();
            if (owner && this.tickCount % 3 == 0) {
               player.displayClientMessage(Component.literal("Put a saddle on " + this.kind().title + " to ride it.").withStyle(ChatFormatting.GRAY), true);
            }
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else if (!player.isShiftKeyDown() && this.canAddPassenger(player)) {
         if (!this.level().isClientSide && (owner || this.maxRiders() > 1)) {
            if (this.isSitting()) {
               this.setSitting(false);
            }

            player.startRiding(this);
            this.chomp();
         }

         return InteractionResult.sidedSuccess(this.level().isClientSide);
      } else {
         return super.mobInteract(player, hand);
      }
   }

   protected abstract RawAnimation movement(AnimationState<DragonEntity> var1);

   protected abstract String animPrefix();

   protected abstract String[] actions();

   protected void act(String name) {
      this.triggerAnim("actions", name);
      if (!name.startsWith("grumble") && !name.equals("hurt") && !name.equals("flex")) {
         this.flourish(this.mouth(), 14, 0.6);
      }
   }

   protected ParticleOptions accent() {
      return ParticleTypes.SOUL_FIRE_FLAME;
   }

   protected ParticleOptions accent2() {
      return ParticleTypes.END_ROD;
   }

   protected void flourish(Vec3 at, int n, double r) {
      if (this.level() instanceof ServerLevel sl) {
         for (int i = 0; i < n; i++) {
            double a = (double)i * Math.PI * 2.0 / (double)n;
            sl.sendParticles(
               this.accent(),
               at.x + Math.cos(a) * r,
               at.y + Math.sin(a * 2.0) * 0.3,
               at.z + Math.sin(a) * r,
               0,
               Math.cos(a) * 0.4,
               0.15,
               Math.sin(a) * 0.4,
               0.12
            );
         }

         sl.sendParticles(this.accent2(), at.x, at.y, at.z, n / 2, 0.4, 0.4, 0.4, 0.06);
      }
   }

   protected void crescent(Vec3 at) {
      if (this.level() instanceof ServerLevel sl) {
         Vec3 f = this.forward();
         Vec3 s = new Vec3(-f.z, 0.0, f.x);

         for (int i = 0; i <= 16; i++) {
            double k = ((double)i / 16.0 - 0.5) * Math.PI;
            Vec3 p = at.add(s.scale(Math.sin(k) * 1.4)).add(0.0, Math.cos(k) * 1.0 - 0.3, 0.0).add(f.scale(Math.cos(k) * 0.4));
            sl.sendParticles(this.accent(), p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.01);
         }

         sl.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
      }
   }

   protected double speed() {
      return this.position().subtract(this.xo, this.yo, this.zo).length();
   }

   protected boolean airborne() {
      for (int i = 1; i <= 2; i++) {
         if (!this.level().getBlockState(this.blockPosition().below(i)).isAir()) {
            return false;
         }
      }

      return true;
   }

   public void registerControllers(ControllerRegistrar controllers) {
      RawAnimation sit = RawAnimation.begin().thenLoop(this.animPrefix() + this.sitAnim());
      controllers.add(new AnimationController[]{new AnimationController(this, "movement", 4, state -> {
         if (this.isDeadOrDying()) {
            return state.setAndContinue(RawAnimation.begin().thenPlayAndHold(this.animPrefix() + "die"));
         } else {
            return this.isSitting() && !this.airborne() ? state.setAndContinue(sit) : state.setAndContinue(this.movement(state));
         }
      })});
      AnimationController<DragonEntity> actions = new AnimationController(this, "actions", 2, state -> PlayState.STOP);

      for (String n : this.actions()) {
         actions.triggerableAnim(n, RawAnimation.begin().thenPlay(this.animPrefix() + n));
      }

      controllers.add(new AnimationController[]{actions});
   }

   public AnimatableInstanceCache getAnimatableInstanceCache() {
      return this.cache;
   }

   @Override
   public boolean hurt(DamageSource src, float amount) {
      if (this.fading()) {
         return false;
      } else {
         boolean hit = super.hurt(src, amount);
         if (hit && !this.level().isClientSide && this.hurtTime == this.hurtDuration && !this.busy()) {
            this.act("hurt");
         }

         return hit;
      }
   }

   protected String sitAnim() {
      return "sit";
   }

   protected boolean busy() {
      return false;
   }

   protected void ring(ParticleOptions p, Vec3 at, double r, int n, double speed) {
      if (this.level() instanceof ServerLevel sl) {
         for (int i = 0; i < n; i++) {
            double a = (double)i * Math.PI * 2.0 / (double)n;
            sl.sendParticles(p, at.x + Math.cos(a) * r, at.y, at.z + Math.sin(a) * r, 0, Math.cos(a), 0.05, Math.sin(a), speed);
         }
      }
   }

   protected void line(ParticleOptions p, Vec3 a, Vec3 b, double step) {
      if (this.level() instanceof ServerLevel sl) {
         Vec3 d = b.subtract(a);
         int n = Math.max(1, (int)(d.length() / step));

         for (int i = 0; i <= n; i++) {
            Vec3 q = a.add(d.scale((double)i / (double)n));
            sl.sendParticles(p, q.x, q.y, q.z, 1, 0.05, 0.05, 0.05, 0.0);
         }
      }
   }
}
