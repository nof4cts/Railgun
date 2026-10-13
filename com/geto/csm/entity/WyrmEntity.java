package com.geto.csm.entity;

import com.geto.csm.CsmSounds;
import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;

public class WyrmEntity extends DragonEntity {
   private static final RawAnimation HOVER = RawAnimation.begin().thenLoop("animation.nulljaw.hover");
   private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.nulljaw.idle");
   private int blinkCd = 100;
   private static final double[][] PARTS = new double[][]{
      {7.0, 1.0, 2.6, 2.2}, {5.0, 1.4, 2.4, 2.4}, {3.0, 1.6, 2.4, 2.4}, {-3.0, 1.2, 2.2, 2.0}, {-6.0, 1.0, 1.8, 1.6}, {-9.0, 0.8, 1.4, 1.2}
   };
   private final WyrmPart[] parts = new WyrmPart[PARTS.length];

   public WyrmEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setNoGravity(true);

      for (int i = 0; i < PARTS.length; i++) {
         this.parts[i] = new WyrmPart(this, (float)PARTS[i][2], (float)PARTS[i][3]);
      }

      this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
   }

   public void setId(int id) {
      super.setId(id);

      for (int i = 0; i < this.parts.length; i++) {
         this.parts[i].setId(id + i + 1);
      }
   }

   public boolean isMultipartEntity() {
      return true;
   }

   public PartEntity<?>[] getParts() {
      return this.parts;
   }

   public void aiStep() {
      super.aiStep();
      float yaw = this.yBodyRot * (float) (Math.PI / 180.0);
      double fx = (double)(-Mth.sin(yaw));
      double fz = (double)Mth.cos(yaw);

      for (int i = 0; i < this.parts.length; i++) {
         WyrmPart p = this.parts[i];
         double x = this.getX() + fx * PARTS[i][0];
         double y = this.getY() + PARTS[i][1] - PARTS[i][3] / 2.0;
         double z = this.getZ() + fz * PARTS[i][0];
         p.xo = p.xOld = p.getX();
         p.yo = p.yOld = p.getY();
         p.zo = p.zOld = p.getZ();
         p.setPos(x, y, z);
      }
   }

   @Override
   public boolean hurt(DamageSource src, float amount) {
      return src.getEntity() != null && src.getEntity().getId() == this.ownerId() && !this.hasPassenger(src.getEntity()) && src.getEntity() instanceof Player
         ? !this.fading() && this.hurtDirect(src, amount)
         : super.hurt(src, amount);
   }

   @Override
   public CurseKind kind() {
      return CurseKind.WYRM;
   }

   @Override
   protected float damageTaken() {
      return 1.0F;
   }

   @Override
   protected double rideSpeed() {
      return 1.3;
   }

   @Override
   public void tick() {
      super.tick();
      if (!this.level().isClientSide && this.blinkCd > 0) {
         this.blinkCd--;
      }
   }

   @Override
   protected void serverAi(ServerPlayer o) {
      if (this.tickCount % 220 == 37 && this.random.nextInt(2) == 0) {
         int n = this.random.nextInt(3);
         this.act("grumble" + (n + 1));
         this.sound((SoundEvent)(n == 0 ? CsmSounds.NULLJAW_GRUMBLE1 : (n == 1 ? CsmSounds.NULLJAW_GRUMBLE2 : CsmSounds.NULLJAW_GRUMBLE3)).get(), 2.0F, 0.7F);
      }

      LivingEntity t = this.findTarget(o, 40.0);
      if (t == null) {
         this.followOwner(o, 8.0, 0.7, true);
      } else {
         double d = (double)this.distanceTo(t);
         double jaw = this.mouthDist(t);
         if (jaw > this.biteReach() * 0.6) {
            this.flyToward(this.biteApproach(t), d > 14.0 ? 0.95 : 0.6);
         } else {
            this.setDeltaMovement(this.getDeltaMovement().scale(0.6));
         }

         this.faceToward(t.position());
         if (d > 12.0 && d < 30.0 && this.blinkCd <= 0) {
            this.voidBlink(t);
         } else if (jaw < this.biteReach() && this.attackCd <= 0) {
            this.bite();
         } else if (d < 10.0 && this.specialCd <= 0) {
            this.tailSweep();
         } else if (d > 10.0 && d < 28.0 && this.signatureCd <= 0) {
            this.roar(t.position().add(0.0, 1.0, 0.0).subtract(this.mouth()));
         }
      }
   }

   private void voidBlink(LivingEntity t) {
      Vec3 behind = t.position().subtract(t.getLookAngle().multiply(1.0, 0.0, 1.0).normalize().scale(this.mouthForward() + 1.5)).add(0.0, 1.0, 0.0);
      this.blinkTo(behind);
      this.faceToward(t.position());
      this.attackCd = 0;
      this.bite();
   }

   private void blinkTo(Vec3 to) {
      this.blinkCd = 220;
      Vec3 from = this.position();
      this.line(ParticleTypes.REVERSE_PORTAL, from.add(0.0, 1.5, 0.0), from.add(0.0, 4.0, 0.0), 0.25);
      this.ring(ParticleTypes.END_ROD, from.add(0.0, 1.5, 0.0), 2.5, 32, 0.25);
      this.sound(SoundEvents.ENDERMAN_TELEPORT, 2.0F, 0.6F);
      this.teleportTo(to.x, to.y, to.z);
      this.ring(ParticleTypes.END_ROD, to, 2.5, 32, 0.3);
      this.ring(ParticleTypes.SOUL_FIRE_FLAME, to, 1.5, 24, 0.15);
   }

   private void bite() {
      this.attackCd = 28;
      this.action(1);
      this.jawBite("bite", (SoundEvent)CsmSounds.NULLJAW_BITE.get(), 7, 16.0F, 1.4, true, e -> this.sound(SoundEvents.GENERIC_EXPLODE, 0.8F, 1.3F));
   }

   private void tailSweep() {
      this.specialCd = 140;
      this.action(2);
      this.act("grumble2");
      this.sound(SoundEvents.ENDER_DRAGON_FLAP, 2.5F, 0.55F);
      Scheduler.after(8, () -> {
         if (this.isAlive()) {
            CsmNetwork.fx(this, FxType.TAIL_SWEEP, this, (Entity)null, this.position(), this.getYRot(), 11.0F);

            for (LivingEntity e : this.enemiesAround(this.position(), 11.0)) {
               if (!this.hasPassenger(e)) {
                  this.strike(e, 9.0F);
                  launch(e, e.position().subtract(this.position()).normalize().scale(1.7).add(0.0, 0.6, 0.0));
               }
            }
         }
      });
   }

   public void roar(Vec3 dir) {
      this.specialCd = Math.max(this.specialCd, 60);
      this.signatureCd = 600;
      this.action(3);
      this.act("grumble1");
      Vec3 d = dir.normalize();
      Vec3 from = this.mouth().add(d.scale(1.0));
      this.sound((SoundEvent)CsmSounds.NULLJAW_GRUMBLE3.get(), 3.0F, 0.55F);
      this.sound(SoundEvents.ENDER_DRAGON_GROWL, 2.0F, 0.7F);
      CsmNetwork.fx(
         this,
         FxType.ROAR_BEAM,
         this,
         (Entity)null,
         from,
         (float)(Mth.atan2(d.z, d.x) * 180.0F / (float)Math.PI) - 90.0F,
         (float)(-(Mth.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)) * 180.0F / (float)Math.PI))
      );
      Scheduler.after(6, () -> {
         if (this.isAlive()) {
            this.line(ParticleTypes.END_ROD, from, from.add(d.scale(24.0)), 0.6);

            for (LivingEntity e : this.enemiesAround(from.add(d.scale(12.0)), 13.0)) {
               if (!this.hasPassenger(e)) {
                  Vec3 to = e.getBoundingBox().getCenter().subtract(from);
                  double along = to.dot(d);
                  if (!(along < 0.0) && !(along > 26.0)) {
                     double off = to.subtract(d.scale(along)).length();
                     if (!(off > 2.5 + along * 0.12)) {
                        this.strike(e, 13.0F);
                        launch(e, d.scale(1.2).add(0.0, 0.4, 0.0));
                     }
                  }
               }
            }
         }
      });
   }

   @Override
   public String[] riderMoves() {
      return new String[]{"Bite", "Tail Sweep", "Roar Beam", "Void Blink"};
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
            if (this.ready(this.specialCd, p, "Tail Sweep")) {
               this.tailSweep();
            }
            break;
         case 2:
            if (this.ready(this.signatureCd, p, "Roar Beam")) {
               LivingEntity t = this.riderTarget(p, 28.0);
               this.roar(t != null ? t.getBoundingBox().getCenter().subtract(this.mouth()) : p.getLookAngle());
            }
            break;
         case 3:
            if (this.ready(this.blinkCd, p, "Void Blink")) {
               Vec3 look = p.getLookAngle();
               Vec3 from = this.position().add(0.0, (double)this.getBbHeight() * 0.5, 0.0);
               Vec3 hit = this.level().clip(new ClipContext(from, from.add(look.scale(16.0)), Block.COLLIDER, Fluid.NONE, this)).getLocation();
               Vec3 to = hit.subtract(look.scale((double)this.getBbWidth())).subtract(0.0, (double)this.getBbHeight() * 0.5, 0.0);
               this.blinkTo(to);
               this.blinkCd = 160;
            }
      }
   }

   public boolean riderAttack(Player rider) {
      if (rider instanceof ServerPlayer sp && this.hasPassenger(rider) && this.signatureCd <= 0) {
         this.doRiderMove(sp, 2);
         return true;
      }

      return false;
   }

   protected SoundEvent getHurtSound(DamageSource src) {
      return (SoundEvent)CsmSounds.NULLJAW_HURT.get();
   }

   protected SoundEvent getDeathSound() {
      return (SoundEvent)CsmSounds.NULLJAW_DIE.get();
   }

   @Override
   protected RawAnimation movement(AnimationState<DragonEntity> state) {
      return !(this.speed() > 0.03) && !this.isVehicle() ? IDLE : HOVER;
   }

   @Override
   protected String animPrefix() {
      return "animation.nulljaw.";
   }

   @Override
   protected String[] actions() {
      return new String[]{"bite", "hurt", "grumble1", "grumble2", "grumble3"};
   }

   @Override
   protected double seatHeight() {
      return 3.4;
   }

   @Override
   protected double seatForward() {
      return 0.4;
   }

   @Override
   protected double mouthForward() {
      return 7.6;
   }

   @Override
   protected double mouthLow() {
      return 0.1;
   }

   @Override
   protected double mouthHigh() {
      return 1.4;
   }

   @Override
   protected double biteReach() {
      return 2.6;
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
      return (SoundEvent)CsmSounds.NULLJAW_BITE.get();
   }

   @Override
   protected String sitAnim() {
      return "idle";
   }
}
