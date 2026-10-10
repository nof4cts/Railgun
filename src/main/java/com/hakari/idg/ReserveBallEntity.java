package com.hakari.idg;

import com.hakari.idg.network.FxType;
import com.hakari.idg.network.HakariNetwork;
import com.hakari.idg.server.Combat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class ReserveBallEntity extends ThrowableProjectile {
   public ReserveBallEntity(EntityType<? extends ReserveBallEntity> type, Level level) {
      super(type, level);
   }

   public ReserveBallEntity(Level level, LivingEntity owner) {
      super((EntityType)ModRegistry.RESERVE_BALL_ENTITY.get(), owner, level);
   }

   protected void defineSynchedData() {
   }

   protected float getGravity() {
      return 0.004F;
   }

   public void tick() {
      super.tick();
      if (!this.level().isClientSide && this.tickCount > 60) {
         this.discard();
      }
   }

   protected void onHitEntity(EntityHitResult result) {
      super.onHitEntity(result);
      if (!this.level().isClientSide) {
         Entity owner = this.getOwner();
         if (result.getEntity() instanceof LivingEntity target && owner instanceof ServerPlayer sp && target != sp) {
            double dist = (double)sp.distanceTo(target);
            boolean close = dist < 4.5;
            Combat.damage(sp, target, 5.0F, false);
            if (close) {
               Vec3 dir = this.getDeltaMovement().normalize();
               Combat.launch(target, dir.scale(1.6).add(0.0, 0.6, 0.0));
            } else {
               Combat.stun(target, 20);
            }

            HakariNetwork.fx((ServerLevel)this.level(), FxType.BALL_IMPACT, sp.getId(), target.getId(), this.position(), close ? 1.0F : 0.0F, 0.0F);
            this.level()
               .playSound((Player)null, this.getX(), this.getY(), this.getZ(), (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, 2.0F);
            this.level().playSound((Player)null, this.getX(), this.getY(), this.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.3F, 1.8F);
         }

         this.discard();
      }
   }

   protected void onHitBlock(BlockHitResult result) {
      super.onHitBlock(result);
      if (!this.level().isClientSide) {
         Entity owner = this.getOwner();
         HakariNetwork.fx((ServerLevel)this.level(), FxType.BALL_IMPACT, owner == null ? -1 : owner.getId(), -1, this.position(), 0.0F, 0.0F);
         this.level()
            .playSound((Player)null, this.getX(), this.getY(), this.getZ(), (SoundEvent)SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.6F, 1.7F);
         this.discard();
      }
   }
}
