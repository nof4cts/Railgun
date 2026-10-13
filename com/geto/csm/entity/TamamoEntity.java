package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class TamamoEntity extends CurseEntity {
   public TamamoEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
      this.setNoGravity(true);
   }

   @Override
   public CurseKind kind() {
      return CurseKind.TAMAMO;
   }

   @Override
   protected float damageTaken() {
      return 0.6F;
   }

   @Override
   protected void serverAi(ServerPlayer o) {
      LivingEntity t = this.findTarget(o, 40.0);
      if (t == null) {
         this.followOwner(o, 6.0, 0.6, true);
      } else {
         double a = (double)this.tickCount * 0.025 + (double)this.getId();
         double d = (double)this.distanceTo(t);
         if (d < 4.5 && this.attackCd <= 0) {
            this.tailSweep();
         } else {
            this.flyToward(t.position().add(Math.cos(a) * 7.0, 4.0, Math.sin(a) * 7.0), 0.7);
            this.faceToward(t.position());
         }

         if (this.signatureCd <= 0 && d < 30.0) {
            this.killingStone(t);
         } else if (this.tickCount % 40 == 0 && d < 30.0) {
            this.foxfire(t);
         }
      }
   }

   private void foxfire(LivingEntity t) {
      this.action(1);
      this.sound(SoundEvents.FIRECHARGE_USE, 1.4F, 1.4F);

      for (int i = 0; i < 3; i++) {
         CsmNetwork.fx(this, FxType.FOXFIRE, this, t, this.getEyePosition(), (float)i, 0.0F);
         Scheduler.after(14 + i * 3, () -> {
            if (this.isAlive() && t.isAlive() && !(this.distanceTo(t) > 34.0F)) {
               this.strike(t, 5.0F);
               t.setSecondsOnFire(3);
            }
         });
      }
   }

   private void tailSweep() {
      this.attackCd = 40;
      this.action(2);
      this.sound(SoundEvents.ENDER_DRAGON_FLAP, 1.8F, 1.2F);
      CsmNetwork.fx(this, FxType.TAIL_SWEEP, this, (Entity)null, this.position(), this.getYRot(), 6.0F);

      for (LivingEntity e : this.enemiesAround(this.position(), 6.0)) {
         this.strike(e, 10.0F);
         launch(e, e.position().subtract(this.position()).normalize().scale(1.8).add(0.0, 0.7, 0.0));
      }
   }

   private void killingStone(LivingEntity t) {
      this.signatureCd = 1200;
      this.action(3);
      Vec3 at = t.position();
      CsmNetwork.fx(this, FxType.KILLING_STONE, this, t, at, 0.0F, 0.0F);
      this.sound(SoundEvents.WITHER_SPAWN, 0.8F, 1.6F);
      Scheduler.after(20, () -> {
         if (this.isAlive()) {
            this.sound(SoundEvents.GENERIC_EXPLODE, 2.0F, 0.6F);

            for (LivingEntity e : this.enemiesAround(at.add(0.0, 1.0, 0.0), 4.5)) {
               this.strike(e, 16.0F);
               launch(e, e.position().subtract(at).normalize().scale(1.2).add(0.0, 0.8, 0.0));
            }

            CsmNetwork.fx(this, FxType.MIASMA, this, (Entity)null, at, 6.0F, 0.0F);

            for (int i = 1; i <= 12; i++) {
               Scheduler.after(i * 10, () -> {
                  if (this.isAlive()) {
                     for (LivingEntity ex : this.enemiesAround(at.add(0.0, 1.0, 0.0), 5.5)) {
                        ex.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0));
                        this.strike(ex, 2.0F);
                     }
                  }
               });
            }
         }
      });
   }
}
