package com.geto.csm.entity;

import com.geto.csm.CsmSounds;
import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;

public class RaevyxEntity extends DragonEntity {
   private static final RawAnimation FLY_IDLE = RawAnimation.begin().thenLoop("animation.raevyx.fly_idle");
   private static final RawAnimation FLAP = RawAnimation.begin().thenLoop("animation.raevyx.flap");
   private static final RawAnimation SPRINT = RawAnimation.begin().thenLoop("animation.raevyx.sprint_flap");
   private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.raevyx.idle");
   private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.raevyx.walk");
   private int beamCd = 120;
   private int stormCd = 300;
   private int goreCd = 80;
   private int beamTicks;
   private Vec3 beamDir = Vec3.ZERO;

   public RaevyxEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
   }

   @Override
   public CurseKind kind() {
      return CurseKind.RAEVYX;
   }

   @Override
   protected float damageTaken() {
      return 1.0F;
   }

   @Override
   protected double rideSpeed() {
      return 1.4;
   }

   @Override
   protected boolean busy() {
      return this.beamTicks > 0;
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         if (this.beamCd > 0) {
            this.beamCd--;
         }

         if (this.stormCd > 0) {
            this.stormCd--;
         }

         if (this.goreCd > 0) {
            this.goreCd--;
         }

         if (this.beamTicks > 0 && this.getControllingPassenger() != null) {
            this.tickBeam();
         }
      }
   }

   @Override
   protected void serverAi(ServerPlayer o) {
      if (this.beamTicks > 0) {
         this.tickBeam();
      } else {
         LivingEntity t = this.findTarget(o, 40.0);
         if (t == null) {
            this.followOwner(o, 8.0, 0.75, true);
         } else {
            double d = (double)this.distanceTo(t);
            double jaw = this.mouthDist(t);
            if (this.attackCd <= 0 && d < 16.0) {
               this.flyToward(this.biteApproach(t), 0.9);
            } else if (d > 9.0) {
               this.flyToward(t.position().add(0.0, 4.0, 0.0), 0.9);
            } else {
               this.setDeltaMovement(this.getDeltaMovement().scale(0.7));
            }

            this.faceToward(t.position());
            if (d < 30.0 && this.stormCd <= 0) {
               this.summonStorm(t.position());
            } else if (d > 8.0 && d < 24.0 && this.beamCd <= 0) {
               this.startBeam(t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0).subtract(this.mouth()));
            } else if (d > 6.0 && d < 14.0 && this.goreCd <= 0) {
               this.hornGore(t.position().add(0.0, 1.0, 0.0).subtract(this.position()));
            } else if (jaw < this.biteReach() && this.attackCd <= 0) {
               this.lightningBite();
            }
         }
      }
   }

   private void lightningBite() {
      this.attackCd = 26;
      boolean[] chained = new boolean[]{false};
      this.jawBite("lightning_bite", (SoundEvent)CsmSounds.RAEVYX_BITE.get(), 7, 15.0F, 0.8, true, e -> {
         if (!chained[0]) {
            chained[0] = true;
            this.chain(e, 10.0F, 5, 8.0);
         }
      });
   }

   private void chain(LivingEntity first, float dmg, int jumps, double radius) {
      List<LivingEntity> hit = new ArrayList<>();
      hit.add(first);
      LivingEntity from = first;
      float d = dmg;

      for (int i = 0; i < jumps; i++) {
         LivingEntity next = null;
         double best = radius * radius;

         for (LivingEntity e : this.enemiesAround(from.position(), radius)) {
            if (!hit.contains(e) && e.distanceToSqr(from) < best) {
               best = e.distanceToSqr(from);
               next = e;
            }
         }

         if (next == null) {
            break;
         }

         this.line(
            ParticleTypes.ELECTRIC_SPARK,
            from.position().add(0.0, (double)from.getBbHeight() * 0.6, 0.0),
            next.position().add(0.0, (double)next.getBbHeight() * 0.6, 0.0),
            0.35
         );
         this.strike(next, d);
         d *= 0.75F;
         hit.add(next);
         from = next;
      }

      this.sound(SoundEvents.LIGHTNING_BOLT_IMPACT, 1.2F, 1.6F);
   }

   private void hornGore(Vec3 dir) {
      this.goreCd = 140;
      this.attackCd = 20;
      this.act("horn_gore");
      this.sound((SoundEvent)CsmSounds.RAEVYX_HORNGORE.get(), 1.8F, 1.0F);
      Vec3 dash = dir.normalize().scale(1.6);

      for (int i = 0; i < 6; i++) {
         Scheduler.after(2 + i, () -> {
            if (this.isAlive()) {
               this.setDeltaMovement(dash);
               this.hurtMarked = true;
            }
         });
      }

      for (int k = 4; k <= 9; k++) {
         Scheduler.after(k, () -> {
            if (this.isAlive()) {
               for (LivingEntity e : this.inJaws(1.0)) {
                  if (e.invulnerableTime <= 10) {
                     this.strike(e, 15.0F);
                     launch(e, dash.normalize().scale(1.3).add(0.0, 0.9, 0.0));
                     CsmNetwork.fx(this, FxType.BITE, this, e, e.position().add(0.0, 1.0, 0.0), this.getYRot(), 1.0F);
                  }
               }
            }
         });
      }
   }

   public void startBeam(Vec3 dir) {
      this.beamCd = 260;
      this.beamTicks = 40;
      this.beamDir = dir.normalize();
      this.act("lightning_beam_start");
      this.sound((SoundEvent)CsmSounds.RAEVYX_BEAM_START.get(), 2.0F, 1.0F);
   }

   private void tickBeam() {
      this.beamTicks--;
      this.setDeltaMovement(this.getDeltaMovement().scale(0.5));
      LivingEntity t = this.getTarget();
      if (t != null && t.isAlive() && this.getControllingPassenger() == null) {
         Vec3 want = t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0).subtract(this.mouth()).normalize();
         this.beamDir = this.beamDir.lerp(want, 0.08).normalize();
      } else if (this.getControllingPassenger() instanceof Player p) {
         this.beamDir = p.getLookAngle();
      }

      this.faceToward(this.position().add(this.beamDir.scale(10.0)));
      Vec3 a = this.mouth();
      Vec3 b = a.add(this.beamDir.scale(24.0));
      if (this.beamTicks == 30) {
         this.act("lightning_beaming");
      }

      if (this.beamTicks % 4 == 0) {
         this.line(ParticleTypes.ELECTRIC_SPARK, a, b, 0.5);
         this.line(ParticleTypes.END_ROD, a, b, 1.5);

         for (LivingEntity e : this.enemiesAround(a.add(this.beamDir.scale(12.0)), 13.0)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(a);
            double along = to.dot(this.beamDir);
            if (!this.hasPassenger(e) && along > 0.0 && along < 24.0 && to.subtract(this.beamDir.scale(along)).length() < 0.75 + (double)e.getBbWidth()) {
               this.strike(e, 5.0F);
            }
         }
      }

      if (this.beamTicks % 20 == 0) {
         this.sound((SoundEvent)CsmSounds.RAEVYX_BEAMING.get(), 1.8F, 1.0F);
      }

      if (this.beamTicks == 0) {
         this.act("lightning_beam_stop");
         this.sound((SoundEvent)CsmSounds.RAEVYX_BEAM_STOP.get(), 1.8F, 1.0F);
      }
   }

   private void summonStorm(Vec3 where) {
      this.stormCd = 600;
      this.attackCd = 40;
      this.act(this.airborne() ? "summon_storm_air" : "summon_storm");
      this.sound((SoundEvent)CsmSounds.RAEVYX_SUMMON_STORM.get(), 2.5F, 1.0F);

      for (int i = 0; i < 8; i++) {
         Scheduler.after(20 + i * 7, () -> {
            if (this.isAlive() && this.level() instanceof ServerLevel sl) {
               Vec3 var9 = where.add(this.random.nextGaussian() * 3.0, 0.0, this.random.nextGaussian() * 3.0);
               double y = (double)sl.getHeight(Types.MOTION_BLOCKING, Mth.floor(var9.x), Mth.floor(var9.z));
               LightningBolt bolt = (LightningBolt)EntityType.LIGHTNING_BOLT.create(sl);
               if (bolt != null) {
                  bolt.moveTo(var9.x, y, var9.z);
                  bolt.setVisualOnly(true);
                  sl.addFreshEntity(bolt);
               }

               for (LivingEntity e : this.enemiesAround(new Vec3(var9.x, y, var9.z), 3.0)) {
                  if (!this.hasPassenger(e)) {
                     this.strike(e, 8.0F);
                     e.setSecondsOnFire(3);
                  }
               }
            }
         });
      }
   }

   @Override
   public String[] riderMoves() {
      return new String[]{"Lightning Bite", "Horn Gore", "Lightning Beam", "Summon Storm"};
   }

   @Override
   protected void doRiderMove(ServerPlayer p, int slot) {
      if (this.beamTicks <= 0) {
         switch (slot) {
            case 0:
               if (this.ready(this.attackCd, p, "Lightning Bite")) {
                  this.lightningBite();
               }
               break;
            case 1:
               if (this.ready(this.goreCd, p, "Horn Gore")) {
                  this.hornGore(p.getLookAngle());
               }
               break;
            case 2:
               if (this.ready(this.beamCd, p, "Lightning Beam")) {
                  this.startBeam(p.getLookAngle());
               }
               break;
            case 3:
               if (this.ready(this.stormCd, p, "Summon Storm")) {
                  LivingEntity t = this.riderTarget(p, 40.0);
                  this.summonStorm(t != null ? t.position() : this.riderAim(p, 40.0));
               }
         }
      }
   }

   @Override
   public boolean hurt(DamageSource src, float amount) {
      return src.is(DamageTypeTags.IS_LIGHTNING) ? false : super.hurt(src, amount);
   }

   protected SoundEvent getHurtSound(DamageSource src) {
      return (SoundEvent)CsmSounds.RAEVYX_HURT.get();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)CsmSounds.RAEVYX_DIE.get();
   }

   @Override
   protected RawAnimation movement(AnimationState<DragonEntity> state) {
      double v = this.speed();
      if (!this.airborne()) {
         return v > 0.03 ? WALK : IDLE;
      } else {
         return v > 0.6 ? SPRINT : (v > 0.15 ? FLAP : FLY_IDLE);
      }
   }

   @Override
   protected String animPrefix() {
      return "animation.raevyx.";
   }

   @Override
   protected String[] actions() {
      return new String[]{
         "lightning_bite",
         "horn_gore",
         "lightning_beam_start",
         "lightning_beaming",
         "lightning_beam_stop",
         "summon_storm",
         "summon_storm_air",
         "roar",
         "hurt",
         "grumble1",
         "grumble2",
         "grumble3",
         "flex"
      };
   }

   @Override
   protected double seatHeight() {
      return 4.12;
   }

   @Override
   protected double seatForward() {
      return 2.6;
   }

   @Override
   protected double mouthForward() {
      return 5.2;
   }

   @Override
   protected double mouthLow() {
      return 0.8;
   }

   @Override
   protected double mouthHigh() {
      return 5.4;
   }

   @Override
   protected double biteReach() {
      return 2.4;
   }

   @Override
   protected boolean flyer() {
      return true;
   }

   @Override
   protected String chompAnim() {
      return "lightning_bite";
   }

   @Override
   protected SoundEvent chompSound() {
      return (SoundEvent)CsmSounds.RAEVYX_BITE.get();
   }

   @Override
   protected ParticleOptions accent() {
      return ParticleTypes.ELECTRIC_SPARK;
   }

   @Override
   protected ParticleOptions accent2() {
      return ParticleTypes.END_ROD;
   }
}
