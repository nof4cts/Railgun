package com.geto.csm.entity;

import com.geto.csm.CsmSounds;
import com.geto.csm.CurseKind;
import com.geto.csm.server.Scheduler;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;

public class CindervaneEntity extends DragonEntity {
   private static final RawAnimation FLY_IDLE = RawAnimation.begin().thenLoop("animation.cindervane.fly_idle");
   private static final RawAnimation FLAP = RawAnimation.begin().thenLoop("animation.cindervane.flap");
   private static final RawAnimation SPRINT = RawAnimation.begin().thenLoop("animation.cindervane.sprint_flap");
   private static final RawAnimation GLIDE = RawAnimation.begin().thenLoop("animation.cindervane.glide");
   private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.cindervane.idle");
   private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.cindervane.walk");
   private int volleyCd = 80;
   private int doubleCd = 60;
   private int fighting;

   public CindervaneEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
   }

   @Override
   public CurseKind kind() {
      return CurseKind.CINDERVANE;
   }

   @Override
   protected float damageTaken() {
      return 1.0F;
   }

   @Override
   protected double rideSpeed() {
      return 1.55;
   }

   @Override
   protected void serverAi(ServerPlayer o) {
      LivingEntity t = this.findTarget(o, 40.0);
      if (t == null) {
         this.fighting = 0;
         this.followOwner(o, 6.0, 0.85, true);
      } else {
         this.fighting = 60;
         double d = (double)this.distanceTo(t);
         double jaw = this.mouthDist(t);
         double a = (double)this.tickCount * 0.06 + (double)this.getId();
         Vec3 orbit = t.position().add(Math.cos(a) * 7.0, 3.5, Math.sin(a) * 7.0);
         boolean diving = this.attackCd <= 0 || this.doubleCd <= 0;
         this.flyToward(diving && d < 14.0 ? this.biteApproach(t) : orbit, 1.1);
         this.faceToward(t.position());
         if (d > 7.0 && d < 26.0 && this.volleyCd <= 0) {
            this.magmaVolley(t.position().add(0.0, (double)t.getBbHeight() * 0.5, 0.0));
         } else if (jaw < this.biteReach() && this.doubleCd <= 0) {
            this.doubleBite();
         } else if (jaw < this.biteReach() && this.attackCd <= 0) {
            if (this.random.nextBoolean()) {
               this.bite();
            } else {
               this.slash();
            }
         }
      }
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide) {
         if (this.volleyCd > 0) {
            this.volleyCd--;
         }

         if (this.doubleCd > 0) {
            this.doubleCd--;
         }
      }

      if (this.level().isClientSide) {
         if (this.random.nextInt(3) == 0) {
            this.level().addParticle(ParticleTypes.FLAME, this.getRandomX(0.8), this.getRandomY(), this.getRandomZ(0.8), 0.0, 0.02, 0.0);
         }
      } else {
         if (this.fighting > 0) {
            this.fighting--;
            if (this.tickCount % 20 == 0) {
               for (LivingEntity e : this.enemiesAround(this.position().add(0.0, 1.0, 0.0), 3.5)) {
                  this.strike(e, 3.0F);
                  e.setSecondsOnFire(4);
               }

               this.ring(ParticleTypes.FLAME, this.position().add(0.0, 1.0, 0.0), 3.5, 28, 0.02);
            }
         }
      }
   }

   private void bite() {
      this.attackCd = 22;
      this.jawBite(this.airborne() ? "bite_air" : "bite", (SoundEvent)CsmSounds.CINDERVANE_BITE.get(), 6, 12.0F, 0.5, false, e -> e.setSecondsOnFire(3));
   }

   private void slash() {
      this.attackCd = 26;
      this.act("cindervane_slash_left");
      this.sound((SoundEvent)CsmSounds.CINDERVANE_SLASH.get(), 1.6F, 1.0F);
      Scheduler.after(8, () -> {
         if (this.isAlive()) {
            Vec3 at = this.position().add(this.forward().scale(this.mouthForward() * 0.6)).add(0.0, this.mouthUp() * 0.5, 0.0);

            for (LivingEntity e : this.enemiesAround(at, 3.5)) {
               if (!this.hasPassenger(e)) {
                  this.strike(e, 12.0F);
                  e.setSecondsOnFire(5);
                  this.ring(ParticleTypes.FLAME, e.position().add(0.0, 1.0, 0.0), 1.2, 16, 0.08);
               }
            }
         }
      });
   }

   private void doubleBite() {
      this.doubleCd = 120;
      this.attackCd = 30;
      this.jawBite(this.airborne() ? "double_bite_air" : "double_bite", (SoundEvent)CsmSounds.CINDERVANE_DOUBLE_BITE.get(), 5, 15.0F, 0.2, false, null);
      Scheduler.after(6, () -> {
         if (this.isAlive()) {
            this.jawBite(null, null, 5, 15.0F, 0.6, true, null);
         }
      });
   }

   public void magmaVolley(Vec3 at) {
      this.volleyCd = 220;
      this.act("magma_volley");
      this.sound((SoundEvent)CsmSounds.CINDERVANE_MAGMA_VOLLEY.get(), 2.0F, 1.0F);

      for (int v = 0; v < 3; v++) {
         Scheduler.after(
            4 + v * 10,
            () -> {
               if (this.isAlive() && this.level() instanceof ServerLevel sl) {
                  Vec3 var7 = this.mouth().add(this.forward().scale(0.8));

                  for (int i = 0; i < 3; i++) {
                     Vec3 aim = at.add(this.random.nextGaussian() * 1.6, this.random.nextGaussian() * 0.5, this.random.nextGaussian() * 1.6)
                        .subtract(var7)
                        .normalize();
                     SmallFireball fb = new SmallFireball(sl, this, aim.x, aim.y, aim.z);
                     fb.setPos(var7.x, var7.y, var7.z);
                     sl.addFreshEntity(fb);
                  }

                  sl.sendParticles(ParticleTypes.LAVA, var7.x, var7.y, var7.z, 6, 0.3, 0.3, 0.3, 0.0);
               }
            }
         );
      }
   }

   @Override
   public String[] riderMoves() {
      return new String[]{"Bite", "Double Bite", "Claw Slash", "Magma Volley"};
   }

   @Override
   protected void doRiderMove(ServerPlayer p, int slot) {
      switch (slot) {
         case 0:
            if (this.ready(this.attackCd, p, "Bite")) {
               this.bite();
            }
            break;
         case 1:
            if (this.ready(Math.max(this.doubleCd, this.attackCd), p, "Double Bite")) {
               this.doubleBite();
            }
            break;
         case 2:
            if (this.ready(this.attackCd, p, "Claw Slash")) {
               this.slash();
            }
            break;
         case 3:
            if (this.ready(this.volleyCd, p, "Magma Volley")) {
               LivingEntity t = this.riderTarget(p, 32.0);
               this.magmaVolley(t != null ? t.getBoundingBox().getCenter() : this.riderAim(p, 32.0));
            }
      }
   }

   @Override
   public boolean hurt(DamageSource src, float amount) {
      return src.is(DamageTypeTags.IS_FIRE) ? false : super.hurt(src, amount);
   }

   protected SoundEvent getHurtSound(DamageSource src) {
      return (SoundEvent)CsmSounds.CINDERVANE_HURT.get();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)CsmSounds.CINDERVANE_DIE.get();
   }

   @Override
   protected RawAnimation movement(AnimationState<DragonEntity> state) {
      double v = this.speed();
      if (!this.airborne()) {
         return v > 0.03 ? WALK : IDLE;
      } else {
         return v > 0.6 ? SPRINT : (v > 0.3 ? FLAP : (v > 0.08 ? GLIDE : FLY_IDLE));
      }
   }

   @Override
   protected String animPrefix() {
      return "animation.cindervane.";
   }

   @Override
   protected String[] actions() {
      return new String[]{
         "bite", "bite_air", "double_bite", "double_bite_air", "magma_volley", "cindervane_slash_left", "hurt", "grumble1", "grumble2", "grumble3", "flex"
      };
   }

   @Override
   protected double seatHeight() {
      return 1.2;
   }

   @Override
   protected double seatForward() {
      return 0.65;
   }

   @Override
   protected double mouthForward() {
      return 5.8;
   }

   @Override
   protected double mouthLow() {
      return 1.2;
   }

   @Override
   protected double mouthHigh() {
      return 4.5;
   }

   @Override
   protected double biteReach() {
      return 2.2;
   }

   @Override
   protected boolean flyer() {
      return true;
   }

   @Override
   protected String chompAnim() {
      return "bite";
   }

   @Override
   protected SoundEvent chompSound() {
      return (SoundEvent)CsmSounds.CINDERVANE_BITE.get();
   }

   @Override
   protected ParticleOptions accent() {
      return ParticleTypes.FLAME;
   }

   @Override
   protected ParticleOptions accent2() {
      return ParticleTypes.LAVA;
   }
}
