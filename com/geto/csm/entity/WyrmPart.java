package com.geto.csm.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.entity.PartEntity;

public class WyrmPart extends PartEntity<WyrmEntity> {
   private final EntityDimensions size;

   public WyrmPart(WyrmEntity parent, float w, float h) {
      super(parent);
      this.size = EntityDimensions.scalable(w, h);
      this.refreshDimensions();
   }

   protected void defineSynchedData() {
   }

   protected void readAdditionalSaveData(CompoundTag tag) {
   }

   protected void addAdditionalSaveData(CompoundTag tag) {
   }

   public boolean isPickable() {
      return true;
   }

   public ItemStack getPickResult() {
      return ((WyrmEntity)this.getParent()).getPickResult();
   }

   public boolean hurt(DamageSource src, float amount) {
      return !this.isInvulnerableTo(src) && ((WyrmEntity)this.getParent()).hurt(src, amount);
   }

   public boolean is(Entity e) {
      return this == e || this.getParent() == e;
   }

   public EntityDimensions getDimensions(Pose pose) {
      return this.size;
   }

   public boolean shouldBeSaved() {
      return false;
   }
}
