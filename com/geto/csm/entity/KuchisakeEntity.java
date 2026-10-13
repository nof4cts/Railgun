package com.geto.csm.entity;

import com.geto.csm.CurseKind;
import com.geto.csm.network.CsmNetwork;
import com.geto.csm.network.FxType;
import com.geto.csm.server.KuchisakeGame;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.level.Level;

public class KuchisakeEntity extends CurseEntity {
   private static final EntityDataAccessor<Boolean> UNMASKED = SynchedEntityData.defineId(KuchisakeEntity.class, EntityDataSerializers.BOOLEAN);
   public int askCooldown = 20;

   public KuchisakeEntity(EntityType<? extends PathfinderMob> type, Level level) {
      super(type, level);
   }

   @Override
   protected void defineSynchedData() {
      super.defineSynchedData();
      this.entityData.define(UNMASKED, false);
   }

   public boolean unmasked() {
      return (Boolean)this.entityData.get(UNMASKED);
   }

   public void setUnmasked(boolean b) {
      this.entityData.set(UNMASKED, b);
   }

   @Override
   public CurseKind kind() {
      return CurseKind.KUCHISAKE;
   }

   @Override
   protected void serverAi(ServerPlayer o) {
      if (this.askCooldown > 0) {
         this.askCooldown--;
      }

      LivingEntity t = this.findTarget(o, 30.0);
      if (t == null) {
         this.followOwner(o, 4.0, 1.0, false);
      } else {
         double d = (double)this.distanceTo(t);
         if (this.askCooldown <= 0 && d < 7.0 && !KuchisakeGame.busy(t)) {
            this.getNavigation().stop();
            KuchisakeGame.start(this, t);
         } else {
            this.walkToward(t, 1.25);
            if (d < 3.2 && this.attackCd <= 0) {
               this.attackCd = 26;
               this.action(1);
               this.sound(SoundEvents.SHEEP_SHEAR, 2.0F, 0.5F);
               this.strike(t, 8.0F);
               CsmNetwork.fx(this, FxType.SHEARS, this, t, t.position().add(0.0, (double)t.getBbHeight() * 0.6, 0.0), this.getYRot(), 0.0F);
            }
         }
      }
   }
}
