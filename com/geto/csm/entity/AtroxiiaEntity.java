package com.geto.csm.entity;

import com.geto.csm.CsmSounds;
import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;

public class AtroxiiaEntity extends DragonEntity {
   private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.atroxiia.idle");
   private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.atroxiia.walk");
   private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.atroxiia.run");
   private int busy;
   private AtroxiiaEntity.Move move;
   private int moveTick;
   private int sweepCd = 120;
   private int strikeCd = 80;
   private int quakeCd = 200;
   private int biteCd = 40;
   private boolean rightSide;
   private int nudgeTicks;
   private Vec3 nudgeVel = Vec3.ZERO;
   private static final RawAnimation IDLE_A = RawAnimation.begin().thenLoop("animation.atroxiia.idle");

   public AtroxiiaEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setMaxUpStep(1.5F);
   }

   @Override
   public CurseKind kind() {
      return CurseKind.ATROXIIA;
   }

   @Override
   protected float damageTaken() {
      return 1.0F;
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         if (this.nudgeTicks > 0) {
            this.nudgeTicks--;
            this.setDeltaMovement(this.nudgeVel.x, this.getDeltaMovement().y, this.nudgeVel.z);
            this.hurtMarked = true;
         }

         if (this.sweepCd > 0) {
            this.sweepCd--;
         }

         if (this.strikeCd > 0) {
            this.strikeCd--;
         }

         if (this.quakeCd > 0) {
            this.quakeCd--;
         }

         if (this.biteCd > 0) {
            this.biteCd--;
         }

         if (this.getControllingPassenger() != null) {
            this.tickMove();
         }
      }
   }

   private boolean tickMove() {
      if (this.busy <= 0) {
         return false;
      } else {
         this.busy--;
         this.getNavigation().stop();
         if (this.move != null) {
            this.move.tick(++this.moveTick);
         }

         if (this.busy == 0) {
            this.move = null;
         }

         return true;
      }
   }

   @Override
   protected void serverAi(ServerPlayer o) {
      if (!this.tickMove()) {
         LivingEntity t = this.findTarget(o, 40.0);
         if (t == null) {
            double d = (double)this.distanceTo(o);
            if (d > 9.0) {
               this.getNavigation().moveTo(o, d > 20.0 ? 1.45 : 1.0);
            } else {
               this.getNavigation().stop();
            }

            if (d > 48.0) {
               this.teleportTo(o.getX() + 3.0, o.getY(), o.getZ() + 3.0);
            }

            if (this.tickCount % 300 == 120) {
               this.grumble();
            }
         } else {
            this.faceToward(t.position());
            this.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double dx = (double)(this.distanceTo(t) - t.getBbWidth() / 2.0F);
            int crowd = this.enemiesAround(this.position(), 4.0).size();
            double jaw = this.mouthDist(t);
            if ((dx < 2.5 || crowd >= 3) && this.sweepCd <= 0) {
               this.devastatingSweep();
            } else if (dx < 18.0 && this.quakeCd <= 0 && this.signatureCd <= 0) {
               this.helheimQuake();
            } else if (dx >= 4.0 && dx <= 10.0 && this.specialCd <= 0 && this.facing(t) > 0.82) {
               this.gungnirStab();
            } else if (dx >= 3.0 && dx <= 6.5 && this.strikeCd <= 0) {
               this.preciseStrike();
            } else if (jaw < this.biteReach() && this.biteCd <= 0 && this.random.nextInt(3) == 0) {
               this.bite();
            } else if (dx < 3.5 && this.attackCd <= 0) {
               this.slam();
            } else if (dx < 6.0 && this.attackCd <= 0) {
               this.swipe();
            } else if (dx > 5.0) {
               this.getNavigation().moveTo(t, 1.45);
            } else {
               this.getNavigation().stop();
            }
         }
      }
   }

   private void begin(int ticks, AtroxiiaEntity.Move m) {
      this.busy = ticks;
      this.moveTick = 0;
      this.move = m;
      this.getNavigation().stop();
   }

   private double facing(LivingEntity t) {
      Vec3 to = t.position().subtract(this.position()).multiply(1.0, 0.0, 1.0);
      return to.lengthSqr() < 1.0E-4 ? 1.0 : to.normalize().dot(this.forward());
   }

   private List<LivingEntity> cone(double range, double angle) {
      Vec3 f = this.forward();
      Vec3 c = this.position();
      double cos = Math.cos(Math.toRadians(angle / 2.0));
      List<LivingEntity> out = new ArrayList<>();

      for (LivingEntity e : this.enemiesAround(c.add(f.scale(range * 0.5)), range + 2.0)) {
         Vec3 to = e.position().subtract(c).multiply(1.0, 0.0, 1.0);
         double dist = to.length() - (double)e.getBbWidth() / 2.0;
         if (dist <= range + (double)this.getBbWidth() * 0.5
            && (to.lengthSqr() < 1.0 || to.normalize().dot(f) >= cos)
            && Math.abs(e.getY() - this.getY()) < 5.0) {
            out.add(e);
         }
      }

      return out;
   }

   private void nudge(int ticks, double distance) {
      this.nudgeTicks = ticks;
      this.nudgeVel = this.forward().scale(distance / (double)ticks);
   }

   private void push(LivingEntity e, Vec3 dir, double strength, double lift) {
      Vec3 h = new Vec3(dir.x, 0.0, dir.z);
      if (h.lengthSqr() < 1.0E-6) {
         h = this.forward();
      }

      h = h.normalize().scale(strength);
      launch(e, new Vec3(h.x, lift, h.z));
   }

   private void frost(LivingEntity e, int stunTicks, boolean burst) {
      int dur = Math.max(1, stunTicks);
      e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, dur, 6, false, true));
      e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, dur, 1, false, true));
      if (e.canFreeze()) {
         e.setTicksFrozen(Math.max(e.getTicksFrozen(), e.getTicksRequiredToFreeze() + dur * 2));
      }

      if (burst && this.level() instanceof ServerLevel sl) {
         for (int i = 0; i < 28; i++) {
            double a = this.random.nextDouble() * Math.PI * 2.0;
            Vec3 dir = new Vec3(Math.cos(a), this.random.nextDouble() * 0.8, Math.sin(a)).normalize();
            sl.sendParticles(
               ParticleTypes.SNOWFLAKE,
               e.getX() + dir.x * 0.4,
               e.getY() + (double)e.getBbHeight() * (0.25 + this.random.nextDouble() * 0.5),
               e.getZ() + dir.z * 0.4,
               0,
               dir.x,
               dir.y,
               dir.z,
               0.12 + this.random.nextDouble() * 0.28
            );
         }
      }
   }

   private void bite() {
      this.biteCd = 36;
      this.attackCd = Math.max(this.attackCd, 14);
      this.begin(16, t -> {
      });
      this.jawBite("underwater_bite", (SoundEvent)CsmSounds.ATROXIIA_BITE.get(), 7, 14.0F, 0.6, true, e -> this.frost(e, 40, true));
   }

   private void swipe() {
      this.attackCd = 24;
      this.rightSide = !this.rightSide;
      this.act(this.rightSide ? "swipe_right" : "swipe_left");
      this.sound((SoundEvent)CsmSounds.ATROXIIA_SWIPE.get(), 1.4F, 1.0F);
      this.begin(20, t -> {
         if (t == 13) {
            for (LivingEntity e : this.cone(6.0, 115.0)) {
               this.strike(e, 12.0F);
               this.push(e, this.forward(), 0.3, 0.1);
            }
         }
      });
   }

   private void slam() {
      this.attackCd = 32;
      this.rightSide = !this.rightSide;
      this.act(this.rightSide ? "slam_right" : "slam_left");
      this.sound((SoundEvent)CsmSounds.ATROXIIA_SLAM.get(), 1.5F, 1.0F);
      this.begin(25, t -> {
         if (t == 10) {
            Vec3 at = this.position().add(this.forward().scale(3.0));
            CsmNetwork.fx(this, FxType.BITE, this, (Entity)null, at.add(0.0, 0.3, 0.0), this.getYRot(), 1.0F);

            for (LivingEntity e : this.cone(5.5, 100.0)) {
               this.strike(e, 16.0F);
               this.push(e, this.forward(), 0.45, 0.18);
            }
         }
      });
   }

   private void devastatingSweep() {
      this.sweepCd = 240;
      this.attackCd = 26;
      this.act("devastating_sweep");
      this.sound((SoundEvent)CsmSounds.ATROXIIA_DEVASTATING_SWEEP.get(), 2.0F, 1.0F);
      this.begin(26, t -> {
         if (t == 10) {
            CsmNetwork.fx(this, FxType.TAIL_SWEEP, this, (Entity)null, this.position(), this.getYRot(), 12.0F);

            for (LivingEntity e : this.enemiesAround(this.position(), 12.0)) {
               if (!(Math.abs(e.getY() - this.getY()) > 6.0)) {
                  this.strike(e, 13.0F);
                  this.push(e, e.position().subtract(this.position()), 1.65, 0.32);
               }
            }
         }
      });
   }

   private void preciseStrike() {
      this.strikeCd = 360;
      this.act("precise_strike");
      this.sound((SoundEvent)CsmSounds.ATROXIIA_PRECISE_STRIKE.get(), 1.5F, 1.0F);
      this.begin(108, t -> {
         LivingEntity tgt = this.getTarget();
         if (tgt != null && tgt.isAlive() && t < 100) {
            Vec3 d = tgt.position().subtract(this.position());
            float want = (float)(Mth.atan2(d.z, d.x) * (180.0 / Math.PI)) - 90.0F;
            float yaw = Mth.approachDegrees(this.getYRot(), want, 10.0F);
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
         }

         switch (t) {
            case 11:
               this.nudge(5, 5.0);
               this.comboHit(t);
               break;
            case 25:
               Vec3 pull = this.position().add(0.0, (double)this.getBbHeight() * 0.45, 0.0);

               for (LivingEntity e : this.cone(8.5, 100.0)) {
                  Vec3 h = pull.subtract(e.position()).multiply(1.0, 0.0, 1.0);
                  if (h.lengthSqr() > 1.0E-6) {
                     launch(e, h.normalize().add(0.0, 0.12, 0.0));
                  }
               }
               break;
            case 39:
            case 53:
               this.nudge(5, 5.0);
               break;
            case 45:
               this.comboHit(t);
               break;
            case 64:
               CsmNetwork.fx(this, FxType.QUAKE_WAVE, this, (Entity)null, this.position().add(this.forward().scale(4.0)), 7.0F, 0.0F);
               this.comboHit(t);
         }
      });
   }

   private void comboHit(int t) {
      for (LivingEntity e : this.cone(8.5, 100.0)) {
         this.strike(e, 10.0F);
         this.frost(e, Math.max(1, 108 - t + 40), true);
         this.push(e, this.forward(), 0.75, 0.16);
      }

      CsmNetwork.fx(this, FxType.BITE, this, (Entity)null, this.position().add(this.forward().scale(4.5)).add(0.0, 1.0, 0.0), this.getYRot(), 1.0F);
   }

   private void gungnirStab() {
      this.specialCd = 400;
      float yaw = this.getYRot();
      this.act("gungnir_stab");
      this.sound((SoundEvent)CsmSounds.ATROXIIA_GUNGNIR_STAB.get(), 2.0F, 1.0F);
      this.begin(37, t -> {
         this.setYRot(yaw);
         this.yBodyRot = yaw;
         this.yHeadRot = yaw;
         if (t == 12) {
            this.nudge(4, 2.0);
         } else if (t == 17) {
            this.nudge(7, 8.0);
            Vec3 f = this.forward();
            Vec3 a = this.position().add(0.0, 1.0, 0.0);

            for (LivingEntity e : this.enemiesAround(a.add(f.scale(10.0)), 12.0)) {
               Vec3 to = e.getBoundingBox().getCenter().subtract(a);
               double along = to.dot(f);
               if (!(along < -1.0) && !(along > 20.0)) {
                  Vec3 off = to.subtract(f.scale(along));
                  if (Math.hypot(off.x, off.z) <= 3.0 + (double)e.getBbWidth() / 2.0 && Math.abs(off.y) <= 4.0) {
                     this.strike(e, 40.0F);
                     this.frost(e, 37 - t + 40, true);
                  }
               }
            }

            CsmNetwork.fx(this, FxType.ROAR_BEAM, this, (Entity)null, a.add(f.scale(2.0)), yaw, 0.0F);
         }
      });
   }

   private void helheimQuake() {
      this.quakeCd = 600;
      this.signatureCd = 240;
      this.act("helheim_quake1");
      this.sound((SoundEvent)CsmSounds.ATROXIIA_HELHEIM_QUAKE1.get(), 2.5F, 1.0F);
      this.begin(39, t -> {
         if (t == 18) {
            this.freezeWater(12);
            this.quake(true);
         } else if (t == 22) {
            this.act("helheim_quake2");
            this.sound((SoundEvent)CsmSounds.ATROXIIA_HELHEIM_QUAKE2.get(), 2.5F, 1.0F);
         } else if (t == 36) {
            this.quake(false);
         }
      });
   }

   private void quake(boolean first) {
      Vec3 at = new Vec3(this.getX(), this.getBoundingBox().minY + 0.12, this.getZ());
      CsmNetwork.fx(this, FxType.QUAKE_WAVE, this, (Entity)null, at, first ? 14.0F : 20.0F, 0.0F);
      if (this.level() instanceof ServerLevel sl) {
         for (int i = 0; i < 90; i++) {
            double a = (double)i * Math.PI * 2.0 / 90.0;
            double r = 2.0 + this.random.nextDouble() * 3.0;
            sl.sendParticles(ParticleTypes.SNOWFLAKE, at.x + Math.cos(a) * r, at.y + 0.2, at.z + Math.sin(a) * r, 0, Math.cos(a), 0.15, Math.sin(a), 0.6);
            if (i % 3 == 0) {
               sl.sendParticles(ParticleTypes.CLOUD, at.x + Math.cos(a) * r, at.y + 0.3, at.z + Math.sin(a) * r, 0, Math.cos(a), 0.05, Math.sin(a), 0.35);
            }
         }

         sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
      }

      this.sound(SoundEvents.GENERIC_EXPLODE, 2.0F, first ? 0.6F : 0.45F);

      for (LivingEntity e : this.enemiesAround(this.position(), 20.0)) {
         if (!(Math.abs(e.getY() - this.getY()) > 6.0)) {
            this.strike(e, 25.0F);
            this.frost(e, 100, false);
            this.push(e, e.position().subtract(this.position()), first ? 0.75 : 1.8, first ? 0.2 : 0.5);
         }
      }
   }

   private void freezeWater(int radius) {
      Level lvl = this.level();
      MutableBlockPos p = new MutableBlockPos();
      int cx = Mth.floor(this.getX());
      int cz = Mth.floor(this.getZ());
      int top = Mth.floor(this.getBoundingBox().minY);

      for (int dx = -radius; dx <= radius; dx++) {
         for (int dz = -radius; dz <= radius; dz++) {
            if (dx * dx + dz * dz <= radius * radius) {
               for (int dy = 0; dy <= 10; dy++) {
                  p.set(cx + dx, top - dy, cz + dz);
                  BlockState s = lvl.getBlockState(p);
                  if (s.is(Blocks.WATER) && s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource() && lvl.getBlockState(p.above()).isAir()) {
                     lvl.setBlockAndUpdate(p, Blocks.FROSTED_ICE.defaultBlockState());
                     break;
                  }
               }
            }
         }
      }
   }

   private void grumble() {
      int n = this.random.nextInt(3);
      this.act("grumble" + (n + 1));
      this.sound((SoundEvent)(n == 0 ? CsmSounds.ATROXIIA_GRUMBLE1 : (n == 1 ? CsmSounds.ATROXIIA_GRUMBLE2 : CsmSounds.ATROXIIA_GRUMBLE3)).get(), 1.5F, 1.0F);
   }

   @Override
   public void bind(ServerPlayer owner) {
      super.bind(owner);
      this.act("flex");
      this.sound((SoundEvent)CsmSounds.ATROXIIA_FLEX.get(), 2.0F, 1.0F);
      this.begin(30, t -> {
      });
   }

   protected SoundEvent getHurtSound(DamageSource src) {
      return (SoundEvent)CsmSounds.ATROXIIA_HURT.get();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)CsmSounds.ATROXIIA_DIE.get();
   }

   protected void playStepSound(BlockPos pos, BlockState state) {
      this.playSound((SoundEvent)CsmSounds.ATROXIIA_STEP.get(), 0.8F, 1.0F);
   }

   @Override
   protected boolean busy() {
      return this.busy > 0;
   }

   @Override
   protected double rideSpeed() {
      return 0.55;
   }

   @Override
   public void travel(Vec3 input) {
      if (this.isVehicle() && this.getControllingPassenger() instanceof Player p) {
         this.setYRot(p.getYRot());
         this.yRotO = this.getYRot();
         this.yBodyRot = this.getYRot();
         this.yHeadRot = this.getYRot();
         this.setSpeed((float)this.rideSpeed() * (p.zza < 0.0F ? 0.4F : 1.0F));
         this.travelGround(new Vec3((double)(p.xxa * 0.5F), 0.0, (double)p.zza));
      } else {
         super.travel(input);
      }
   }

   @Override
   public String[] riderMoves() {
      return new String[]{"Bite", "Claw Swipe", "Slam", "Devastating Sweep", "Precise Strike", "Gungnir Stab", "Helheim Quake"};
   }

   @Override
   protected void doRiderMove(ServerPlayer p, int slot) {
      if (this.busy <= 0) {
         switch (slot) {
            case 0:
               if (this.ready(this.biteCd, p, "Bite")) {
                  this.bite();
               }
               break;
            case 1:
               if (this.ready(this.attackCd, p, "Claw Swipe")) {
                  this.swipe();
               }
               break;
            case 2:
               if (this.ready(this.attackCd, p, "Slam")) {
                  this.slam();
               }
               break;
            case 3:
               if (this.ready(this.sweepCd, p, "Devastating Sweep")) {
                  this.devastatingSweep();
               }
               break;
            case 4:
               if (this.ready(this.strikeCd, p, "Precise Strike")) {
                  LivingEntity t = this.riderTarget(p, 12.0);
                  if (t != null) {
                     this.setTarget(t);
                  }

                  this.preciseStrike();
               }
               break;
            case 5:
               if (this.ready(this.specialCd, p, "Gungnir Stab")) {
                  this.gungnirStab();
               }
               break;
            case 6:
               if (this.ready(Math.max(this.quakeCd, this.signatureCd), p, "Helheim Quake")) {
                  this.helheimQuake();
               }
         }
      }
   }

   @Override
   protected RawAnimation movement(AnimationState<DragonEntity> state) {
      double v = this.speed();
      return v > 0.2 ? RUN : (v > 0.025 ? WALK : IDLE_A);
   }

   @Override
   protected String animPrefix() {
      return "animation.atroxiia.";
   }

   @Override
   protected String[] actions() {
      return new String[]{
         "swipe_right",
         "swipe_left",
         "slam_right",
         "slam_left",
         "devastating_sweep",
         "precise_strike",
         "gungnir_stab",
         "helheim_quake1",
         "helheim_quake2",
         "hurt",
         "flex",
         "grumble1",
         "grumble2",
         "grumble3",
         "taunt",
         "underwater_bite"
      };
   }

   @Override
   protected double seatHeight() {
      return 4.53;
   }

   @Override
   protected double seatForward() {
      return 0.8;
   }

   @Override
   protected double mouthForward() {
      return 3.3;
   }

   @Override
   protected double mouthLow() {
      return 1.6;
   }

   @Override
   protected double mouthHigh() {
      return 2.6;
   }

   @Override
   protected double biteReach() {
      return 2.0;
   }

   @Override
   protected boolean flyer() {
      return false;
   }

   @Override
   protected String chompAnim() {
      return "underwater_bite";
   }

   @Override
   protected SoundEvent chompSound() {
      return (SoundEvent)CsmSounds.ATROXIIA_BITE.get();
   }

   @Override
   protected ParticleOptions accent() {
      return ParticleTypes.SNOWFLAKE;
   }

   @Override
   protected ParticleOptions accent2() {
      return ParticleTypes.END_ROD;
   }

   private interface Move {
      void tick(int var1);
   }
}
