package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.Scheduler;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PyreEntity extends CurseEntity {
   public static final int HEAT_RAMP_TICKS = 800;

   public PyreEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
   }

   @Override
   public CurseKind kind() {
      return CurseKind.PYRE;
   }

   public static float heat(int ticks) {
      float k = Mth.clamp((float)ticks / 800.0F, 0.0F, 1.0F);
      return 120.0F + 2880.0F * k * k;
   }

   public float heat() {
      return heat(this.tickCount);
   }

   @Override
   protected void serverAi(ServerPlayer o) {
      float h = this.heat();
      double aura = 2.0 + (double)h / 600.0;
      if (this.tickCount % 20 == 0) {
         CsmNetwork.fx(this, FxType.PYRE_PULSE, this, (Entity)null, this.position(), h, (float)aura);

         for (LivingEntity e : this.enemiesAround(this.position().add(0.0, 1.0, 0.0), aura)) {
            e.setSecondsOnFire(3 + (int)(h / 600.0F));
            this.strike(e, h / 420.0F);
         }
      }

      LivingEntity t = this.findTarget(o, 30.0);
      if (t == null) {
         this.followOwner(o, 5.0, 1.0, false);
      } else {
         this.walkToward(t, 1.1);
         if (this.distanceTo(t) < 9.0F && this.signatureCd <= 0) {
            this.flare(t, h);
         }
      }
   }

   private void flare(LivingEntity t, float h) {
      this.signatureCd = 1200;
      this.action(1);
      this.sound(SoundEvents.BLAZE_SHOOT, 2.0F, 0.5F);
      Vec3 d = t.position().subtract(this.position()).normalize();
      CsmNetwork.fx(this, FxType.PYRE_FLARE, this, t, this.position().add(0.0, 1.4, 0.0), this.getYRot(), h);
      Scheduler.after(8, () -> {
         if (this.isAlive()) {
            for (LivingEntity e : this.enemiesAround(this.position().add(d.scale(5.0)).add(0.0, 1.0, 0.0), 5.5)) {
               this.strike(e, 6.0F + h / 400.0F);
               e.setSecondsOnFire(6);
               launch(e, d.scale(1.0).add(0.0, 0.5, 0.0));
            }

            this.sound(SoundEvents.GENERIC_EXPLODE, 1.2F, 0.8F);
         }
      });
   }
}
